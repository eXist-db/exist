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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.StartupTrigger;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Startup trigger that scans {@code /db/apps/} for collections containing
 * {@code controller.json} or {@code api.json} files and registers their
 * OpenAPI routes with the {@link OpenApiServiceRegistry}.
 * <p>
 * Configure in conf.xml:
 * <pre>
 * &lt;trigger class="org.exist.http.openapi.OpenApiStartupTrigger"/&gt;
 * </pre>
 * </p>
 */
public class OpenApiStartupTrigger implements StartupTrigger {

    private static final Logger LOG = LogManager.getLogger(OpenApiStartupTrigger.class);

    @Override
    public void execute(final DBBroker sysBroker, final Txn transaction,
                        final Map<String, List<? extends Object>> params) {
        LOG.info("OpenAPI startup trigger: scanning /db/apps/ for controller.json files...");

        try {
            final XmldbURI appsUri = XmldbURI.create("/db/apps");
            final Collection appsCollection = sysBroker.getCollection(appsUri);
            if (appsCollection == null) {
                LOG.info("No /db/apps/ collection found, skipping OpenAPI scan");
                return;
            }

            int routeCount = 0;
            for (final Iterator<XmldbURI> it = appsCollection.collectionIterator(sysBroker); it.hasNext(); ) {
                final XmldbURI childName = it.next();
                final XmldbURI childUri = appsUri.append(childName);
                final Collection childColl = sysBroker.getCollection(childUri);
                if (childColl == null) {
                    continue;
                }

                // Check for controller.json
                final DocumentImpl controllerDoc = childColl.getDocument(sysBroker,
                        XmldbURI.create("controller.json"));
                if (controllerDoc != null && controllerDoc instanceof BinaryDocument) {
                    try {
                        final String json = readBinaryDocument(sysBroker, (BinaryDocument) controllerDoc);
                        final List<Map<String, String>> apis = OpenApiTrigger.parseApisArray(json);

                        final List<OpenApiServiceRegistry.Route> allRoutes = new ArrayList<>();
                        for (final Map<String, String> api : apis) {
                            final String specPath = api.get("spec");
                            if (specPath == null) continue;
                            final String prefix = api.get("prefix");

                            // Resolve spec relative to app collection
                            final XmldbURI specUri = childUri.append(specPath);
                            final DocumentImpl specDoc = sysBroker.getResource(specUri, org.exist.security.Permission.READ);
                            if (specDoc == null) {
                                LOG.warn("OpenAPI spec not found: {}", specUri);
                                continue;
                            }
                            final String specJson = readBinaryDocument(sysBroker, (BinaryDocument) specDoc);
                            allRoutes.addAll(OpenApiTrigger.parseOpenApiSpec(specJson, prefix));
                        }

                        if (!allRoutes.isEmpty()) {
                            OpenApiServiceRegistry.getInstance().registerRoutes(
                                    childUri.getCollectionPath(), allRoutes);
                            routeCount += allRoutes.size();
                        }
                    } catch (final Exception e) {
                        LOG.error("Failed to process controller.json in {}: {}",
                                childUri, e.getMessage(), e);
                    }
                    continue;
                }

                // Check for standalone api.json
                final DocumentImpl apiDoc = childColl.getDocument(sysBroker,
                        XmldbURI.create("api.json"));
                if (apiDoc != null && apiDoc instanceof BinaryDocument) {
                    try {
                        final String json = readBinaryDocument(sysBroker, (BinaryDocument) apiDoc);
                        final List<OpenApiServiceRegistry.Route> routes =
                                OpenApiTrigger.parseOpenApiSpec(json, null);
                        if (!routes.isEmpty()) {
                            OpenApiServiceRegistry.getInstance().registerRoutes(
                                    childUri.getCollectionPath(), routes);
                            routeCount += routes.size();
                        }
                    } catch (final Exception e) {
                        LOG.error("Failed to process api.json in {}: {}",
                                childUri, e.getMessage(), e);
                    }
                }
            }

            LOG.info("OpenAPI startup trigger: registered {} routes", routeCount);

        } catch (final Exception e) {
            LOG.error("OpenAPI startup trigger failed: {}", e.getMessage(), e);
        }
    }

    private String readBinaryDocument(final DBBroker broker, final BinaryDocument document)
            throws IOException {
        try (final InputStream is = broker.getBinaryResource(document)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
