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

import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.ProcessMonitor;

import jakarta.websocket.*;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * WebSocket endpoint for real-time, streaming XQuery evaluation.
 *
 * Supports:
 * <ul>
 *   <li>Query evaluation with streaming results</li>
 *   <li>Query cancellation</li>
 *   <li>Compile-only checks (no execution)</li>
 *   <li>Progress reporting</li>
 *   <li>Timing breakdown (parse/compile/evaluate/serialize)</li>
 * </ul>
 *
 * Authentication is performed during the WebSocket handshake using
 * Basic authentication from the HTTP upgrade request headers.
 */
@ServerEndpoint(
        value = "/ws/eval",
        configurator = EvalWebSocketEndpoint.AuthConfigurator.class
)
public class EvalWebSocketEndpoint {

    private static final Logger LOG = LogManager.getLogger(EvalWebSocketEndpoint.class);
    private static final String USER_PROPERTY = "exist.eval.subject";

    private static final Map<Session, EvalSession> sessions = new ConcurrentHashMap<>();
    private static ExecutorService queryExecutorService = createExecutorService();

    private static ExecutorService createExecutorService() {
        return Executors.newCachedThreadPool(r -> {
            final Thread t = new Thread(r, "ws-eval-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Shutdown the WebSocket eval subsystem.
     */
    public static synchronized void shutdown() {
        if (queryExecutorService != null) {
            queryExecutorService.shutdown();
            queryExecutorService = null;
        }
    }

    /**
     * Re-initialize the executor service if needed.
     */
    private static synchronized ExecutorService getExecutorService() {
        if (queryExecutorService == null) {
            queryExecutorService = createExecutorService();
        }
        return queryExecutorService;
    }

    /**
     * Configurator that extracts Basic auth credentials during the WebSocket
     * handshake and authenticates the user against eXist's SecurityManager.
     */
    public static class AuthConfigurator extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(final ServerEndpointConfig sec,
                                     final HandshakeRequest request,
                                     final HandshakeResponse response) {
            final Map<String, List<String>> headers = request.getHeaders();
            final List<String> authHeaders = headers.get("authorization");

            Subject subject = null;
            if (authHeaders != null && !authHeaders.isEmpty()) {
                final String credentials = authHeaders.get(0);
                if (credentials.toLowerCase().startsWith("basic ")) {
                    try {
                        final byte[] decoded = Base64.decodeBase64(
                                credentials.substring("basic ".length()));
                        final String s = new String(decoded, UTF_8);
                        final int p = s.indexOf(':');
                        final String username = p < 0 ? s : s.substring(0, p);
                        final String password = p < 0 ? null : s.substring(p + 1);

                        final BrokerPool pool = BrokerPool.getInstance();
                        final SecurityManager secman = pool.getSecurityManager();
                        subject = secman.authenticate(username, password);
                    } catch (final AuthenticationException e) {
                        LOG.warn("WebSocket eval authentication failed: {}", e.getMessage());
                    } catch (final EXistException e) {
                        LOG.error("Failed to get BrokerPool for WebSocket auth: {}", e.getMessage());
                    }
                }
            }

            // No guest fallback — authentication is required for WebSocket eval.
            // Unauthenticated connections will be rejected in onOpen().
            if (subject != null) {
                sec.getUserProperties().put(USER_PROPERTY, subject);
            } else {
                LOG.debug("WebSocket eval connection without valid credentials — will be rejected in onOpen");
            }
        }
    }

    private static final int MAX_TEXT_MESSAGE_SIZE = 10 * 1024 * 1024; // 10MB

    @OnOpen
    public void onOpen(final Session session, final EndpointConfig config) {
        session.setMaxIdleTimeout(0); // no idle timeout for eval sessions
        session.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_SIZE);

        final Subject subject = (Subject) config.getUserProperties().get(USER_PROPERTY);
        if (subject == null) {
            try {
                session.close(new CloseReason(
                        CloseReason.CloseCodes.VIOLATED_POLICY, "Authentication failed"));
            } catch (final IOException e) {
                LOG.debug("Error closing unauthenticated session: {}", e.getMessage());
            }
            return;
        }

        final EvalSession evalSession = new EvalSession(subject);
        sessions.put(session, evalSession);
        LOG.debug("Eval WebSocket opened for user: {}", subject.getName());
    }

    @OnMessage
    public void onMessage(final String message, final Session session) {
        final EvalSession evalSession = sessions.get(session);
        if (evalSession == null) {
            LOG.warn("Received message on unregistered session");
            return;
        }

        final EvalProtocol.ClientMessage msg;
        try {
            msg = EvalProtocol.parseClientMessage(message);
        } catch (final IOException e) {
            try {
                session.getBasicRemote().sendText(
                        EvalProtocol.errorMessage("unknown", null,
                                "Invalid message: " + e.getMessage(), 0, 0, null));
            } catch (final IOException ex) {
                LOG.debug("Failed to send parse error: {}", ex.getMessage());
            }
            return;
        }

        switch (msg.action) {
            case EvalProtocol.ACTION_EVAL:
                handleEval(session, evalSession, msg);
                break;
            case EvalProtocol.ACTION_CANCEL:
                handleCancel(evalSession, msg);
                break;
            case EvalProtocol.ACTION_COMPILE:
                handleCompile(session, evalSession, msg);
                break;
            case EvalProtocol.ACTION_ADMIN_CANCEL:
                handleAdminCancel(session, evalSession, msg);
                break;
            default:
                try {
                    session.getBasicRemote().sendText(
                            EvalProtocol.errorMessage(msg.id, null,
                                    "Unknown action: " + msg.action, 0, 0, null));
                } catch (final IOException e) {
                    LOG.debug("Failed to send unknown action error: {}", e.getMessage());
                }
                break;
        }
    }

    @OnClose
    public void onClose(final Session session, final CloseReason reason) {
        final EvalSession evalSession = sessions.remove(session);
        if (evalSession != null) {
            evalSession.cancelAll();
            LOG.debug("Eval WebSocket closed: {}", reason.getReasonPhrase());
        }
    }

    @OnError
    public void onError(final Session session, final Throwable error) {
        if (error.getMessage() != null && error.getMessage().contains("Text message size")) {
            LOG.warn("WebSocket message exceeds {}MB buffer limit: {}", MAX_TEXT_MESSAGE_SIZE / (1024 * 1024), error.getMessage());
        } else {
            LOG.warn("WebSocket eval error: {}", error.getMessage(), error);
        }
        final EvalSession evalSession = sessions.remove(session);
        if (evalSession != null) {
            evalSession.cancelAll();
        }
    }

    private void handleEval(final Session session, final EvalSession evalSession,
                             final EvalProtocol.ClientMessage msg) {
        if (msg.query == null || msg.query.isEmpty()) {
            try {
                session.getBasicRemote().sendText(
                        EvalProtocol.errorMessage(msg.id, null,
                                "Missing required field: query", 0, 0, null));
            } catch (final IOException e) {
                LOG.debug("Failed to send missing query error: {}", e.getMessage());
            }
            return;
        }

        // Execute on a worker thread to avoid blocking the WebSocket message thread
        getExecutorService().submit(() -> {
            try {
                final BrokerPool pool = BrokerPool.getInstance();
                final QueryExecutor executor = new QueryExecutor(pool);
                executor.execute(session, evalSession, msg);
            } catch (final EXistException e) {
                try {
                    session.getBasicRemote().sendText(
                            EvalProtocol.errorMessage(msg.id, null,
                                    "Database unavailable: " + e.getMessage(), 0, 0, null));
                } catch (final IOException ex) {
                    LOG.debug("Failed to send database error: {}", ex.getMessage());
                }
            }
        });
    }

    private void handleCancel(final EvalSession evalSession,
                               final EvalProtocol.ClientMessage msg) {
        final boolean cancelled = evalSession.cancelQuery(msg.id);
        if (!cancelled) {
            LOG.debug("Cancel requested for unknown query: {}", msg.id);
        }
    }

    /**
     * Admin cancel: kill any running query by its context identity hash code.
     * Requires DBA role. Uses the ProcessMonitor (same as system:kill-running-xquery).
     */
    private void handleAdminCancel(final Session session, final EvalSession evalSession,
                                    final EvalProtocol.ClientMessage msg) {
        if (!evalSession.getSubject().hasDbaRole()) {
            try {
                session.getBasicRemote().sendText(
                        EvalProtocol.errorMessage(msg.id, null,
                                "Permission denied: admin-cancel requires DBA role", 0, 0, null));
            } catch (final IOException e) {
                LOG.debug("Failed to send permission error: {}", e.getMessage());
            }
            return;
        }

        try {
            final BrokerPool pool = BrokerPool.getInstance();
            final ProcessMonitor monitor = pool.getProcessMonitor();
            final int targetId = Integer.parseInt(msg.id);

            boolean found = false;
            for (final org.exist.xquery.XQueryWatchDog wd : monitor.getRunningXQueries()) {
                if (System.identityHashCode(wd.getContext()) == targetId) {
                    wd.kill(0);
                    found = true;
                    break;
                }
            }

            if (!found) {
                LOG.debug("Admin cancel: query {} not found", msg.id);
            }
        } catch (final Exception e) {
            LOG.warn("Admin cancel failed: {}", e.getMessage());
        }
    }

    private void handleCompile(final Session session, final EvalSession evalSession,
                                final EvalProtocol.ClientMessage msg) {
        if (msg.query == null || msg.query.isEmpty()) {
            try {
                session.getBasicRemote().sendText(
                        EvalProtocol.errorMessage(msg.id, null,
                                "Missing required field: query", 0, 0, null));
            } catch (final IOException e) {
                LOG.debug("Failed to send missing query error: {}", e.getMessage());
            }
            return;
        }

        getExecutorService().submit(() -> {
            try {
                final BrokerPool pool = BrokerPool.getInstance();
                final QueryExecutor executor = new QueryExecutor(pool);
                executor.compile(session, evalSession, msg);
            } catch (final EXistException e) {
                try {
                    session.getBasicRemote().sendText(
                            EvalProtocol.errorMessage(msg.id, null,
                                    "Database unavailable: " + e.getMessage(), 0, 0, null));
                } catch (final IOException ex) {
                    LOG.debug("Failed to send database error: {}", ex.getMessage());
                }
            }
        });
    }
}
