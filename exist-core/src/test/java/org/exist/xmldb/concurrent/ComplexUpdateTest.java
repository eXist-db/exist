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
import org.exist.xmldb.concurrent.action.ComplexUpdateAction;
import org.junit.Before;
import org.xmldb.api.modules.XMLResource;

import java.util.Arrays;
import java.util.List;

/**
 * @author wolf
 */
public class ComplexUpdateTest extends ConcurrentTestBase {
	
	private static final String XML =
		"<TEST><USER-SESSION-DATA version=\"0\"/></TEST>";
	
	@Before
	public void setUp() throws Exception {
        final XMLResource res = getTestCollection().createResource("R01.xml", XMLResource.class);
        res.setContent(XML);
        getTestCollection().storeResource(res);
        getTestCollection().close();
	}

    @Override
    public String getTestCollectionName() {
        return "complex";
    }

    @Override
    public List<Runner> getRunners() {
        return List.of(
                new Runner(new ComplexUpdateAction(XmldbURI.LOCAL_DB + "/complex", "R01.xml", 200), 1, 0, 0)
        );
    }
}
