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

import org.exist.collections.CollectionConfiguration;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.ValueAppendAction;
import org.junit.Before;

import java.util.Arrays;
import java.util.List;

/**
 * @author wolf
 */
public class ValueIndexUpdateTest extends ConcurrentTestBase {
    
    private static final String XCONF =
        "<exist:collection xmlns:exist=\"http://exist-db.org/collection-config/1.0\">" +
	        "<exist:index doctype=\"items\" xmlns:x=\"http://www.foo.com\">" +
		        "<exist:create path=\"//item/@id\" type=\"xs:integer\"/>" +
		        "<exist:create path=\"//item/name\" type=\"xs:string\"/>" +
		        "<exist:create path=\"//item/value\" type=\"xs:double\"/>" +
	        "</exist:index>" +
        "</exist:collection>";

	@Before
    public void setUp() throws Exception {
		DBUtils.addXMLResource(getTestCollection(), CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE, XCONF);
		DBUtils.addXMLResource(getTestCollection(), "R1.xml", "<items/>");
	}

	@Override
	public String getTestCollectionName() {
		return "C1";
	}

	@Override
	public List<Runner> getRunners() {
		return List.of(
                new Runner(new ValueAppendAction(XmldbURI.LOCAL_DB + "/C1", "R1.xml"), 50, 0, 100)
        );
	}
}
