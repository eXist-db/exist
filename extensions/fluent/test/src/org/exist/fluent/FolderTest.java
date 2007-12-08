package org.exist.fluent;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.Test;

public class FolderTest extends DatabaseTestCase {

	@Test public void createTop() {
		Folder f = db.createFolder("/top");
		assertEquals("top", f.name());
		assertEquals("/top", f.path());
		db.getFolder("/top");
	}

	@Test public void createNested() {
		Folder f = db.createFolder("/top/nested");
		assertEquals("nested", f.name());
		assertEquals("/top/nested", f.path());
		db.getFolder("/top/nested");
	}

	@Test public void getRoot() {
		assertEquals("", db.getFolder("/").name());
	}
	
	@Test public void createTopChild() {
		Folder f = db.getFolder("/").children().create("child");
		assertEquals("child", f.name());
		assertEquals("/child", f.path());
	}

	@Test public void createNestedChild() {
		Folder f = db.createFolder("/top").children().create("child");
		assertEquals("child", f.name());
		assertEquals("/top/child", f.path());
	}

	@Test(expected = DatabaseException.class)
	public void getMissingTop1() {
		db.getFolder("/top");
	}

	@Test(expected = DatabaseException.class)
	public void getMissingNested1() {
		db.getFolder("/top/nested");
	}

	@Test public void duplicate1() {
		Folder c1 = db.createFolder("/top");
		Folder c2 = c1.clone();
		assertEquals(c1.path(), c2.path());
	}

	@Test public void duplicate2() {
		Folder c1 = db.createFolder("/top");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		Folder c2 = c1.clone();
		assertEquals("http://www.ideanest.com/", c2.namespaceBindings().get("foo"));
	}

	@Test public void getName1() {
		assertEquals("top", db.createFolder("/top").name());
	}

	@Test public void getName2() {
		assertEquals("nested", db.createFolder("/top/nested").name());
	}

	@Test public void getPath1() {
		assertEquals("/top", db.createFolder("/top").path());
	}

	@Test public void getPath2() {
		assertEquals("/top/nested", db.createFolder("/top/nested").path());
	}

	@Test public void getPath3() {
		assertEquals("/", db.createFolder("/").path());
	}

	@Test public void getParent1() {
		assertEquals("/top", db.createFolder("/top/nested").parent().path());
	}

	@Test(expected = DatabaseException.class)
	public void getParent2() {
		db.getFolder("/").parent();
	}

	@Test public void getChild1() {
		db.createFolder("/top/nested");
		db.getFolder("/top").children().get("nested");
	}

	@Test public void getChild2() {
		db.createFolder("/top/nested/more");
		db.getFolder("/top").children().get("nested/more");
	}

	@Test(expected = IllegalArgumentException.class)
	public void getChild3() {
		db.createFolder("/top/nested");
		db.getFolder("/top").children().get("/nested");
	}

	@Test public void getChild4() {
		db.createFolder("/top/nested");
		db.getFolder("/").children().get("top");
	}

	@Test public void getChild5() {
		db.createFolder("/top/nested");
		Folder c1 = db.getFolder("/top");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		Folder c2 = c1.children().get("nested");
		assertEquals("http://www.ideanest.com/", c2.namespaceBindings().get("foo"));
	}

	@Test public void namespace1() {
		Folder c1 = db.getFolder("/");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		assertEquals("http://www.ideanest.com/", c1.namespaceBindings().get("foo"));
	}

	@Test public void namespace2() {
		Folder c1 = db.getFolder("/");
		c1.namespaceBindings().put("", "http://www.ideanest.com/");
		assertEquals("http://www.ideanest.com/", c1.namespaceBindings().get(""));
	}

	@Test public void namespace3() {
		Folder c1 = db.getFolder("/");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		c1.namespaceBindings().remove("foo");
		assertNull(c1.namespaceBindings().get("foo"));
	}

	@Test public void namespace4() {
		Folder c1 = db.getFolder("/");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		c1.namespaceBindings().put("bar", "urn:blah");
		c1.namespaceBindings().remove("foo");
		assertNull(c1.namespaceBindings().get("foo"));
		assertEquals("urn:blah", c1.namespaceBindings().get("bar"));
	}

	@Test public void namespace5() {
		Folder c1 = db.getFolder("/");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		c1.namespaceBindings().put("bar", "urn:blah");
		c1.namespaceBindings().clear();
		assertNull(c1.namespaceBindings().get("foo"));
		assertNull(c1.namespaceBindings().get("bar"));
	}

	@Test public void buildDocument1() {
		Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.create("doc1")).elem("test").end("test").commit();
		assertEquals(1, c1.documents().size());
	}

	@Test public void buildDocument2() {
		Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.create("doc1")).elem("test1").end("test1").commit();
		c1.documents().build(Name.overwrite("doc1")).elem("test2").end("test2").commit();
		assertEquals(1, c1.documents().size());
	}

	@Test(expected = DatabaseException.class)
	public void buildDocument3() {
		Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.create("doc1")).elem("test1").end("test1").commit();
		c1.documents().build(Name.create("doc1")).elem("test2").end("test2").commit();
	}

	@Test public void buildDocument4() {
		Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.generate()).elem("test").end("test").commit();
		assertEquals(1, c1.documents().size());
	}

	@Test public void buildDocument5() {
		Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.generate()).elem("test").end("test").commit();
		c1.documents().build(Name.generate()).elem("test").end("test").commit();
		assertEquals(2, c1.documents().size());
	}

	@Test public void size1() {
		assertEquals(0, db.getFolder("/").documents().size());
	}

	@Test public void size2() {
		Folder c1 = db.createFolder("/top/nested");
		c1.documents().build(Name.generate()).elem("test").end("test").commit();
		c1.documents().build(Name.generate()).elem("test").end("test").commit();
		c1.documents().build(Name.create("doc1")).elem("test").end("test").commit();
		assertEquals(3, c1.documents().size());
		assertEquals(0, db.getFolder("/top").documents().size());
	}

	@Test public void childrenSize1() {
		Folder c1 = db.createFolder("/top");
		assertEquals(0, c1.children().size());
	}

	@Test public void childrenSize2() {
		db.createFolder("/top/nested1");
		db.createFolder("/top/nested2");
		Folder c1 = db.getFolder("/top");
		assertEquals(2, c1.children().size());
	}

	@Test public void childrenSize3() {
		db.createFolder("/top/nested1");
		db.createFolder("/top/nested2").documents().build(Name.generate()).elem("test").end("test").commit();
		Folder c1 = db.getFolder("/top");
		c1.documents().build(Name.generate()).elem("test").end("test").commit();
		assertEquals(2, c1.children().size());
	}
	
	@Test public void iterateChildren1() {
		db.createFolder("/top1");
		db.createFolder("/top2");
		Collection<Folder> children = new ArrayList<Folder>();
		for (Folder child : db.getFolder("/").children()) {
			children.add(child);
		}
		assertEquals(3, children.size());
	}

	@Test public void clear1() {
		Folder c1 = db.createFolder("/top");
		c1.clear();
		assertEquals(0, c1.documents().size());
	}

	@Test public void clear2() {
		Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.generate()).elem("test").end("test").commit();
		c1.documents().build(Name.generate()).elem("test").end("test").commit();
		c1.clear();
		assertEquals(0, c1.documents().size());
	}

	@Test public void clear3() {
		db.createFolder("/top/nested1");
		db.createFolder("/top/nested2");
		Folder c1 = db.getFolder("/top");
		c1.clear();
		assertEquals(0, c1.children().size());
	}

	@Test public void clear4() {
		db.createFolder("/top/nested1");
		db.createFolder("/top/nested1/more");
		db.createFolder("/top/nested2");
		Folder c1 = db.getFolder("/top");
		c1.documents().build(Name.generate()).elem("test").end("test").commit();
		c1.documents().build(Name.generate()).elem("test").end("test").commit();
		c1.clear();
		assertEquals(0, c1.documents().size());
		assertEquals(0, c1.children().size());
	}

	@Test public void delete1() {
		Folder c1 = db.createFolder("/top/nested");
		db.getFolder("/top/nested");
		c1.delete();
		try {
			db.getFolder("/top/nested");
			fail();
		} catch (DatabaseException e) {
		}
	}

	@Test(expected = DatabaseException.class)
	public void delete2() {
		db.createFolder("/top/nested/more");
		Folder c1 = db.getFolder("/top/nested");
		db.getFolder("/top/nested/more");
		c1.delete();
		db.getFolder("/top/nested/more");
	}

	@Test public void getDocument() {
		Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create("original")).elem("test").end("test").commit();
		Document d = c1.documents().get("original");
		assertNotNull(d);
	}

	@Test public void query1() {
		Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create("original")).elem("test").end("test").commit();
		c1.query().single("/test");
	}

	@Test public void query2() {
		Folder c1 = db.createFolder("/c1");
		c1.namespaceBindings().put("", "http://example.com");
		c1.documents().build(Name.create("original")).elem("test").end("test").commit();
		c1.query().single("/test");
	}

	@Test public void queryBaseUri() {
		Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create("original")).elem("test").end("test").commit();
		assertTrue(c1.query().single("doc-available('original')").booleanValue());
	}

	@Test public void convertToSequence() {
		Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
		c1.documents().build(Name.create("one")).elem("test").end("test").commit();
		c1.children().create("sub").documents().build(Name.create("another"))	.elem("test").end("test").commit();
		assertEquals(0, c2.query().all("/test").size());
		assertEquals(2, c1.query().all("/test").size());
		assertEquals(2, c2.query().all("$_1/test", new Object[] { c1 }).size());
	}

	@Test public void convertDocumentsToSequence() {
		Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
		c1.documents().build(Name.create("one")).elem("test").end("test").commit();
		c1.children().create("sub").documents().build(Name.create("another")).elem("test").end("test").commit();
		assertEquals(0, c2.query().all("/test").size());
		assertEquals(1, c2.query().all("$_1/test", new Object[] { c1.documents() }).size());
	}

	@Test public void move1() {
		Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
		Folder f = c1.children().create("f");
		f.move(c2, Name.keepCreate());
		assertEquals("/c2/f", f.path());
		assertEquals(c2, f.parent());
	}

	@Test public void move2() {
		Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
		Folder f = c1.children().create("f");
		f.move(c2, Name.create("g"));
		assertEquals("/c2/g", f.path());
		assertEquals(c2, f.parent());
	}

	@Test public void move3() {
		Folder c1 = db.createFolder("/c1");
		Folder f = c1.children().create("f");
		f.move(f.parent(), Name.create("g"));
		assertEquals("/c1/g", f.path());
		assertEquals(c1, f.parent());
	}

	@Test public void copy1() {
		Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
		Folder f1 = c1.children().create("f");
		Folder f2 = f1.copy(c2, Name.keepCreate());
		assertEquals("/c1/f", f1.path());
		assertEquals(c1, f1.parent());
		assertEquals("/c2/f", f2.path());
		assertEquals(c2, f2.parent());
	}

	@Test public void copy2() {
		Folder c1 = db.createFolder("/c1");
		Folder f1 = c1.children().create("f1");
		Folder f2 = f1.copy(f1.parent(), Name.create("f2"));
		assertEquals("/c1/f1", f1.path());
		assertEquals(c1, f1.parent());
		assertEquals("/c1/f2", f2.path());
		assertEquals(c1, f2.parent());
	}
}
