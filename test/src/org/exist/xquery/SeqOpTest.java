package org.exist.xquery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;


public class SeqOpTest extends TestCase {

	private final static String URI = XmldbURI.LOCAL_DB;
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

	private XPathQueryService query;
	private Collection c;
	
	public static void main(String[] args) {
		TestRunner.run(SeqOpTest.class);
	}
	
	public SeqOpTest(String name) {
		super(name);
	}
	
	public void testReverseEmpty() throws XMLDBException {
		assertSeq(new String[0], "reverse(())");
	}
	
	public void testReverseAtomic1() throws XMLDBException {
		assertSeq(new String[]{"a"}, "reverse(('a'))");
	}
	
	public void testReverseAtomic2() throws XMLDBException {
		assertSeq(new String[]{"b", "a"}, "reverse(('a', 'b'))");
	}
	
	public void testReverseNodes1() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<a/>"}, "reverse(//a)");
	}
	
	public void testReverseNodes2() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<b/>", "<a/>"}, "reverse(/top/*)");
	}
	
	public void testReverseMixed() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"c", "<b/>", "<a/>"}, "reverse((/top/*, 'c'))");
	}
	
	public void testRemoveEmpty1() throws XMLDBException {
		assertSeq(new String[0], "remove((), 1)");
	}
	
	public void testRemoveEmpty2() throws XMLDBException {
		assertSeq(new String[0], "remove((), 0)");
	}
	
	public void testRemoveEmpty3() throws XMLDBException {
		assertSeq(new String[0], "remove((), 42)");
	}
	
	public void testRemoveOutOfBounds1() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b'), 0)");
	}
	
	public void testRemoveOutOfBounds2() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b'), 3)");
	}
	
	public void testRemoveOutOfBounds3() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b'), -1)");
	}
	
	public void testRemoveAtomic1() throws XMLDBException {
		assertSeq(new String[]{"b", "c"}, "remove(('a', 'b', 'c'), 1)");
	}
	
	public void testRemoveAtomic2() throws XMLDBException {
		assertSeq(new String[]{"a", "c"}, "remove(('a', 'b', 'c'), 2)");
	}
	
	public void testRemoveAtomic3() throws XMLDBException {
		assertSeq(new String[]{"a", "b"}, "remove(('a', 'b', 'c'), 3)");
	}

	public void testRemoveMixed1() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<b/>", "a", "b", "c"}, "remove((/top/*, 'a', 'b', 'c'), 1)");
	}
	
	public void testRemoveMixed2() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<a/>", "a", "b", "c"}, "remove((/top/*, 'a', 'b', 'c'), 2)");
	}
	
	public void testRemoveMixed3() throws XMLDBException {
		createDocument("foo", "<top><a/><b/></top>");
		assertSeq(new String[]{"<a/>", "<b/>", "b", "c"}, "remove((/top/*, 'a', 'b', 'c'), 3)");
	}
	
	public void testRemoveNodes1() throws XMLDBException {
		createDocument("foo", "<top><a/><b/><c/></top>");
		assertSeq(new String[]{"<b/>", "<c/>"}, "remove(/top/*, 1)");
	}
	
	public void testRemoveNodes2() throws XMLDBException {
		createDocument("foo", "<top><a/><b/><c/></top>");
		assertSeq(new String[]{"<a/>", "<c/>"}, "remove(/top/*, 2)");
	}
	
	public void testRemoveNodes3() throws XMLDBException {
		createDocument("foo", "<top><a/><b/><c/></top>");
		assertSeq(new String[]{"<a/>", "<b/>"}, "remove(/top/*, 3)");
	}
	
	public void testInsertEmpty1() throws XMLDBException {
		assertSeq(new String[0], "insert-before((), 1, ())");
	}
	
	public void testInsertEmpty2() throws XMLDBException {
		assertSeq(new String[]{"a"}, "insert-before((), 1, ('a'))");
	}
	
	public void testInsertEmpty3() throws XMLDBException {
		assertSeq(new String[]{"a"}, "insert-before(('a'), 1, ())");
	}
	
	public void testInsertOutOfBounds1() throws XMLDBException {
		assertSeq(new String[]{"c", "d", "a", "b"}, "insert-before(('a', 'b'), 0, ('c', 'd'))");
	}
	
	public void testInsertOutOfBounds2() throws XMLDBException {
		assertSeq(new String[]{"a", "b", "c", "d"}, "insert-before(('a', 'b'), 3, ('c', 'd'))");
	}
	
	public void testInsertOutOfBounds3() throws XMLDBException {
		assertSeq(new String[]{"a", "b", "c", "d"}, "insert-before(('a', 'b'), 4, ('c', 'd'))");
	}
	
	public void testInsertAtomic1() throws XMLDBException {
		assertSeq(new String[]{"a", "c", "d", "b"}, "insert-before(('a', 'b'), 2, ('c', 'd'))");
	}
	
	public void testInsertAtomic2() throws XMLDBException {
		assertSeq(new String[]{"c", "d", "a", "b"}, "insert-before(('a', 'b'), 1, ('c', 'd'))");
	}
	
	public void testInsertAtomic3() throws XMLDBException {
		assertSeq(new String[]{"a", "a", "b", "b"}, "insert-before(('a', 'b'), 2, ('a', 'b'))");
	}
	
	public void testInsertNodes1() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<c/>", "<d/>", "<b/>"}, "insert-before(/top/x/*, 2, /top/y/*)");
	}
	
	public void testInsertNodes2() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<c/>", "<d/>", "<a/>", "<b/>"}, "insert-before(/top/x/*, 1, /top/y/*)");
	}

	public void testInsertNodes3() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<b/>", "<c/>", "<d/>"}, "insert-before(/top/x/*, 3, /top/y/*)");
	}

	// TODO: currently fails because duplicate nodes are removed
	public void testInsertNodes4() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<a/>", "<b/>", "<b/>"}, "insert-before(/top/x/*, 2, /top/x/*)");
	}

	public void testInsertMixed1() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "c", "<b/>"}, "insert-before(/top/x/*, 2, ('c'))");
	}

	// TODO: currently fails because duplicate nodes are removed
	public void testInsertMixed2() throws XMLDBException {
		createDocument("foo", "<top><x><a/><b/></x><y><c/><d/></y></top>");
		assertSeq(new String[]{"<a/>", "<a/>", "<b/>", "<b/>", "c"}, "insert-before((/top/x/*, 'c'), 2, /top/x/*)");
	}

	private void assertSeq(String[] expected, String q) throws XMLDBException {
		ResourceSet rs = query.query(q);
		assertEquals(expected.length, rs.getSize());
		List<String> a = Arrays.asList(expected);
		List<Object> r = new ArrayList<Object>((int) rs.getSize());
		for (int i = 0; i < rs.getSize(); i++) r.add(rs.getResource(i).getContent());
		if (!a.equals(r)) fail("expected " + a + ", got " + r);
	}
	
	private XMLResource createDocument(String name, String content) throws XMLDBException {
		XMLResource res = (XMLResource) c.createResource(name, XMLResource.RESOURCE_TYPE);
		res.setContent(content);
		c.storeResource(res);
		return res;
	}

	private Collection setupTestCollection() throws XMLDBException {
		Collection root = DatabaseManager.getCollection(URI, "admin", "");
		CollectionManagementService rootcms = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
		Collection c = root.getChildCollection("test");
		if(c != null) rootcms.removeCollection("test");
		rootcms.createCollection("test");
		c = DatabaseManager.getCollection(URI+"/test", "admin", "");
		assertNotNull(c);
		return c;
	}

	protected void setUp() {
		try {
			// initialize driver
			Database database = (Database) Class.forName(DRIVER).newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			
			c = setupTestCollection();
			query = (XPathQueryService) c.getService("XPathQueryService", "1.0");

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("failed setup", e);
		}
	}
	
	protected void tearDown() {
		try {
			if (c != null) c.close();
            c = null;
            query = null;
		} catch (XMLDBException e) {
			throw new RuntimeException("failed teardown", e);
		}
	}
	
}
