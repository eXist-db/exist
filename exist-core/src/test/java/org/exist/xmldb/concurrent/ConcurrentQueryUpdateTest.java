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
package org.exist.xmldb.concurrent;

import java.util.Arrays;
import java.util.List;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.XQueryUpdateAction;
import org.junit.Before;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

public class ConcurrentQueryUpdateTest extends ConcurrentTestBase {

	@Before
	public void setUp() throws Exception {
		final Collection col = getTestCollection();
		final XMLResource res = (XMLResource) col.createResource("testappend.xml", "XMLResource");
		res.setContent("<root><node id=\"1\"/></root>");
		col.storeResource(res);
	}

	@Override
	public void assertAdditional() throws XMLDBException {
		final Collection col = getTestCollection();
		final XQueryService service = (XQueryService) col.getService("XQueryService", "1.0");
		final ResourceSet result = service.query("distinct-values(//node/@id)");
		assertEquals(41, result.getSize());
		for (int i = 0; i < result.getSize(); i++) {
			final XMLResource next = (XMLResource) result.getResource((long)i);
			next.getContent();
		}
	}

	@Override
	public String getTestCollectionName() {
		return "C1";
	}

	@Override
	public List<Runner> getRunners() {
		return Arrays.asList(
				new Runner(new XQueryUpdateAction(XmldbURI.LOCAL_DB + "/C1", "testappend.xml"), 20, 0, 0),
				new Runner(new XQueryUpdateAction(XmldbURI.LOCAL_DB + "/C1", "testappend.xml"), 20, 0, 0)
		);
	}
}
