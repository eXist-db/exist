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
package org.exist.xmldb;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.concurrent.DBUtils;
import org.junit.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Due to limitation of ExistXmldbEmbeddedServer we need to split this test to two files.
 * It's not possible to have two instances of ExistXmldbEmbeddedServer at the same time.
 */
public class DbStoreTest2 {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServerWithAnyURI = new ExistXmldbEmbeddedServer(false, true,
            true, getConfig());

    private static final int BUFFER_SIZE = 1024 * 1024 * 4; // 4MiB buffer
    private static final long FILE_SIZE = 3l * 1024l * 1024l * 1024l; //3GiB file.
    private static final String TEST_COLLECTION = "testAnyUri2";

    private static Path largeFileLocation = null;
    private static Path jettyRootDir = null;
    private static Path pictureLocation = null;

    private static Server jettyServer = null;
    private static int jettyPort = 30350;

    private final static Path getConfig() {
        try {
            final URL path = DbStoreTest.class.getClassLoader().getResource("org/exist/xmldb/any-uri-enabled.xml");
            return Paths.get(path.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("unable to parse URI for resource", e);
        }
    }

    @BeforeClass
    public static void beforeClass() throws Exception {

        jettyPort += new Random().nextInt(15000);

        jettyRootDir = Files.createTempDirectory("dbstore2-test");
        largeFileLocation = jettyRootDir.resolve("large-file.bin");
        pictureLocation = jettyRootDir.resolve("picture.jpg");

        System.out.println("generating temp file : " + largeFileLocation.toAbsolutePath().toString());
        System.out.println("generating temp file : " + largeFileLocation.getFileName().toString());

        try (FileOutputStream fOut = new FileOutputStream(pictureLocation.toFile(), true)) {
            byte buff[] = new byte[BUFFER_SIZE];
            fOut.write(buff);
        }

        jettyServer = new Server(jettyPort);
        ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setDirectoriesListed(true);
        String dir = jettyRootDir.toAbsolutePath().toFile().getCanonicalPath();
        resource_handler.setResourceBase(dir);

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{resource_handler, new DefaultHandler()});

        jettyServer.setHandler(handlers);
        jettyServer.start();

    }

    @AfterClass
    public static void afterClass() throws Exception {
        jettyServer.stop();
        FileUtils.deleteDirectory(jettyRootDir.toFile());
    }

    @Test
    public final void testWithAnyUriEnabled() throws XMLDBException {
        final Collection rootCol = existEmbeddedServerWithAnyURI.getRoot();
        Collection testCol = rootCol.getChildCollection(TEST_COLLECTION);
        if (testCol == null) {
            testCol = DBUtils.addCollection(rootCol, TEST_COLLECTION);
            assertNotNull(testCol);
        }

        final XPathQueryService xpqs =
                (XPathQueryService) testCol.getService("XPathQueryService", "1.0");
        final ResourceSet rs =
                xpqs.query(
                        "xmldb:store(\n" +
                                "        '/db',\n" +
                                "        'image.jpg',\n" +
                                "        xs:anyURI('http://localhost:" + jettyPort + "/picture.jpg'),\n" +
                                "        'image/png'\n" +
                                "    )");
        assertNotNull(rs);

        assertTrue(true);
    }

    @Test
    public final void testLargeFileStore() throws XMLDBException, IOException {
        byte buff[] = new byte[BUFFER_SIZE];
        try (FileOutputStream fOut = new FileOutputStream(largeFileLocation.toFile(), true)) {
            for (long written = 0; written < FILE_SIZE; written += BUFFER_SIZE) {
                fOut.write(buff);
            }
            fOut.flush();
        }

        final Collection rootCol = existEmbeddedServerWithAnyURI.getRoot();
        Collection testCol = rootCol.getChildCollection(TEST_COLLECTION);
        if (testCol == null) {
            testCol = DBUtils.addCollection(rootCol, TEST_COLLECTION);
            assertNotNull(testCol);
        }

        final XPathQueryService xpqs =
                (XPathQueryService) testCol.getService("XPathQueryService", "1.0");
        final ResourceSet rs =
                xpqs.query(
                        "xmldb:store(\n" +
                                "        '/db',\n" +
                                "        'image.jpg',\n" +
                                "        xs:anyURI('http://localhost:" + jettyPort + "/large-file.bin'),\n" +
                                "        'image/png'\n" +
                                "    )");
        assertNotNull(rs);

        assertTrue(true);
    }
}