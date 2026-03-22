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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * JSON protocol messages for the /ws/eval WebSocket endpoint.
 *
 * All messages are JSON objects. Client messages have an "action" field;
 * server messages have a "type" field.
 */
public final class EvalProtocol {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    public static final int DEFAULT_CHUNK_SIZE = 1000;
    public static final long DEFAULT_MAX_EXECUTION_TIME = 0; // no limit

    // Client actions
    public static final String ACTION_EVAL = "eval";
    public static final String ACTION_CANCEL = "cancel";
    public static final String ACTION_COMPILE = "compile";
    public static final String ACTION_ADMIN_CANCEL = "admin-cancel";

    // Server message types
    public static final String TYPE_PROGRESS = "progress";
    public static final String TYPE_RESULT = "result";
    public static final String TYPE_COMPILE = "compile";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_CANCELLED = "cancelled";

    // Phases
    public static final String PHASE_PARSING = "parsing";
    public static final String PHASE_COMPILING = "compiling";
    public static final String PHASE_EVALUATING = "evaluating";
    public static final String PHASE_SERIALIZING = "serializing";
    public static final String PHASE_COMPLETE = "complete";

    private EvalProtocol() {
        // utility class
    }

    /**
     * Parsed client request message.
     */
    public static final class ClientMessage {
        public final String action;
        public final String id;
        @Nullable public final String query;
        @Nullable public final Properties serialization;
        @Nullable public final Map<String, String> variables;
        @Nullable public final String context;
        @Nullable public final String moduleLoadPath;
        public final long maxExecutionTime;
        public final boolean streaming;
        public final int chunkSize;

        ClientMessage(final String action, final String id, @Nullable final String query,
                      @Nullable final Properties serialization,
                      @Nullable final Map<String, String> variables,
                      @Nullable final String context,
                      @Nullable final String moduleLoadPath,
                      final long maxExecutionTime,
                      final boolean streaming,
                      final int chunkSize) {
            this.action = action;
            this.id = id;
            this.query = query;
            this.serialization = serialization;
            this.variables = variables;
            this.context = context;
            this.moduleLoadPath = moduleLoadPath;
            this.maxExecutionTime = maxExecutionTime;
            this.streaming = streaming;
            this.chunkSize = chunkSize;
        }
    }

    /**
     * Timing breakdown for query execution phases.
     */
    public static final class Timing {
        public long parse;
        public long compile;
        public long evaluate;
        public long serialize;
        public long total;
    }

    /**
     * Parse a JSON client message.
     */
    public static ClientMessage parseClientMessage(final String json) throws IOException {
        String action = null;
        String id = null;
        String query = null;
        Properties serialization = null;
        Map<String, String> variables = null;
        String context = null;
        String moduleLoadPath = null;
        long maxExecutionTime = DEFAULT_MAX_EXECUTION_TIME;
        boolean streaming = true;
        int chunkSize = DEFAULT_CHUNK_SIZE;

        try (final JsonParser parser = JSON_FACTORY.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected JSON object");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                final String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "action":
                        action = parser.getValueAsString();
                        break;
                    case "id":
                        id = parser.getValueAsString();
                        break;
                    case "query":
                        query = parser.getValueAsString();
                        break;
                    case "context":
                        context = parser.getValueAsString();
                        break;
                    case "module-load-path":
                        moduleLoadPath = parser.getValueAsString();
                        break;
                    case "max-execution-time":
                        maxExecutionTime = parser.getValueAsLong();
                        break;
                    case "streaming":
                        streaming = parser.getValueAsBoolean();
                        break;
                    case "chunk-size":
                        chunkSize = parser.getValueAsInt();
                        break;
                    case "serialization":
                        serialization = parseObject(parser);
                        break;
                    case "variables":
                        variables = parseStringMap(parser);
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }
        }

        if (action == null) {
            throw new IOException("Missing required field: action");
        }
        if (id == null) {
            throw new IOException("Missing required field: id");
        }

        return new ClientMessage(action, id, query, serialization, variables,
                context, moduleLoadPath, maxExecutionTime, streaming, chunkSize);
    }

    private static Properties parseObject(final JsonParser parser) throws IOException {
        final Properties props = new Properties();
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return props;
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            final String key = parser.currentName();
            parser.nextToken();
            props.setProperty(key, parser.getValueAsString());
        }
        return props;
    }

    private static Map<String, String> parseStringMap(final JsonParser parser) throws IOException {
        final Map<String, String> map = new HashMap<>();
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return map;
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            final String key = parser.currentName();
            parser.nextToken();
            map.put(key, parser.getValueAsString());
        }
        return map;
    }

    // --- Server message builders ---

    public static String progressMessage(final String id, final String phase,
                                          final long items, final long elapsed) throws IOException {
        final StringWriter sw = new StringWriter();
        try (final JsonGenerator gen = JSON_FACTORY.createGenerator(sw)) {
            gen.writeStartObject();
            gen.writeStringField("type", TYPE_PROGRESS);
            gen.writeStringField("id", id);
            gen.writeStringField("phase", phase);
            gen.writeNumberField("items", items);
            gen.writeNumberField("elapsed", elapsed);
            gen.writeEndObject();
        }
        return sw.toString();
    }

    public static String resultMessage(final String id, final int chunk,
                                        final String data, final boolean more,
                                        @Nullable final Timing timing,
                                        final long items) throws IOException {
        final StringWriter sw = new StringWriter();
        try (final JsonGenerator gen = JSON_FACTORY.createGenerator(sw)) {
            gen.writeStartObject();
            gen.writeStringField("type", TYPE_RESULT);
            gen.writeStringField("id", id);
            gen.writeNumberField("chunk", chunk);
            gen.writeStringField("data", data);
            gen.writeBooleanField("more", more);
            if (!more && timing != null) {
                writeTiming(gen, timing);
            }
            if (!more) {
                gen.writeNumberField("items", items);
                gen.writeBooleanField("truncated", false);
            }
            gen.writeEndObject();
        }
        return sw.toString();
    }

    public static String compileResultMessage(final String id, final boolean success,
                                               @Nullable final String errorCode,
                                               @Nullable final String errorMessage,
                                               final int line, final int column) throws IOException {
        final StringWriter sw = new StringWriter();
        try (final JsonGenerator gen = JSON_FACTORY.createGenerator(sw)) {
            gen.writeStartObject();
            gen.writeStringField("type", TYPE_COMPILE);
            gen.writeStringField("id", id);
            gen.writeBooleanField("success", success);
            if (!success) {
                gen.writeArrayFieldStart("diagnostics");
                gen.writeStartObject();
                gen.writeNumberField("line", line);
                gen.writeNumberField("column", column);
                gen.writeStringField("severity", "error");
                if (errorCode != null) {
                    gen.writeStringField("code", errorCode);
                }
                if (errorMessage != null) {
                    gen.writeStringField("message", errorMessage);
                }
                gen.writeEndObject();
                gen.writeEndArray();
            }
            gen.writeEndObject();
        }
        return sw.toString();
    }

    public static String errorMessage(final String id, @Nullable final String code,
                                       final String message, final int line,
                                       final int column,
                                       @Nullable final Timing timing) throws IOException {
        final StringWriter sw = new StringWriter();
        try (final JsonGenerator gen = JSON_FACTORY.createGenerator(sw)) {
            gen.writeStartObject();
            gen.writeStringField("type", TYPE_ERROR);
            gen.writeStringField("id", id);
            if (code != null) {
                gen.writeStringField("code", code);
            }
            gen.writeStringField("message", message);
            gen.writeNumberField("line", line);
            gen.writeNumberField("column", column);
            if (timing != null) {
                writeTiming(gen, timing);
            }
            gen.writeEndObject();
        }
        return sw.toString();
    }

    public static String cancelledMessage(final String id, final long items,
                                           @Nullable final Timing timing) throws IOException {
        final StringWriter sw = new StringWriter();
        try (final JsonGenerator gen = JSON_FACTORY.createGenerator(sw)) {
            gen.writeStartObject();
            gen.writeStringField("type", TYPE_CANCELLED);
            gen.writeStringField("id", id);
            gen.writeNumberField("items", items);
            if (timing != null) {
                writeTiming(gen, timing);
            }
            gen.writeEndObject();
        }
        return sw.toString();
    }

    private static void writeTiming(final JsonGenerator gen, final Timing timing) throws IOException {
        gen.writeObjectFieldStart("timing");
        gen.writeNumberField("parse", timing.parse);
        gen.writeNumberField("compile", timing.compile);
        gen.writeNumberField("evaluate", timing.evaluate);
        gen.writeNumberField("serialize", timing.serialize);
        gen.writeNumberField("total", timing.total);
        gen.writeEndObject();
    }
}
