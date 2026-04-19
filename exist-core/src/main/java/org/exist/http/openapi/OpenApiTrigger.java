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
package org.exist.http.openapi;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.triggers.SAXTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Collection trigger that watches for {@code controller.json} and {@code api.json}
 * files being stored in the database. When found, parses the OpenAPI spec(s) and
 * registers routes with the {@link OpenApiServiceRegistry}.
 * <p>
 * Configure in conf.xml:
 * <pre>
 * &lt;trigger class="org.exist.http.openapi.OpenApiTrigger"/&gt;
 * </pre>
 * </p>
 */
public class OpenApiTrigger extends SAXTrigger {

    private static final Logger LOG = LogManager.getLogger(OpenApiTrigger.class);

    @Override
    public void beforeCreateDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws TriggerException {
    }

    @Override
    public void afterCreateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
        processDocument(broker, document);
    }

    @Override
    public void beforeUpdateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
    }

    @Override
    public void afterUpdateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
        processDocument(broker, document);
    }

    @Override
    public void beforeCopyDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
    }

    @Override
    public void afterCopyDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI oldUri) throws TriggerException {
        processDocument(broker, document);
    }

    @Override
    public void beforeMoveDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
    }

    @Override
    public void afterMoveDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI oldUri) throws TriggerException {
        processDocument(broker, document);
    }

    @Override
    public void beforeDeleteDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
        final String fileName = document.getFileURI().lastSegment().toString();
        if ("controller.json".equals(fileName) || fileName.endsWith("api.json")) {
            final String collectionPath = document.getCollection().getURI().getCollectionPath();
            OpenApiServiceRegistry.getInstance().deregisterRoutes(collectionPath);
        }
    }

    @Override
    public void afterDeleteDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws TriggerException {
    }

    @Override
    public void beforeUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
    }

    @Override
    public void afterUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
    }

    /**
     * Process a document that was just stored/updated. If it's a controller.json
     * or api.json, parse and register routes.
     */
    private void processDocument(final DBBroker broker, final DocumentImpl document) {
        final String fileName = document.getFileURI().lastSegment().toString();
        if (!"controller.json".equals(fileName) && !fileName.endsWith("api.json")) {
            return;
        }

        if (!(document instanceof BinaryDocument)) {
            LOG.debug("Ignoring non-binary document: {}", document.getURI());
            return;
        }

        final String collectionPath = document.getCollection().getURI().getCollectionPath();

        try {
            if ("controller.json".equals(fileName)) {
                processControllerJson(broker, (BinaryDocument) document, collectionPath);
            } else {
                // Direct api.json — register with default settings
                processApiJson(broker, (BinaryDocument) document, collectionPath, null);
            }
        } catch (final Exception e) {
            LOG.error("Failed to process OpenAPI document {}: {}", document.getURI(), e.getMessage(), e);
        }
    }

    /**
     * Process a controller.json file. Reads the "apis" array and processes each spec.
     */
    private void processControllerJson(final DBBroker broker, final BinaryDocument document,
                                        final String collectionPath) throws IOException, org.exist.security.PermissionDeniedException {
        final String json = readBinaryDocument(broker, document);
        LOG.info("Processing controller.json for collection {}", collectionPath);

        // Simple JSON parsing for the "apis" array
        // Extract spec paths from: {"apis": [{"spec": "modules/api.json", ...}]}
        final List<Map<String, String>> apis = parseApisArray(json);

        if (apis.isEmpty()) {
            LOG.warn("No 'apis' found in controller.json for {}", collectionPath);
            return;
        }

        final List<OpenApiServiceRegistry.Route> allRoutes = new ArrayList<>();
        for (final Map<String, String> api : apis) {
            final String specPath = api.get("spec");
            if (specPath == null) {
                continue;
            }
            final String prefix = api.get("prefix");

            // Resolve spec path relative to collection
            final XmldbURI specUri = XmldbURI.create(collectionPath).append(specPath);
            final DocumentImpl specDoc = broker.getResource(specUri, org.exist.security.Permission.READ);
            if (specDoc == null) {
                LOG.warn("OpenAPI spec not found: {}", specUri);
                continue;
            }

            final String specJson = readBinaryDocument(broker, (BinaryDocument) specDoc);
            final List<OpenApiServiceRegistry.Route> routes = parseOpenApiSpec(specJson, prefix);
            allRoutes.addAll(routes);
        }

        if (!allRoutes.isEmpty()) {
            OpenApiServiceRegistry.getInstance().registerRoutes(collectionPath, allRoutes);
        }
    }

    /**
     * Process a standalone api.json file (no controller.json).
     */
    private void processApiJson(final DBBroker broker, final BinaryDocument document,
                                 final String collectionPath, final String prefix) throws IOException {
        final String json = readBinaryDocument(broker, document);
        LOG.info("Processing api.json for collection {}", collectionPath);

        final List<OpenApiServiceRegistry.Route> routes = parseOpenApiSpec(json, prefix);
        if (!routes.isEmpty()) {
            OpenApiServiceRegistry.getInstance().registerRoutes(collectionPath, routes);
        }
    }

    /**
     * Parse an OpenAPI 3.0 spec and extract routes.
     * Uses Jackson streaming API for efficient parsing.
     */
    static List<OpenApiServiceRegistry.Route> parseOpenApiSpec(final String json, final String prefix) throws IOException {
        final List<OpenApiServiceRegistry.Route> routes = new ArrayList<>();
        final JsonFactory factory = new JsonFactory();

        try (final JsonParser parser = factory.createParser(json)) {
            // Navigate to "paths" object
            if (!advanceTo(parser, "paths")) {
                return routes;
            }

            // parser is now at START_OBJECT for "paths"
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() == JsonToken.FIELD_NAME) {
                    String pathPattern = parser.currentName();

                    // Apply prefix filter if specified
                    if (prefix != null && !pathPattern.startsWith(prefix)) {
                        parser.nextToken(); // skip value
                        parser.skipChildren();
                        continue;
                    }

                    parser.nextToken(); // START_OBJECT for this path
                    if (parser.currentToken() != JsonToken.START_OBJECT) {
                        continue;
                    }

                    // Parse methods under this path
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        if (parser.currentToken() == JsonToken.FIELD_NAME) {
                            final String method = parser.currentName().toUpperCase();
                            if (!Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD").contains(method)) {
                                parser.nextToken();
                                parser.skipChildren();
                                continue;
                            }

                            parser.nextToken(); // START_OBJECT for this operation
                            String operationId = null;
                            final Map<String, Object> spec = new HashMap<>();

                            // Find operationId in this operation object
                            int depth = 1;
                            while (depth > 0) {
                                final JsonToken token = parser.nextToken();
                                if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                                    depth++;
                                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                                    depth--;
                                } else if (token == JsonToken.FIELD_NAME && depth == 1) {
                                    if ("operationId".equals(parser.currentName())) {
                                        parser.nextToken();
                                        operationId = parser.getValueAsString();
                                    }
                                }
                            }

                            if (operationId != null) {
                                final Pattern regex = OpenApiServiceRegistry.compilePathPattern(pathPattern);
                                routes.add(new OpenApiServiceRegistry.Route(
                                        method, pathPattern, regex, operationId, spec));
                                LOG.debug("Parsed route: {} {} → {}", method, pathPattern, operationId);
                            }
                        }
                    }
                }
            }
        }

        return routes;
    }

    /**
     * Parse the "apis" array from a controller.json string.
     */
    static List<Map<String, String>> parseApisArray(final String json) throws IOException {
        final List<Map<String, String>> apis = new ArrayList<>();
        final JsonFactory factory = new JsonFactory();

        try (final JsonParser parser = factory.createParser(json)) {
            if (!advanceTo(parser, "apis")) {
                return apis;
            }

            // parser is now at START_ARRAY for "apis"
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    final Map<String, String> api = new HashMap<>();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        if (parser.currentToken() == JsonToken.FIELD_NAME) {
                            final String key = parser.currentName();
                            parser.nextToken();
                            api.put(key, parser.getValueAsString());
                        }
                    }
                    apis.add(api);
                }
            }
        }

        return apis;
    }

    /**
     * Advance the parser to the value of a top-level field with the given name.
     * Returns true if found, false if not.
     */
    private static boolean advanceTo(final JsonParser parser, final String fieldName) throws IOException {
        while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.FIELD_NAME && fieldName.equals(parser.currentName())) {
                parser.nextToken(); // advance to value
                return true;
            }
            if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
                if (parser.currentToken() == JsonToken.START_OBJECT && parser.currentName() != null && !fieldName.equals(parser.currentName())) {
                    parser.skipChildren();
                }
            }
        }
        return false;
    }

    private String readBinaryDocument(final DBBroker broker, final BinaryDocument document) throws IOException {
        try (final InputStream is = broker.getBinaryResource(document)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
