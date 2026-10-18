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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
public class SeqOpTest {
	private static XPathQueryService query;
	private static Collection c;

    @org.junit.jupiter.api.Test
    void testReverseEmpty() throws XMLDBException {
		assertSeq(new String[0], "reverse(())");
	}

    @org.junit.jupiter.api.Test
    void testReverseAtomic1() throws XMLDBException {
		assertSeq(new String[]{"a"}, "reverse(('a'))");
	}

    @org.junit.jupiter.api.Test
    void testReverseAtomic2() throws XMLDBException {
		assertSeq(new String[]{"b", "a"}, "reverse(('a', 'b'))");
	}

    @org.junit.jupiter.api.Test
    void testReverseNodes1() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<a/>"}, "reverse(//a)");
	}

    @org.junit.jupiter.api.Test
    void testReverseNodes2() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<b/>", "<a/>"}, "reverse(/top/*)");
	}

    @org.junit.jupiter.api.Test
    void testReverseMixed() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"c", "<b/>", "<a/>"}, "reverse((/top/*, 'c'))");
	}

    @org.junit.jupiter.api.Test
    void testRemoveEmpty1() throws XMLDBException {
		assertSeq(new String[0], "remove((), 1)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveEmpty2() throws XMLDBException {
		assertSeq(new String[0], "remove((), 0)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveEmpty3() throws XMLDBException {
		assertSeq(new String[0], "remove((), 42)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveOutOfBounds1() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b'), 0)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveOutOfBounds2() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b'), 3)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveOutOfBounds3() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b'), -1)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveAtomic1() throws XMLDBException {
		assertSeq(new String[]{"b", "c"}, "remove(('a', 'b', 'c'), 1)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveAtomic2() throws XMLDBException {
		assertSeq(new String[]{"a", "c"}, "remove(('a', 'b', 'c'), 2)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveAtomic3() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b', 'c'), 3)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveMixed1() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<b/>", "a", "b", "c"}, "remove((/top/*, 'a', 'b', 'c'), 1)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveMixed2() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<a/>", "a", "b", "c"}, "remove((/top/*, 'a', 'b', 'c'), 2)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveMixed3() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<a/>", "<b/>", "b", "c"}, "remove((/top/*, 'a', 'b', 'c'), 3)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveNodes1() throws XMLDBException {
		createDocument("foo", "<top><a/><b/><c/></top>");
		assertSeq(new String[]{"<b/>", "<c/>"}, "remove(/top/*, 1)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveNodes2() throws XMLDBException {
		createDocument("foo", "<top><a/><b/><c/></top>");
		assertSeq(new String[]{"<a/>", "<c/>"}, "remove(/top/*, 2)");
	}

    @org.junit.jupiter.api.Test
    void testRemoveNodes3() throws XMLDBException {
		createDocument("foo", "<top><a/><b/><c/></top>");
		assertSeq(new String[]{"<a/>", "<b/>"}, "remove(/top/*, 3)");
	}

    @org.junit.jupiter.api.Test
    void testInsertEmpty1() throws XMLDBException {
		assertSeq(new String[0], "insert-before((), 1, ())");
	}

    @org.junit.jupiter.api.Test
    void testInsertEmpty2() throws XMLDBException {
		assertSeq(new String[]{"a"}, "insert-before((), 1, ('a'))");
	}

    @org.junit.jupiter.api.Test
    void testInsertEmpty3() throws XMLDBException {
		assertSeq(new String[]{"a"}, "insert-before(('a'), 1, ())");
	}

    @org.junit.jupiter.api.Test
    void testInsertOutOfBounds1() throws XMLDBException {
		assertSeq(new String[]{"c", "d", "a", "b"}, "insert-before(('a', 'b'), 0, ('c', 'd'))");
	}

    @org.junit.jupiter.api.Test
    void testInsertOutOfBounds2() throws XMLDBException {
		assertSeq(new String[]{"a", "b", "c", "d"}, "insert-before(('a', 'b'), 3, ('c', 'd'))");
	}

    @org.junit.jupiter.api.Test
    void testInsertOutOfBounds3() throws XMLDBException {
		assertSeq(new String[]{"a", "b", "c", "d"}, "insert-before(('a', 'b'), 4, ('c', 'd'))");
	}

    @org.junit.jupiter.api.Test
    void testInsertAtomic1() throws XMLDBException {
		assertSeq(new String[]{"a", "c", "d", "b"}, "insert-before(('a', 'b'), 2, ('c', 'd'))");
	}

    @org.junit.jupiter.api.Test
    void testInsertAtomic2() throws XMLDBException {
		assertSeq(new String[]{"c", "d", "a", "b"}, "insert-before(('a', 'b'), 1, ('c', 'd'))");
	}

    @org.junit.jupiter.api.Test
    void testInsertAtomic3() throws XMLDBException {
		assertSeq(new String[]{"a", "a", "b", "b"}, "insert-before(('a', 'b'), 2, ('a', 'b'))");
	}

    @org.junit.jupiter.api.Test
    void testInsertNodes1() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<c/>", "<d/>", "<b/>"}, "insert-before(/top/x/*, 2, /top/y/*)");
	}

    @org.junit.jupiter.api.Test
    void testInsertNodes2() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<c/>", "<d/>", "<a/>", "<b/>"}, "insert-before(/top/x/*, 1, /top/y/*)");
	}

    @org.junit.jupiter.api.Test
    void testInsertNodes3() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<b/>", "<c/>", "<d/>"}, "insert-before(/top/x/*, 3, /top/y/*)");
	}

    // TODO: currently fails because duplicate nodes are removed
    @org.junit.jupiter.api.Test
    void testInsertNodes4() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<a/>", "<b/>", "<b/>"}, "insert-before(/top/x/*, 2, /top/x/*)");
	}

    @org.junit.jupiter.api.Test
    void testInsertMixed1() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "c", "<b/>"}, "insert-before(/top/x/*, 2, ('c'))");
	}

    // TODO: currently fails because duplicate nodes are removed
    @org.junit.jupiter.api.Test
    void testInsertMixed2() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<a/>", "<b/>", "<b/>", "c"}, "insert-before((/top/x/*, 'c'), 2, /top/x/*)");
	}

	private void assertSeq(String[] expected, String q) throws XMLDBException {
		ResourceSet rs = query.query(q);
		assertEquals(expected.length, rs.getSize());
		List<String> a = Arrays.asList(expected);
		List<Object> r = new ArrayList<>((int) rs.getSize());
		for (int i = 0; i < rs.getSize(); i++) {
            r.add(rs.getResource(i).getContent());
        }
		if (!a.equals(r)) {
            fail("expected " + a + ", got " + r);
        }
	}
	
	private XMLResource createDocument(String name, String content) throws XMLDBException {
		XMLResource res = c.createResource(name, XMLResource.class);
		res.setContent(content);
		c.storeResource(res);
		return res;
	}

	@RegisterExtension
	public static ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @BeforeAll
    static void setupTestCollection() throws XMLDBException {
		final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
		final CollectionManagementService rootcms = root.getService(CollectionManagementService.class);
		c = root.getChildCollection("test");
		if (c != null) {
			rootcms.removeCollection("test");
		}
		c = rootcms.createCollection("test");
		assertNotNull(c);
		query = c.getService(XPathQueryService.class);
	}

    @AfterAll
    static void tearDown() throws XMLDBException {
		if (c != null) {
			final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
			final CollectionManagementService rootcms = root.getService(CollectionManagementService.class);
			rootcms.removeCollection("test");
			query = null;
			c = null;
		}
	}
	
}
