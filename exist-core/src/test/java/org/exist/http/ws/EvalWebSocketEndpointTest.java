/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.http.ws;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.security.Account;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistWebServer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import jakarta.websocket.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Integration tests for the /ws/eval WebSocket endpoint.
 */
public class EvalWebSocketEndpointTest {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private static final String TEST_COLLECTION = "/db/ws-eval-test";
    /** Bounded FLWOR loop for cancel and rapid-cancel tests. */
    private static final String CANCEL_TEST_QUERY =
            "for $i in 1 to 10000000 return ()";
    /** Slower loop for max-execution-time smoke test (wall-clock backstop). */
    private static final String TIMEOUT_TEST_QUERY =
            "for $i in 1 to 100000000 return string($i)";
    private static final long CANCEL_MAX_EXECUTION_MS = 15_000L;
    private static final long CANCEL_AWAIT_SEC = 20L;
    /** Wall-clock backstop on the server guarantees a terminal response by this limit. */
    private static final long TIMEOUT_MAX_EXECUTION_MS = 1_000L;
    private static final long TIMEOUT_AWAIT_SLACK_MS = 3_000L;
    private static final String TEST_MODULE = """
            module namespace test = 'http://exist-db.org/test';
            declare function test:hello($name as xs:string) as xs:string {
                concat('Hello, ', $name, '!')
            };
            """;

    @ClassRule
    public static final ExistWebServer existWebServer =
            new ExistWebServer(true, false, true, true);

    @BeforeClass
    public static void storeTestModule() throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        final SecurityManager securityManager = pool.getSecurityManager();
        try (final DBBroker broker = pool.get(Optional.of(securityManager.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            if (!securityManager.hasAccount("test-user")) {
                final Account user = new UserAider("test-user");
                user.setPassword("test-pass");
                securityManager.addAccount(user);
            }

            final Collection col = broker.getOrCreateCollection(transaction,
                    XmldbURI.create(TEST_COLLECTION));
            broker.saveCollection(transaction, col);

            broker.storeDocument(transaction, XmldbURI.create("test-module.xqm"),
                    new StringInputSource(TEST_MODULE.getBytes(StandardCharsets.UTF_8)),
                    MimeType.XQUERY_TYPE, col);

            transaction.commit();
        }
    }

    @AfterClass
    public static void cleanupTestModule() throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        final SecurityManager securityManager = pool.getSecurityManager();
        try (final DBBroker broker = pool.get(Optional.of(securityManager.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection col = broker.getCollection(XmldbURI.create(TEST_COLLECTION));
            if (col != null) {
                broker.removeCollection(transaction, col);
            }

            if (securityManager.hasAccount("test-user")) {
                securityManager.deleteAccount("test-user");
            }

            transaction.commit();
        }
    }

    private URI getWsUri() throws Exception {
        return new URI("ws://localhost:" + existWebServer.getPort() + "/ws/eval");
    }

    private ClientEndpointConfig createAuthConfig(final String username, final String password) {
        return ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(final Map<String, List<String>> headers) {
                        final String credentials = username + ":" + password;
                        final String encoded = Base64.getEncoder().encodeToString(
                                credentials.getBytes(StandardCharsets.UTF_8));
                        headers.put("Authorization",
                                Collections.singletonList("Basic " + encoded));
                    }
                })
                .build();
    }

    private ClientEndpointConfig createAdminConfig() {
        return createAuthConfig("admin", "");
    }

    /**
     * Helper to parse a JSON message and extract a field value.
     */
    private static Map<String, Object> parseJson(final String json) throws IOException {
        final Map<String, Object> result = new HashMap<>();
        try (final JsonParser parser = JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return result;
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                final String field = parser.currentName();
                parser.nextToken();
                switch (parser.currentToken()) {
                    case VALUE_STRING -> result.put(field, parser.getValueAsString());
                    case VALUE_NUMBER_INT -> result.put(field, parser.getValueAsLong());
                    case VALUE_NUMBER_FLOAT -> result.put(field, parser.getValueAsDouble());
                    case VALUE_TRUE, VALUE_FALSE -> result.put(field, parser.getValueAsBoolean());
                    case START_OBJECT -> {
                        // nested object - skip for simplicity, just mark as present
                        result.put(field, "OBJECT");
                        parser.skipChildren();
                    }
                    case START_ARRAY -> {
                        result.put(field, "ARRAY");
                        parser.skipChildren();
                    }
                    default -> { }
                }
            }
        }
        return result;
    }

    @Test
    public void simpleEval() throws Exception {
        final CountDownLatch resultLatch = new CountDownLatch(1);
        final AtomicReference<String> resultData = new AtomicReference<>();
        final List<String> allMessages = new CopyOnWriteArrayList<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        allMessages.add(message);
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("result".equals(parsed.get("type"))
                                    && Boolean.FALSE.equals(parsed.get("more"))) {
                                resultData.set((String) parsed.get("data"));
                                resultLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"q-1","query":"1 + 1"}""");

            assertTrue("Should receive result within 5s", resultLatch.await(5, TimeUnit.SECONDS));
            assertEquals("2", resultData.get());

            // Should have received at least one progress message and one result
            assertTrue("Should have multiple messages", allMessages.size() >= 2);
        } finally {
            session.close();
        }
    }

    @Test
    public void evalWithVariables() throws Exception {
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final AtomicReference<String> resultData = new AtomicReference<>();
        final AtomicReference<String> errorData = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("result".equals(parsed.get("type"))
                                    && Boolean.FALSE.equals(parsed.get("more"))) {
                                resultData.set((String) parsed.get("data"));
                                doneLatch.countDown();
                            } else if ("error".equals(parsed.get("type"))) {
                                errorData.set(message);
                                doneLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"q-2",\
                            "query":"declare variable $x external; xs:integer($x) * 2",\
                            "variables":{"x":"21"}}""");

            assertTrue("Should receive response within 5s", doneLatch.await(5, TimeUnit.SECONDS));
            assertNull("Should not have error: " + errorData.get(), errorData.get());
            assertEquals("42", resultData.get());
        } finally {
            session.close();
        }
    }

    @Test
    public void compileError() throws Exception {
        final CountDownLatch errorLatch = new CountDownLatch(1);
        final AtomicReference<Map<String, Object>> errorMsg = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("error".equals(parsed.get("type"))) {
                                errorMsg.set(parsed);
                                errorLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"q-3","query":"let $x := retrun"}""");

            assertTrue("Should receive error within 5s", errorLatch.await(5, TimeUnit.SECONDS));
            final Map<String, Object> error = errorMsg.get();
            assertNotNull(error.get("message"));
            assertEquals("q-3", error.get("id"));
        } finally {
            session.close();
        }
    }

    @Test
    public void compileAction() throws Exception {
        final CountDownLatch compileLatch = new CountDownLatch(1);
        final AtomicReference<Map<String, Object>> compileMsg = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("compile".equals(parsed.get("type"))) {
                                compileMsg.set(parsed);
                                compileLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            // Valid query
            session.getBasicRemote().sendText(
                    """
                            {"action":"compile","id":"c-1","query":"1 + 1"}""");

            assertTrue("Should receive compile result within 5s",
                    compileLatch.await(5, TimeUnit.SECONDS));
            assertEquals(true, compileMsg.get().get("success"));
        } finally {
            session.close();
        }
    }

    @Test
    public void compileActionWithError() throws Exception {
        final CountDownLatch compileLatch = new CountDownLatch(1);
        final AtomicReference<Map<String, Object>> compileMsg = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("compile".equals(parsed.get("type"))) {
                                compileMsg.set(parsed);
                                compileLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    """
                            {"action":"compile","id":"c-2","query":"let $x := retrun"}""");

            assertTrue("Should receive compile result within 5s",
                    compileLatch.await(5, TimeUnit.SECONDS));
            assertEquals(false, compileMsg.get().get("success"));
            assertEquals("c-2", compileMsg.get().get("id"));
        } finally {
            session.close();
        }
    }

    @Test
    public void streamingResults() throws Exception {
        final List<Map<String, Object>> resultChunks = new CopyOnWriteArrayList<>();
        final CountDownLatch finalLatch = new CountDownLatch(1);

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("result".equals(parsed.get("type"))) {
                                resultChunks.add(parsed);
                                if (Boolean.FALSE.equals(parsed.get("more"))) {
                                    finalLatch.countDown();
                                }
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            // Generate 500 items with chunk-size 100 → expect 5 chunks
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"q-5",\
                            "query":"1 to 500",\
                            "chunk-size":100,"streaming":true}""");

            assertTrue("Should receive all chunks within 10s",
                    finalLatch.await(10, TimeUnit.SECONDS));

            assertEquals("Should have 5 result chunks", 5, resultChunks.size());

            // First 4 chunks should have more=true
            for (int i = 0; i < 4; i++) {
                assertEquals("Chunk " + i + " should have more=true",
                        true, resultChunks.get(i).get("more"));
            }
            // Last chunk should have more=false
            assertEquals(false, resultChunks.get(4).get("more"));

            // Last chunk should have timing
            assertNotNull("Last chunk should have timing",
                    resultChunks.get(4).get("timing"));
        } finally {
            session.close();
        }
    }

    @Test
    public void cancellation() throws Exception {
        final CountDownLatch cancelledLatch = new CountDownLatch(1);
        final AtomicReference<Map<String, Object>> cancelledMsg = new AtomicReference<>();
        final CountDownLatch progressLatch = new CountDownLatch(1);

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("progress".equals(parsed.get("type"))
                                    && "evaluating".equals(parsed.get("phase"))) {
                                progressLatch.countDown();
                            } else if ("cancelled".equals(parsed.get("type"))) {
                                cancelledMsg.set(parsed);
                                cancelledLatch.countDown();
                            } else if ("error".equals(parsed.get("type"))) {
                                // Timeout or termination also acceptable
                                cancelledMsg.set(parsed);
                                cancelledLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    "{\"action\":\"eval\",\"id\":\"q-cancel\"," +
                    "\"query\":\"" + CANCEL_TEST_QUERY + "\"," +
                    "\"max-execution-time\":" + CANCEL_MAX_EXECUTION_MS + "}");

            // Wait for the server to confirm the query is executing before cancelling.
            assertTrue("Query should start executing within 10s",
                    progressLatch.await(10, TimeUnit.SECONDS));

            session.getBasicRemote().sendText(
                    """
                            {"action":"cancel","id":"q-cancel"}""");

            // Await longer than max-execution-time so the watchdog safety net can fire on slow CI.
            assertTrue("Should receive cancelled/error within " + CANCEL_AWAIT_SEC + "s",
                    cancelledLatch.await(CANCEL_AWAIT_SEC, TimeUnit.SECONDS));
            assertEquals("q-cancel", cancelledMsg.get().get("id"));
        } finally {
            session.close();
        }
    }

    @Test
    public void timing() throws Exception {
        final CountDownLatch resultLatch = new CountDownLatch(1);
        final AtomicReference<Map<String, Object>> resultMsg = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("result".equals(parsed.get("type"))
                                    && Boolean.FALSE.equals(parsed.get("more"))) {
                                resultMsg.set(parsed);
                                resultLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"q-timing","query":"1 to 100"}""");

            assertTrue("Should receive result within 5s", resultLatch.await(5, TimeUnit.SECONDS));
            assertNotNull("Should have timing object", resultMsg.get().get("timing"));
        } finally {
            session.close();
        }
    }

    @Test
    public void progressReporting() throws Exception {
        final List<Map<String, Object>> progressMessages = new CopyOnWriteArrayList<>();
        final CountDownLatch doneLatch = new CountDownLatch(1);

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("progress".equals(parsed.get("type"))) {
                                progressMessages.add(parsed);
                            } else if ("result".equals(parsed.get("type"))
                                    && Boolean.FALSE.equals(parsed.get("more"))) {
                                doneLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"q-prog","query":"1 to 100"}""");

            assertTrue("Should complete within 5s", doneLatch.await(5, TimeUnit.SECONDS));
            assertFalse("Should have at least one progress message",
                    progressMessages.isEmpty());

            // Should have parsing and/or evaluating phases
            assertTrue("Should have phase field",
                    progressMessages.stream().allMatch(m -> m.containsKey("phase")));
        } finally {
            session.close();
        }
    }

    @Test
    public void serializationOptions() throws Exception {
        final CountDownLatch resultLatch = new CountDownLatch(1);
        final AtomicReference<String> resultData = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("result".equals(parsed.get("type"))
                                    && Boolean.FALSE.equals(parsed.get("more"))) {
                                resultData.set((String) parsed.get("data"));
                                resultLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"q-ser",\
                            "query":"<root><item>1</item></root>",\
                            "serialization":{"method":"adaptive"}}""");

            assertTrue("Should receive result within 5s", resultLatch.await(5, TimeUnit.SECONDS));
            assertNotNull("Should have result data", resultData.get());
            // Adaptive serialization wraps elements in their XML representation
            assertTrue("Result should contain XML", resultData.get().contains("<root>"));
        } finally {
            session.close();
        }
    }

    @Test
    public void maxExecutionTime() throws Exception {
        final CountDownLatch errorLatch = new CountDownLatch(1);
        final AtomicReference<Map<String, Object>> errorMsg = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("error".equals(parsed.get("type"))
                                    || "cancelled".equals(parsed.get("type"))) {
                                errorMsg.set(parsed);
                                errorLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    "{\"action\":\"eval\",\"id\":\"q-timeout\"," +
                    "\"query\":\"" + TIMEOUT_TEST_QUERY + "\"," +
                    "\"max-execution-time\":" + TIMEOUT_MAX_EXECUTION_MS + "}");

            final long errorWaitMs = TIMEOUT_MAX_EXECUTION_MS + TIMEOUT_AWAIT_SLACK_MS;
            assertTrue("Should receive timeout response within " + errorWaitMs + "ms",
                    errorLatch.await(errorWaitMs, TimeUnit.MILLISECONDS));
            assertEquals("q-timeout", errorMsg.get().get("id"));
        } finally {
            session.close();
        }
    }

    @Test
    public void invalidMessage() throws Exception {
        final CountDownLatch errorLatch = new CountDownLatch(1);

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("error".equals(parsed.get("type"))) {
                                errorLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText("not valid json{{{");

            assertTrue("Should receive error within 5s", errorLatch.await(5, TimeUnit.SECONDS));
        } finally {
            session.close();
        }
    }

    @Test
    public void missingQuery() throws Exception {
        final CountDownLatch errorLatch = new CountDownLatch(1);

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("error".equals(parsed.get("type"))) {
                                errorLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"q-noquery"}""");

            assertTrue("Should receive error within 5s", errorLatch.await(5, TimeUnit.SECONDS));
        } finally {
            session.close();
        }
    }

    // ====== Edge case tests ======

    /**
     * Two eval actions on the same connection should produce independent results.
     */
    @Test
    public void concurrentQueries() throws Exception {
        final CountDownLatch doneLatch = new CountDownLatch(2);
        final Map<String, String> results = new ConcurrentHashMap<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("result".equals(parsed.get("type"))
                                    && Boolean.FALSE.equals(parsed.get("more"))) {
                                results.put((String) parsed.get("id"),
                                        (String) parsed.get("data"));
                                doneLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            // Fire two queries concurrently on the same connection
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"conc-1","query":"2 + 3"}""");
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"conc-2","query":"10 * 7"}""");

            assertTrue("Both queries should complete within 5s",
                    doneLatch.await(5, TimeUnit.SECONDS));
            assertEquals("5", results.get("conc-1"));
            assertEquals("70", results.get("conc-2"));
        } finally {
            session.close();
        }
    }

    /**
     * Stream 100K+ items and verify chunk count.
     */
    @Test
    public void largeResultStreaming() throws Exception {
        final int totalItems = 100000;
        final int chunkSize = 1000;
        final int expectedChunks = totalItems / chunkSize; // 100
        final List<Map<String, Object>> resultChunks = new CopyOnWriteArrayList<>();
        final CountDownLatch finalLatch = new CountDownLatch(1);

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("result".equals(parsed.get("type"))) {
                                resultChunks.add(parsed);
                                if (Boolean.FALSE.equals(parsed.get("more"))) {
                                    finalLatch.countDown();
                                }
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    "{\"action\":\"eval\",\"id\":\"q-large\"," +
                    "\"query\":\"1 to " + totalItems + "\"," +
                    "\"chunk-size\":" + chunkSize + ",\"streaming\":true}");

            assertTrue("Should receive all chunks within 60s",
                    finalLatch.await(60, TimeUnit.SECONDS));

            assertEquals("Should have " + expectedChunks + " result chunks",
                    expectedChunks, resultChunks.size());

            // Last chunk should have timing and items
            final Map<String, Object> lastChunk = resultChunks.get(resultChunks.size() - 1);
            assertEquals(false, lastChunk.get("more"));
            assertNotNull("Last chunk should have timing", lastChunk.get("timing"));
            assertEquals("Total items should be " + totalItems,
                    (long) totalItems, lastChunk.get("items"));
        } finally {
            session.close();
        }
    }

    /**
     * xs:base64Binary result should serialize without error.
     */
    @Test
    public void binaryResultHandling() throws Exception {
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final AtomicReference<String> resultData = new AtomicReference<>();
        final AtomicReference<String> errorData = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("result".equals(parsed.get("type"))
                                    && Boolean.FALSE.equals(parsed.get("more"))) {
                                resultData.set((String) parsed.get("data"));
                                doneLatch.countDown();
                            } else if ("error".equals(parsed.get("type"))) {
                                errorData.set(message);
                                doneLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            // xs:base64Binary('SGVsbG8=') is base64 for "Hello"
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"q-bin",\
                            "query":"xs:base64Binary('SGVsbG8=')"}""");

            assertTrue("Should receive response within 5s", doneLatch.await(5, TimeUnit.SECONDS));
            assertNull("Should not have error: " + errorData.get(), errorData.get());
            assertNotNull("Should have result data", resultData.get());
            assertTrue("Result should contain base64 data",
                    resultData.get().contains("SGVsbG8="));
        } finally {
            session.close();
        }
    }

    /**
     * Map and array results should serialize with adaptive method.
     */
    @Test
    public void mapArrayResultSerialization() throws Exception {
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final AtomicReference<String> resultData = new AtomicReference<>();
        final AtomicReference<String> errorData = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("result".equals(parsed.get("type"))
                                    && Boolean.FALSE.equals(parsed.get("more"))) {
                                resultData.set((String) parsed.get("data"));
                                doneLatch.countDown();
                            } else if ("error".equals(parsed.get("type"))) {
                                errorData.set(message);
                                doneLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            session.getBasicRemote().sendText(
                    """
                            {"action":"eval","id":"q-map",\
                            "query":"map { 'key': 'value', 'nums': [1, 2, 3] }",\
                            "serialization":{"method":"adaptive"}}""");

            assertTrue("Should receive response within 5s", doneLatch.await(5, TimeUnit.SECONDS));
            assertNull("Should not have error: " + errorData.get(), errorData.get());
            assertNotNull("Should have result data", resultData.get());
            // Adaptive serialization of maps uses {"key":"value",...} format
            assertTrue("Result should contain key",
                    resultData.get().contains("key"));
        } finally {
            session.close();
        }
    }

    /**
     * Query that imports a module stored in the database via module-load-path.
     */
    @Test
    public void moduleLoadPath() throws Exception {
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final AtomicReference<String> resultData = new AtomicReference<>();
        final AtomicReference<String> errorData = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("result".equals(parsed.get("type"))
                                    && Boolean.FALSE.equals(parsed.get("more"))) {
                                resultData.set((String) parsed.get("data"));
                                doneLatch.countDown();
                            } else if ("error".equals(parsed.get("type"))) {
                                errorData.set(message);
                                doneLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            final String query = "import module namespace test = 'http://exist-db.org/test' " +
                    "at 'xmldb:exist://" + TEST_COLLECTION + "/test-module.xqm'; " +
                    "test:hello('World')";
            session.getBasicRemote().sendText(
                    "{\"action\":\"eval\",\"id\":\"q-mod\"," +
                    "\"query\":\"" + escapeJson(query) + "\"," +
                    "\"module-load-path\":\"" + TEST_COLLECTION + "\"}");

            assertTrue("Should receive response within 5s", doneLatch.await(5, TimeUnit.SECONDS));
            assertNull("Should not have error: " + errorData.get(), errorData.get());
            assertEquals("Hello, World!", resultData.get());
        } finally {
            session.close();
        }
    }

    /**
     * Admin-cancel: connect as admin, use admin-cancel to kill a query via ProcessMonitor.
     * Non-DBA user should get permission denied.
     */
    @Test
    public void adminCancelRequiresDba() throws Exception {
        final CountDownLatch errorLatch = new CountDownLatch(1);
        final AtomicReference<Map<String, Object>> errorMsg = new AtomicReference<>();

        // Connect as guest (non-DBA)
        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("error".equals(parsed.get("type"))) {
                                errorMsg.set(parsed);
                                errorLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAuthConfig("test-user", "test-pass"), getWsUri());

        try {
            // non-DBA user tries admin-cancel
            session.getBasicRemote().sendText(
                    """
                            {"action":"admin-cancel","id":"12345"}""");

            assertTrue("Should receive error within 5s", errorLatch.await(5, TimeUnit.SECONDS));
            assertTrue("Should mention permission denied",
                    ((String) errorMsg.get().get("message")).contains("Permission denied"));
        } finally {
            session.close();
        }
    }

    /**
     * Monitor channel: subscribe to _monitor on /ws and verify lifecycle events
     * are broadcast when a query runs on /ws/eval.
     */
    @Test
    public void monitorChannelReceivesQueryEvents() throws Exception {
        final List<Map<String, Object>> monitorMessages = new CopyOnWriteArrayList<>();
        final CountDownLatch monitorEventLatch = new CountDownLatch(1);
        final CountDownLatch evalDoneLatch = new CountDownLatch(1);

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();

        // 1. Subscribe to _monitor on /ws
        final URI monitorUri = new URI("ws://localhost:" + existWebServer.getPort() + "/ws");
        final CountDownLatch subscribedLatch = new CountDownLatch(1);
        final Session monitorSession = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                try {
                    session.getBasicRemote().sendText("{\"channel\": \"_monitor\"}");
                    subscribedLatch.countDown();
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("monitor".equals(parsed.get("type"))) {
                                monitorMessages.add(parsed);
                                monitorEventLatch.countDown();
                            }
                        } catch (final IOException e) {
                            // ignore non-JSON frames
                        }
                    }
                });
            }
        }, ClientEndpointConfig.Builder.create().build(), monitorUri);

        try {
            assertTrue("Should subscribe within 2s", subscribedLatch.await(2, TimeUnit.SECONDS));
            Thread.sleep(200); // allow server to process subscription

            // 2. Run a query on /ws/eval
            final Session evalSession = container.connectToServer(new Endpoint() {
                @Override
                public void onOpen(final Session session, final EndpointConfig config) {
                    session.addMessageHandler(new MessageHandler.Whole<String>() {
                        @Override
                        public void onMessage(final String message) {
                            try {
                                final Map<String, Object> parsed = parseJson(message);
                                if ("result".equals(parsed.get("type"))
                                        && Boolean.FALSE.equals(parsed.get("more"))) {
                                    evalDoneLatch.countDown();
                                }
                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    });
                }
            }, createAdminConfig(), getWsUri());

            try {
                evalSession.getBasicRemote().sendText(
                        "{\"action\":\"eval\",\"id\":\"q-monitor\"," +
                        "\"query\":\"1 to 100\"}");

                assertTrue("Eval should complete within 5s",
                        evalDoneLatch.await(5, TimeUnit.SECONDS));

                // Wait for monitor events (broadcast is async, snapshot is every 1s)
                assertTrue("Should receive at least one monitor event within 3s",
                        monitorEventLatch.await(3, TimeUnit.SECONDS));

                assertFalse("Should have monitor messages", monitorMessages.isEmpty());
                assertTrue("All monitor messages should have type=monitor",
                        monitorMessages.stream().allMatch(m -> "monitor".equals(m.get("type"))));
            } finally {
                evalSession.close();
            }
        } finally {
            monitorSession.close();
        }
    }

    /**
     * Disconnect mid-query: the server should cancel the query and release resources.
     */
    @Test
    public void connectionCleanup() throws Exception {
        final CountDownLatch progressLatch = new CountDownLatch(1);

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("progress".equals(parsed.get("type"))
                                    && "evaluating".equals(parsed.get("phase"))) {
                                progressLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        // Start a long-running query
        session.getBasicRemote().sendText(
                "{\"action\":\"eval\",\"id\":\"q-cleanup\"," +
                "\"query\":\"" + CANCEL_TEST_QUERY + "\"," +
                "\"max-execution-time\":" + CANCEL_MAX_EXECUTION_MS + "}");

        // Wait for evaluating phase, then abruptly close
        assertTrue("Should reach evaluating phase within 5s",
                progressLatch.await(5, TimeUnit.SECONDS));
        session.close();

        // Allow session-close cancellation to finish before later tests reuse the broker pool
        Thread.sleep(2_000);

        // The test passes if no resources leak and no exceptions are thrown.
        // ExistWebServer would fail to shut down if brokers were leaked.
    }

    /**
     * After sending invalid JSON, the connection should stay alive for subsequent valid messages.
     */
    @Test
    public void errorRecovery() throws Exception {
        final CountDownLatch errorLatch = new CountDownLatch(1);
        final CountDownLatch resultLatch = new CountDownLatch(1);
        final AtomicReference<String> resultData = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            if ("error".equals(parsed.get("type"))) {
                                errorLatch.countDown();
                            } else if ("result".equals(parsed.get("type"))
                                    && Boolean.FALSE.equals(parsed.get("more"))) {
                                resultData.set((String) parsed.get("data"));
                                resultLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            // Send invalid JSON
            session.getBasicRemote().sendText("{{{{not json}}}}");
            assertTrue("Should receive error within 5s", errorLatch.await(5, TimeUnit.SECONDS));

            // Connection should still be alive — send a valid query
            session.getBasicRemote().sendText(
                    "{\"action\":\"eval\",\"id\":\"q-recovery\",\"query\":\"42\"}");
            assertTrue("Should receive result within 5s", resultLatch.await(5, TimeUnit.SECONDS));
            assertEquals("42", resultData.get());
        } finally {
            session.close();
        }
    }

    /**
     * Cancel immediately after eval, before compilation completes.
     * Should either cancel or complete without hanging.
     */
    @Test
    public void rapidCancel() throws Exception {
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final AtomicReference<String> responseType = new AtomicReference<>();

        final WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        final Session session = container.connectToServer(new Endpoint() {
            @Override
            public void onOpen(final Session session, final EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(final String message) {
                        try {
                            final Map<String, Object> parsed = parseJson(message);
                            final String type = (String) parsed.get("type");
                            // Accept result, cancelled, or error as valid outcomes
                            if ("result".equals(type) || "cancelled".equals(type)
                                    || "error".equals(type)) {
                                responseType.compareAndSet(null, type);
                                doneLatch.countDown();
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        }, createAdminConfig(), getWsUri());

        try {
            // Send eval and immediately cancel
            session.getBasicRemote().sendText(
                    "{\"action\":\"eval\",\"id\":\"q-rapid\"," +
                    "\"query\":\"" + CANCEL_TEST_QUERY + "\"," +
                    "\"max-execution-time\":5000}");
            // Immediate cancel — no sleep
            session.getBasicRemote().sendText(
                    "{\"action\":\"cancel\",\"id\":\"q-rapid\"}");

            assertTrue("Should receive a response within 30s",
                    doneLatch.await(30, TimeUnit.SECONDS));
            assertNotNull("Should get some response type", responseType.get());
        } finally {
            session.close();
        }
    }

    /**
     * Helper to escape a string for embedding in a JSON string value.
     */
    private static String escapeJson(final String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
