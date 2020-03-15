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
package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NamespaceUpdateTest {

	@ClassRule
	public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

	private final static String namespaces =
			"<test xmlns='http://www.foo.com'>"
					+ "<section>"
					+ "<title>Test Document</title>"
					+ "<c:comment xmlns:c='http://www.other.com'>This is my comment</c:comment>"
					+ "</section>"
					+ "</test>";

	private Collection testCollection;

	@Test
	public void updateAttribute() throws XMLDBException {
		XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
		String query =
				"declare namespace t='http://www.foo.com';\n" +
						"<test xmlns='http://www.foo.com'>\n" +
						"{\n" +
						"	update insert attribute { 'ID' } { 'myid' } into /t:test\n" +
						"}\n" +
						"</test>";
		service.query(query);

		query =
				"declare namespace t='http://www.foo.com';\n" +
						"/t:test/@ID/string(.)";
		ResourceSet result = service.query(query);
		assertEquals(1, result.getSize());
		assertEquals("myid", result.getResource(0).getContent().toString());
	}

	@Before
	public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
		// initialize driver
		final CollectionManagementService service =
				(CollectionManagementService) existEmbeddedServer.getRoot().getService(
						"CollectionManagementService",
						"1.0");
		testCollection = service.createCollection("test");
		assertNotNull(testCollection);

		final XMLResource doc =
				(XMLResource) testCollection.createResource("namespace-updates.xml", "XMLResource");
		doc.setContent(namespaces);
		testCollection.storeResource(doc);
	}

	@After
	public void tearDown() throws Exception {
		final CollectionManagementService service =
				(CollectionManagementService) existEmbeddedServer.getRoot().getService(
						"CollectionManagementService",
						"1.0");
		service.removeCollection("test");
		testCollection = null;
	}
}
