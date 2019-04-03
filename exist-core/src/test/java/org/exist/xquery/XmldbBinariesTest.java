/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery;

import org.exist.test.ExistWebServer;
import org.exist.xmldb.XmldbURI;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static org.exist.TestUtils.ADMIN_DB_PWD;
import static org.exist.TestUtils.ADMIN_DB_USER;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@RunWith(Parameterized.class)
public class XmldbBinariesTest extends AbstractBinariesTest<ResourceSet, Resource, XMLDBException> {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);
    private static final String PORT_PLACEHOLDER = "${PORT}";

    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
//                { "local", "xmldb:exist://" },
                { "remote", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc" }
        });
    }

    @Parameterized.Parameter
    public String apiName;

    @Parameterized.Parameter(value = 1)
    public String baseUri;

    private final String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    @Override
    protected void storeBinaryFile(final XmldbURI filePath, byte[] content) throws Exception {
        Collection colRoot = null;
        try {
            colRoot = DatabaseManager.getCollection(getBaseUri() + "/db", ADMIN_DB_USER, ADMIN_DB_PWD);

            final XmldbURI collectionNames[] = filePath.removeLastSegment().getPathSegments();

            final Deque<Collection> cols = new ArrayDeque<>();
            try {
                Collection current = colRoot;
                for (int i = 1; i < collectionNames.length; i++) {
                    final Collection child = getOrCreateCollection(current, collectionNames[i].toString());
                    cols.push(child);
                    current = child;
                }

                final String fileName = filePath.lastSegment().toString();
                final Resource resource = current.createResource(fileName, BinaryResource.RESOURCE_TYPE);
                resource.setContent(content);
                current.storeResource(resource);

            } finally {
                while(!cols.isEmpty()) {
                    try {
                        cols.pop().close();
                    } catch(XMLDBException e) {

                    }
                }
            }
        } finally {
            if(colRoot != null) {
                colRoot.close();
            }
        }
    }

    private Collection getOrCreateCollection(final Collection parent, final String childName) throws XMLDBException {
        Collection child = parent.getChildCollection(childName);
        if(child == null) {
            final CollectionManagementService cms = (CollectionManagementService) parent.getService("CollectionManagementService", "1.0");
            child = cms.createCollection(childName);
        }
        return child;
    }

    @Override
    protected void removeCollection(final XmldbURI collectionUri) throws Exception {
        Collection colRoot = null;
        try {
            colRoot = DatabaseManager.getCollection(getBaseUri() + "/db", ADMIN_DB_USER, ADMIN_DB_PWD);

            final Collection colTest = colRoot.getChildCollection("test");
            try {
                final CollectionManagementService cms = (CollectionManagementService) colTest.getService("CollectionManagementService", "1.0");

                final String testCollectionName = collectionUri.lastSegment().toString();
                cms.removeCollection(testCollectionName);
            } finally {
                if(colTest != null) {
                    colTest.close();
                }
            }
        } finally {
            if(colRoot != null) {
                colRoot.close();
            }
        }
    }

    @Override
    protected QueryResultAccessor<ResourceSet, XMLDBException> executeXQuery(final String query) {
        return consumer -> {
            Collection colRoot = null;
            try {
                colRoot = DatabaseManager.getCollection(getBaseUri() + "/db", ADMIN_DB_USER, ADMIN_DB_PWD);
                final XQueryService xqueryService = (XQueryService)colRoot.getService("XQueryService", "1.0");

                final CompiledExpression compiledExpression = xqueryService.compile(query);
                final ResourceSet results = xqueryService.execute(compiledExpression);


                try {
//                    compiledExpression.reset();  // shows the ordering issue with binary values (see comment below)

                    consumer.accept(results);
                } finally {
                    //the following calls cause the streams of any binary result values to be closed, so if we did so before we are finished with the results, serialization would fail.
                    results.clear();
                    compiledExpression.reset();
                }
            } finally {
                colRoot.close();
            }
        };
    }

    @Override
    protected long size(final ResourceSet results) throws XMLDBException {
        return results.getSize();
    }

    @Override
    protected Resource item(final ResourceSet results, final int index) throws XMLDBException {
        return results.getResource(index);
    }

    @Override
    protected boolean isBinaryType(final Resource item) throws XMLDBException {
        return BinaryResource.RESOURCE_TYPE.equals(item.getResourceType());
    }

    @Override
    protected boolean isBooleanType(final Resource item) throws XMLDBException {
        final String value = item.getContent().toString();
        return "true".equals(value) || "false".equals(value);
    }

    @Override
    protected byte[] getBytes(final Resource item) throws XMLDBException {
        return (byte[])item.getContent();
    }

    @Override
    protected boolean getBoolean(final Resource item) throws XMLDBException {
        return Boolean.parseBoolean(item.getContent().toString());
    }
}
