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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.*;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;

import jakarta.websocket.Session;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Executes XQuery expressions for the /ws/eval endpoint with support for
 * streaming results, progress reporting, cancellation, and timing.
 */
public final class QueryExecutor {

    private static final Logger LOG = LogManager.getLogger(QueryExecutor.class);

    private static final long PROGRESS_INTERVAL_MS = 500;

    private final BrokerPool pool;

    public QueryExecutor(final BrokerPool pool) {
        this.pool = pool;
    }

    /**
     * Execute a query and stream results back over the WebSocket session.
     */
    public void execute(final Session wsSession, final EvalSession evalSession,
                        final EvalProtocol.ClientMessage msg) {
        final EvalProtocol.Timing timing = new EvalProtocol.Timing();
        final long startTime = System.currentTimeMillis();
        final String user = evalSession.getSubject().getName();

        try (final DBBroker broker = pool.get(Optional.of(evalSession.getSubject()))) {
            // Parse phase
            sendProgress(wsSession, msg.id, EvalProtocol.PHASE_PARSING, 0, 0);
            QueryMonitorBroadcaster.broadcastEvent("started", msg.id, user, msg.query,
                    EvalProtocol.PHASE_PARSING, 0, 0);
            final long parseStart = System.currentTimeMillis();

            final XQueryContext context = new XQueryContext(pool);
            configureContext(context, msg);

            final XQuery xquery = pool.getXQueryService();
            final CompiledXQuery compiled;

            // Compile phase
            try {
                compiled = xquery.compile(context, new StringSource(msg.query));
            } catch (final XPathException e) {
                timing.parse = System.currentTimeMillis() - parseStart;
                reportError(wsSession, evalSession, msg, timing, startTime, ErrorInfo.of(e));
                return;
            } catch (final IOException e) {
                reportError(wsSession, evalSession, msg, timing, startTime, ErrorInfo.of(e.getMessage()));
                return;
            }

            timing.parse = System.currentTimeMillis() - parseStart;
            final long compileEnd = System.currentTimeMillis();
            timing.compile = compileEnd - parseStart - timing.parse;

            sendProgress(wsSession, msg.id, EvalProtocol.PHASE_COMPILING, 0,
                    System.currentTimeMillis() - startTime);

            // Set timeout via watchdog
            final XQueryWatchDog watchDog = context.getWatchDog();
            if (msg.maxExecutionTime > 0) {
                watchDog.setTimeout(msg.maxExecutionTime);
            }
            evalSession.registerQuery(msg.id, watchDog);

            try {
                // Evaluate phase
                sendProgress(wsSession, msg.id, EvalProtocol.PHASE_EVALUATING, 0,
                        System.currentTimeMillis() - startTime);
                QueryMonitorBroadcaster.broadcastEvent("progress", msg.id, user, msg.query,
                        EvalProtocol.PHASE_EVALUATING, 0, System.currentTimeMillis() - startTime);
                final long evalStart = System.currentTimeMillis();

                final Sequence result;
                try {
                    result = xquery.execute(broker, compiled, null, new Properties(), true);
                } catch (final TerminatedException e) {
                    timing.evaluate = System.currentTimeMillis() - evalStart;
                    reportTerminationOrError(wsSession, evalSession, msg, timing, startTime,
                            watchDog, ErrorInfo.of(e.getMessage(), e.getLine(), e.getColumn()));
                    return;
                } catch (final XPathException e) {
                    timing.evaluate = System.currentTimeMillis() - evalStart;
                    reportTerminationOrError(wsSession, evalSession, msg, timing, startTime,
                            watchDog, ErrorInfo.of(e));
                    return;
                }

                timing.evaluate = System.currentTimeMillis() - evalStart;

                // Serialize phase
                sendProgress(wsSession, msg.id, EvalProtocol.PHASE_SERIALIZING, 0,
                        System.currentTimeMillis() - startTime);
                final long serStart = System.currentTimeMillis();

                final long itemCount = result.getItemCount();
                final Properties outputProperties = msg.serialization != null
                        ? msg.serialization : new Properties();

                try {
                    if (msg.stream.enabled() && itemCount > msg.stream.chunkSize()) {
                        streamResults(wsSession, msg.id, broker, result, outputProperties,
                                msg.stream.chunkSize(), timing, startTime, watchDog);
                    } else {
                        final String serialized = serializeAll(broker, result, outputProperties);
                        timing.serialize = System.currentTimeMillis() - serStart;
                        timing.total = System.currentTimeMillis() - startTime;

                        sendResult(wsSession, msg.id, 1, serialized, false, timing, itemCount);
                    }
                    QueryMonitorBroadcaster.broadcastEvent("completed", msg.id, user, msg.query,
                            null, itemCount, System.currentTimeMillis() - startTime);
                } catch (final SAXException | XPathException e) {
                    timing.serialize = System.currentTimeMillis() - serStart;
                    reportError(wsSession, evalSession, msg, timing, startTime, ErrorInfo.of(e.getMessage()));
                }
            } finally {
                evalSession.unregisterQuery(msg.id);
                context.runCleanupTasks();
            }

        } catch (final EXistException | PermissionDeniedException e) {
            reportError(wsSession, evalSession, msg, timing, startTime, ErrorInfo.of(e.getMessage()));
        }
    }

    /**
     * Bundle describing a query failure (either a typed XPathException
     * or a free-form message + source location).
     */
    private record ErrorInfo(@Nullable XPathException xpe,
                             @Nullable String message,
                             int line, int column) {
        static ErrorInfo of(final XPathException xpe) {
            return new ErrorInfo(xpe, null, 0, 0);
        }
        static ErrorInfo of(final String message, final int line, final int column) {
            return new ErrorInfo(null, message, line, column);
        }
        static ErrorInfo of(final String message) {
            return new ErrorInfo(null, message, 0, 0);
        }
    }

    private void reportError(final Session wsSession, final EvalSession evalSession,
                             final EvalProtocol.ClientMessage msg, final EvalProtocol.Timing timing,
                             final long startTime, final ErrorInfo error) {
        timing.total = System.currentTimeMillis() - startTime;
        if (error.xpe() != null) {
            sendError(wsSession, msg.id, error.xpe(), timing);
        } else {
            sendError(wsSession, msg.id, null, error.message(), error.line(), error.column(), timing);
        }
        QueryMonitorBroadcaster.broadcastEvent("error", msg.id,
                evalSession.getSubject().getName(), msg.query, null, 0, timing.total);
    }

    private void reportTerminationOrError(final Session wsSession, final EvalSession evalSession,
                                          final EvalProtocol.ClientMessage msg,
                                          final EvalProtocol.Timing timing, final long startTime,
                                          final XQueryWatchDog watchDog,
                                          final ErrorInfo error) {
        if (watchDog.isTerminating()) {
            timing.total = System.currentTimeMillis() - startTime;
            sendCancelled(wsSession, msg.id, 0, timing);
            QueryMonitorBroadcaster.broadcastEvent("cancelled", msg.id,
                    evalSession.getSubject().getName(), msg.query, null, 0, timing.total);
        } else {
            reportError(wsSession, evalSession, msg, timing, startTime, error);
        }
    }

    /**
     * Compile-check a query without executing it.
     */
    public void compile(final Session wsSession, final EvalSession evalSession,
                        final EvalProtocol.ClientMessage msg) {
        try (final DBBroker broker = pool.get(Optional.of(evalSession.getSubject()))) {
            final XQueryContext context = new XQueryContext(broker.getBrokerPool());
            configureContext(context, msg);

            final XQuery xquery = pool.getXQueryService();
            try {
                xquery.compile(context, new StringSource(msg.query));
                sendCompileResult(wsSession, msg.id, true, null, null, 0, 0);
            } catch (final XPathException e) {
                sendCompileResult(wsSession, msg.id, false,
                        e.getErrorCode() != null ? e.getErrorCode().toString() : null,
                        e.getDetailMessage(), e.getLine(), e.getColumn());
            } catch (final IOException e) {
                sendCompileResult(wsSession, msg.id, false, null, e.getMessage(), 0, 0);
            } finally {
                context.runCleanupTasks();
            }
        } catch (final EXistException | PermissionDeniedException e) {
            sendError(wsSession, msg.id, null, e.getMessage(), 0, 0, null);
        }
    }

    private void configureContext(final XQueryContext context,
                                  final EvalProtocol.ClientMessage msg) {
        if (msg.moduleLoadPath != null) {
            context.setModuleLoadPath(msg.moduleLoadPath);
        }
        if (msg.context != null) {
            try {
                final XmldbURI baseUri = XmldbURI.create(msg.context);
                context.setStaticallyKnownDocuments(new XmldbURI[]{baseUri});
                context.setBaseURI(new AnyURIValue(msg.context));
            } catch (final XPathException e) {
                LOG.warn("Invalid context URI: {}", msg.context, e);
            }
        }
        if (msg.variables != null) {
            for (final Map.Entry<String, String> entry : msg.variables.entrySet()) {
                try {
                    context.declareVariable(entry.getKey(), entry.getValue());
                } catch (final XPathException e) {
                    LOG.warn("Failed to declare variable ${}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
    }

    private void streamResults(final Session wsSession, final String queryId,
                                final DBBroker broker, final Sequence result,
                                final Properties outputProperties, final int chunkSize,
                                final EvalProtocol.Timing timing, final long startTime,
                                final XQueryWatchDog watchDog) {
        final long serStart = System.currentTimeMillis();
        final long totalItems = result.getItemCount();
        int chunkNum = 0;
        long itemsSent = 0;

        try {
            final SequenceIterator iter = result.iterate();
            while (iter.hasNext()) {
                if (watchDog.isTerminating()) {
                    timing.serialize = System.currentTimeMillis() - serStart;
                    timing.total = System.currentTimeMillis() - startTime;
                    sendCancelled(wsSession, queryId, itemsSent, timing);
                    return;
                }

                // collect a chunk
                final ValueSequence chunk = new ValueSequence();
                for (int i = 0; i < chunkSize && iter.hasNext(); i++) {
                    chunk.add(iter.nextItem());
                }

                chunkNum++;
                itemsSent += chunk.getItemCount();
                final boolean more = iter.hasNext();

                final String data = serializeAll(broker, chunk, outputProperties);

                if (!more) {
                    timing.serialize = System.currentTimeMillis() - serStart;
                    timing.total = System.currentTimeMillis() - startTime;
                }

                sendResult(wsSession, queryId, chunkNum, data, more,
                        more ? null : timing, totalItems);

                // send progress during streaming
                if (more && (System.currentTimeMillis() - startTime) % PROGRESS_INTERVAL_MS < 50) {
                    sendProgress(wsSession, queryId, EvalProtocol.PHASE_SERIALIZING,
                            itemsSent, System.currentTimeMillis() - startTime);
                }
            }
        } catch (final XPathException | SAXException e) {
            timing.serialize = System.currentTimeMillis() - serStart;
            timing.total = System.currentTimeMillis() - startTime;
            sendError(wsSession, queryId, null, e.getMessage(), 0, 0, timing);
        }
    }

    private String serializeAll(final DBBroker broker, final Sequence sequence,
                                 final Properties outputProperties)
            throws SAXException, XPathException {
        final StringWriter writer = new StringWriter();
        final XQuerySerializer serializer = new XQuerySerializer(broker, outputProperties, writer);
        serializer.serialize(sequence);
        return writer.toString();
    }

    // --- WebSocket message senders ---

    private void sendProgress(final Session session, final String id,
                               final String phase, final long items, final long elapsed) {
        try {
            session.getBasicRemote().sendText(
                    EvalProtocol.progressMessage(id, phase, items, elapsed));
        } catch (final IOException e) {
            LOG.debug("Failed to send progress: {}", e.getMessage());
        }
        // Also broadcast to monitor channel (user/query not available here,
        // but the snapshot fills in full details)
        QueryMonitorBroadcaster.broadcastEvent("progress", id, "", null, phase, items, elapsed);
    }

    private void sendResult(final Session session, final String id, final int chunk,
                             final String data, final boolean more,
                             @Nullable final EvalProtocol.Timing timing, final long items) {
        try {
            session.getBasicRemote().sendText(
                    EvalProtocol.resultMessage(id, chunk, data, more, timing, items));
        } catch (final IOException e) {
            LOG.debug("Failed to send result: {}", e.getMessage());
        }
    }

    private void sendError(final Session session, final String id,
                            @Nullable final XPathException xpe,
                            @Nullable final EvalProtocol.Timing timing) {
        final String code = xpe != null && xpe.getErrorCode() != null
                ? xpe.getErrorCode().toString() : null;
        final String message = xpe != null ? xpe.getDetailMessage() : "Unknown error";
        final int line = xpe != null ? xpe.getLine() : 0;
        final int column = xpe != null ? xpe.getColumn() : 0;
        sendError(session, id, code, message, line, column, timing);
    }

    private void sendError(final Session session, final String id,
                            @Nullable final String code, final String message,
                            final int line, final int column,
                            @Nullable final EvalProtocol.Timing timing) {
        try {
            session.getBasicRemote().sendText(
                    EvalProtocol.errorMessage(id, code, message, line, column, timing));
        } catch (final IOException e) {
            LOG.debug("Failed to send error: {}", e.getMessage());
        }
    }

    private void sendCancelled(final Session session, final String id,
                                final long items,
                                @Nullable final EvalProtocol.Timing timing) {
        try {
            session.getBasicRemote().sendText(
                    EvalProtocol.cancelledMessage(id, items, timing));
        } catch (final IOException e) {
            LOG.debug("Failed to send cancelled: {}", e.getMessage());
        }
    }

    private void sendCompileResult(final Session session, final String id,
                                    final boolean success,
                                    @Nullable final String errorCode,
                                    @Nullable final String errorMessage,
                                    final int line, final int column) {
        try {
            session.getBasicRemote().sendText(
                    EvalProtocol.compileResultMessage(id, success, errorCode, errorMessage, line, column));
        } catch (final IOException e) {
            LOG.debug("Failed to send compile result: {}", e.getMessage());
        }
    }
}
