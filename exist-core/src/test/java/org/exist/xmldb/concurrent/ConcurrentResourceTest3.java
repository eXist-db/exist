/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb.concurrent;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.MultiResourcesAction;
import org.exist.xmldb.concurrent.action.XQueryAction;
import org.junit.Before;
import org.xmldb.api.base.Collection;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNotNull;

/**
 * @author wolf
 */
public class ConcurrentResourceTest3 extends ConcurrentTestBase {
	
	private static final String FILES_DIR = "/home/wolf/xml/movies";
	private static final String QUERY0 = "collection('" + XmldbURI.ROOT_COLLECTION + "')/movie";

	@Before
	public void setUp() throws Exception {
		final Collection c1 = DBUtils.addCollection(getTestCollection(), "C1-C2");
		assertNotNull(c1);
	}

	@Override
	public String getTestCollectionName() {
		return "C1";
	}

	@Override
	public List<Runner> getRunners() {
		return Arrays.asList(
				new Runner(new MultiResourcesAction(FILES_DIR, XmldbURI.LOCAL_DB + "/C1/C1-C2"), 1, 0, 0),
				new Runner(new XQueryAction(XmldbURI.LOCAL_DB + "/C1/C1-C2", "R1.xml", QUERY0), 1500, 200, 100)
		);
	}
}
