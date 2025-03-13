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
package org.exist.xmlrpc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.TestDataGenerator;
import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.junit.*;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class QuerySessionTest {

    private static final Logger LOG = LogManager.getLogger(QuerySessionTest.class);

    @ClassRule
    public final static ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private final static String generateXQ =
            "declare function local:random-sequence($length as xs:integer, $G as map(xs:string, item())) {\n"
            + "  if ($length eq 0)\n"
            + "  then ()\n"
            + "  else ($G?number, local:random-sequence($length - 1, $G?next()))\n"
            + "};\n"
            + "let $rnd := fn:random-number-generator() return"
            + "<book id=\"{$filename}\" n=\"{$count}\">"
			+ "   <chapter xml:id=\"chapter{$count}\">"
			+ "       <title>{local:random-sequence(7, $rnd)}</title>"
			+ "       {"
			+ "           for $section in 1 to 8 return"
			+ "               <section id=\"sect{$section}\">"
			+ "                   <title>{local:random-sequence(7, $rnd)}</title>"
			+ "                   {"
			+ "                       for $para in 1 to 10 return"
			+ "                           <para>{local:random-sequence(120, $rnd)}</para>"
			+ "                   }"
			+ "               </section>"
			+ "       }"
			+ "   </chapter>"
			+ "</book>";

    private final static String QUERY =
            "declare variable $n external;" +
            "//chapter[@xml:id eq $n]";

    private static String getBaseUri() {
        return "xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc";
    }

    private final static int N_THREADS = 10;

    private final static int DOC_COUNT = 100;

    private Random random = new Random();

    @Test (expected=XMLDBException.class)
    public void manualRelease() throws XMLDBException {
        Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/rpctest", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        XQueryService service = test.getService(XQueryService.class);
        ResourceSet result = service.query("//chapter[@xml:id eq 'chapter1']");
        assertEquals(1, result.getSize());

        // clear should release the query result on the server
        result.clear();

        // the result has been cleared already. we should get an exception here
        Resource members = result.getMembersAsResource();
        members.getContent();
    }

    @Test
    public void runTasks() {
        ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
        for (int i = 0; i < 100; i++) {
            executor.submit(new QueryTask(QUERY));
        }

        executor.shutdown();
		boolean terminated = false;
		try {
			terminated = executor.awaitTermination(60 * 60, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
		    Thread.currentThread().interrupt();
		}
		Assert.assertTrue(terminated);
    }

    private class QueryTask implements Runnable {

        private String query;

        private QueryTask(String query) {
            this.query = query;
        }

        @Override
        public void run() {
            try {
                final Collection test = DatabaseManager.getCollection(getBaseUri() + "/db/rpctest", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
                final XQueryService service = test.getService(XQueryService.class);
                final int n = random.nextInt(DOC_COUNT) + 1;
                service.declareVariable("n", "chapter" + n);
                final ResourceSet result = service.query(query);
                assertEquals(1, result.getSize());
            } catch (final XMLDBException e) {
                LOG.error(e.getMessage(), e);
                fail(e.getMessage());
            }
        }
    }

	@BeforeClass
    public static void startServer() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException, SAXException {
        // initialize XML:DB driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        DatabaseManager.registerDatabase(database);

        Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        
        CollectionManagementService mgmt =
                root.getService(CollectionManagementService.class);
        Collection test = mgmt.createCollection("rpctest");

        final TestDataGenerator generator = new TestDataGenerator("xdb", DOC_COUNT);
        final Path[] files = generator.generate(test, generateXQ);
        for (Path file : files) {
            Resource resource = test.createResource(file.getFileName().toString(), XMLResource.class);
            resource.setContent(file.toFile());
            test.storeResource(resource);
        }
        generator.releaseAll();
    }

    @AfterClass
    public static void stopServer() throws XMLDBException {
        Collection root = DatabaseManager.getCollection(getBaseUri() + "/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        CollectionManagementService mgmt =
                root.getService(CollectionManagementService.class);
        mgmt.removeCollection("rpctest");

        Collection config = DatabaseManager.getCollection(getBaseUri() + "/db/system/config/db", "admin", "");
        mgmt =
                config.getService(CollectionManagementService.class);
        mgmt.removeCollection("rpctest");
    }
}
