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

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.ReplaceResourceAction;
import org.exist.xmldb.concurrent.action.RetrieveResourceAction;
import org.junit.Before;
import org.xmldb.api.base.Collection;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNotNull;

/**
 * Test concurrent access to resources.
 * 
 * @author wolf
 */
public class ConcurrentResourceTest extends ConcurrentTestBase {

	@Before
	public void setUp() throws Exception {
		final Collection c1 = DBUtils.addCollection(getTestCollection(), "C1-C2");
		assertNotNull(c1);
		DBUtils.addXMLResource(c1, "R1.xml", ReplaceResourceAction.XML);
	}

	@Override
	public String getTestCollectionName() {
		return "C1";
	}

	@Override
	public List<Runner> getRunners() {
		return Arrays.asList(
				new Runner(new ReplaceResourceAction(XmldbURI.LOCAL_DB + "/C1/C1-C2", "R1.xml"), 100, 0, 50),
				new Runner(new ReplaceResourceAction(XmldbURI.LOCAL_DB + "/C1/C1-C2", "R2.xml"), 100, 0, 50),
				new Runner(new RetrieveResourceAction(XmldbURI.LOCAL_DB + "/C1/C1-C2", "R1.xml"), 150, 200, 50)
		);
	}
}
