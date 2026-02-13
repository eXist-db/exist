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
import org.exist.xmldb.concurrent.action.MultiResourcesAction;
import org.exist.xmldb.concurrent.action.XQueryAction;
import org.junit.Before;
import org.xmldb.api.base.Collection;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNotNull;

/**
 * @author wolf
 */
public class ConcurrentResourceTest2 extends ConcurrentTestBase {
    
    private static final String QUERY0 =
        "declare default element namespace 'http://www.loc.gov/mods/v3';" +
        "collection(\"" + XmldbURI.ROOT_COLLECTION + "\")//mods[contains(lower-case(titleInfo/title), 'germany')]";
    
    private static final String QUERY1 =
        "declare default element namespace 'http://www.loc.gov/mods/v3';" +
        "<result>{for $t in distinct-values(collection(\"" + XmldbURI.ROOT_COLLECTION + "\")//mods/subject/topic) order by $t return <topic>{$t}</topic>}</result>";

    @Before
    public void setUp() throws Exception {
        Collection c1 = DBUtils.addCollection(getTestCollection(), "C1-C2");
        assertNotNull(c1);
    }

    @Override
    public String getTestCollectionName() {
        return "C1";
    }

    private String getModsPath() {
        try {
            final URI uri = ConcurrentResourceTest2.class.getResource("/samples/mods/mod1.xml").toURI();
            final Path dir = Paths.get(uri).getParent();
            return dir.toAbsolutePath().toString();
        } catch (final URISyntaxException e) {
            throw new RuntimeException("Cannot resolve samples/mods path", e);
        }
    }

    @Override
    public List<Runner> getRunners() {
        final String modsPath = getModsPath();
        return Arrays.asList(
                new Runner(new MultiResourcesAction(modsPath, XmldbURI.LOCAL_DB + "/C1/C1-C2"), 200, 0, 50),
                new Runner(new MultiResourcesAction(modsPath, XmldbURI.LOCAL_DB + "/C1/C1-C2"), 200, 0, 50),
                new Runner(new XQueryAction(XmldbURI.LOCAL_DB + "/C1/C1-C2", "R1.xml", QUERY0), 200, 200, 100),
                new Runner(new XQueryAction(XmldbURI.LOCAL_DB + "/C1/C1-C2", "R1.xml", QUERY1), 200, 300, 100)
                //new Runner(new XQueryAction(getUri + "/C1/C1-C2", "R1.xml", QUERY0), 200, 400, 500),
                //new Runner(new XQueryAction(getUri + "/C1/C1-C2", "R1.xml", QUERY1), 200, 500, 500)
        );
    }
}
