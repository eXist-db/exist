package org.exist.xquery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class SeqOpTest {
	private static XPathQueryService query;
	private static Collection c;

	@Test
	public void testReverseEmpty() throws XMLDBException {
		assertSeq(new String[0], "reverse(())");
	}

    @Test
	public void testReverseAtomic1() throws XMLDBException {
		assertSeq(new String[]{"a"}, "reverse(('a'))");
	}

    @Test
	public void testReverseAtomic2() throws XMLDBException {
		assertSeq(new String[]{"b", "a"}, "reverse(('a', 'b'))");
	}

    @Test
	public void testReverseNodes1() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<a/>"}, "reverse(//a)");
	}

    @Test
	public void testReverseNodes2() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<b/>", "<a/>"}, "reverse(/top/*)");
	}

    @Test
	public void testReverseMixed() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"c", "<b/>", "<a/>"}, "reverse((/top/*, 'c'))");
	}

    @Test
	public void testRemoveEmpty1() throws XMLDBException {
		assertSeq(new String[0], "remove((), 1)");
	}

    @Test
	public void testRemoveEmpty2() throws XMLDBException {
		assertSeq(new String[0], "remove((), 0)");
	}

    @Test
	public void testRemoveEmpty3() throws XMLDBException {
		assertSeq(new String[0], "remove((), 42)");
	}

    @Test
	public void testRemoveOutOfBounds1() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b'), 0)");
	}

    @Test
	public void testRemoveOutOfBounds2() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b'), 3)");
	}

    @Test
	public void testRemoveOutOfBounds3() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b'), -1)");
	}

    @Test
	public void testRemoveAtomic1() throws XMLDBException {
		assertSeq(new String[]{"b", "c"}, "remove(('a', 'b', 'c'), 1)");
	}

    @Test
	public void testRemoveAtomic2() throws XMLDBException {
		assertSeq(new String[]{"a", "c"}, "remove(('a', 'b', 'c'), 2)");
	}

    @Test
	public void testRemoveAtomic3() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b', 'c'), 3)");
	}

    @Test
	public void testRemoveMixed1() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<b/>", "a", "b", "c"}, "remove((/top/*, 'a', 'b', 'c'), 1)");
	}

    @Test
	public void testRemoveMixed2() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<a/>", "a", "b", "c"}, "remove((/top/*, 'a', 'b', 'c'), 2)");
	}

    @Test
	public void testRemoveMixed3() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<a/>", "<b/>", "b", "c"}, "remove((/top/*, 'a', 'b', 'c'), 3)");
	}

    @Test
	public void testRemoveNodes1() throws XMLDBException {
		createDocument("foo", "<top><a/><b/><c/></top>");
		assertSeq(new String[]{"<b/>", "<c/>"}, "remove(/top/*, 1)");
	}

    @Test
	public void testRemoveNodes2() throws XMLDBException {
		createDocument("foo", "<top><a/><b/><c/></top>");
		assertSeq(new String[]{"<a/>", "<c/>"}, "remove(/top/*, 2)");
	}

    @Test
	public void testRemoveNodes3() throws XMLDBException {
		createDocument("foo", "<top><a/><b/><c/></top>");
		assertSeq(new String[]{"<a/>", "<b/>"}, "remove(/top/*, 3)");
	}

    @Test
	public void testInsertEmpty1() throws XMLDBException {
		assertSeq(new String[0], "insert-before((), 1, ())");
	}

    @Test
	public void testInsertEmpty2() throws XMLDBException {
		assertSeq(new String[]{"a"}, "insert-before((), 1, ('a'))");
	}

    @Test
	public void testInsertEmpty3() throws XMLDBException {
		assertSeq(new String[]{"a"}, "insert-before(('a'), 1, ())");
	}

    @Test
	public void testInsertOutOfBounds1() throws XMLDBException {
		assertSeq(new String[]{"c", "d", "a", "b"}, "insert-before(('a', 'b'), 0, ('c', 'd'))");
	}

    @Test
	public void testInsertOutOfBounds2() throws XMLDBException {
		assertSeq(new String[]{"a", "b", "c", "d"}, "insert-before(('a', 'b'), 3, ('c', 'd'))");
	}

    @Test
	public void testInsertOutOfBounds3() throws XMLDBException {
		assertSeq(new String[]{"a", "b", "c", "d"}, "insert-before(('a', 'b'), 4, ('c', 'd'))");
	}

    @Test
	public void testInsertAtomic1() throws XMLDBException {
		assertSeq(new String[]{"a", "c", "d", "b"}, "insert-before(('a', 'b'), 2, ('c', 'd'))");
	}

    @Test
	public void testInsertAtomic2() throws XMLDBException {
		assertSeq(new String[]{"c", "d", "a", "b"}, "insert-before(('a', 'b'), 1, ('c', 'd'))");
	}

    @Test
	public void testInsertAtomic3() throws XMLDBException {
		assertSeq(new String[]{"a", "a", "b", "b"}, "insert-before(('a', 'b'), 2, ('a', 'b'))");
	}

    @Test
	public void testInsertNodes1() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<c/>", "<d/>", "<b/>"}, "insert-before(/top/x/*, 2, /top/y/*)");
	}

    @Test
	public void testInsertNodes2() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<c/>", "<d/>", "<a/>", "<b/>"}, "insert-before(/top/x/*, 1, /top/y/*)");
	}

    @Test
	public void testInsertNodes3() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<b/>", "<c/>", "<d/>"}, "insert-before(/top/x/*, 3, /top/y/*)");
	}

	// TODO: currently fails because duplicate nodes are removed
    @Test
	public void testInsertNodes4() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<a/>", "<b/>", "<b/>"}, "insert-before(/top/x/*, 2, /top/x/*)");
	}

    @Test
	public void testInsertMixed1() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "c", "<b/>"}, "insert-before(/top/x/*, 2, ('c'))");
	}

	// TODO: currently fails because duplicate nodes are removed
    @Test
	public void testInsertMixed2() throws XMLDBException {
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
		XMLResource res = (XMLResource) c.createResource(name, XMLResource.RESOURCE_TYPE);
		res.setContent(content);
		c.storeResource(res);
		return res;
	}

	@ClassRule
	public static ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

	@BeforeClass
	public static void setupTestCollection() throws XMLDBException {
		final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
		final CollectionManagementService rootcms = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
		c = root.getChildCollection("test");
		if (c != null) {
			rootcms.removeCollection("test");
		}
		c = rootcms.createCollection("test");
		assertNotNull(c);
		query = (XPathQueryService) c.getService("XPathQueryService", "1.0");
	}

	@AfterClass
	public static void tearDown() throws XMLDBException {
		if (c != null) {
			final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
			final CollectionManagementService rootcms = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
			rootcms.removeCollection("test");
			query = null;
			c = null;
		}
	}
	
}
