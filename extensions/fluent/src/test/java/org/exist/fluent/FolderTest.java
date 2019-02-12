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
	
	@Test public void relativePath1() {
		assertEquals("foo", db.getFolder("/").relativePath("/foo"));
	}

	@Test public void relativePath2() {
		assertEquals("foo/bar", db.getFolder("/").relativePath("/foo/bar"));
	}

	@Test public void relativePath3() {
		assertEquals("bar", db.createFolder("/foo").relativePath("/foo/bar"));
	}

	@Test public void relativePath4() {
		assertEquals("", db.createFolder("/foo").relativePath("/foo"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void relativePath5() {
		db.createFolder("/foo").relativePath("/foobar");
	}

	@Test(expected = IllegalArgumentException.class)
	public void relativePath6() {
		db.createFolder("/foo").relativePath("foo");
	}

	@Test(expected = IllegalArgumentException.class)
	public void relativePath7() {
		db.getFolder("/").relativePath("foo");
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
		final Folder c1 = db.getFolder("/top");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		final Folder c2 = c1.children().get("nested");
		assertEquals("http://www.ideanest.com/", c2.namespaceBindings().get("foo"));
	}

	@Test public void namespace1() {
		final Folder c1 = db.getFolder("/");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		assertEquals("http://www.ideanest.com/", c1.namespaceBindings().get("foo"));
	}

	@Test public void namespace2() {
		final Folder c1 = db.getFolder("/");
		c1.namespaceBindings().put("", "http://www.ideanest.com/");
		assertEquals("http://www.ideanest.com/", c1.namespaceBindings().get(""));
	}

	@Test public void namespace3() {
		final Folder c1 = db.getFolder("/");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		c1.namespaceBindings().remove("foo");
		assertNull(c1.namespaceBindings().get("foo"));
	}

	@Test public void namespace4() {
		final Folder c1 = db.getFolder("/");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		c1.namespaceBindings().put("bar", "urn:blah");
		c1.namespaceBindings().remove("foo");
		assertNull(c1.namespaceBindings().get("foo"));
		assertEquals("urn:blah", c1.namespaceBindings().get("bar"));
	}

	@Test public void namespace5() {
		final Folder c1 = db.getFolder("/");
		c1.namespaceBindings().put("foo", "http://www.ideanest.com/");
		c1.namespaceBindings().put("bar", "urn:blah");
		c1.namespaceBindings().clear();
		assertNull(c1.namespaceBindings().get("foo"));
		assertNull(c1.namespaceBindings().get("bar"));
	}

	@Test public void buildDocument1() {
		final Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.create(db, "doc1")).elem("test").end("test").commit();
		assertEquals(1, c1.documents().size());
	}

	@Test public void buildDocument2() {
		final Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.create(db, "doc1")).elem("test1").end("test1").commit();
		c1.documents().build(Name.overwrite(db, "doc1")).elem("test2").end("test2").commit();
		assertEquals(1, c1.documents().size());
	}

	@Test(expected = DatabaseException.class)
	public void buildDocument3() {
		final Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.create(db, "doc1")).elem("test1").end("test1").commit();
		c1.documents().build(Name.create(db, "doc1")).elem("test2").end("test2").commit();
	}

	@Test public void buildDocument4() {
		final Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.generate(db)).elem("test").end("test").commit();
		assertEquals(1, c1.documents().size());
	}

	@Test public void buildDocument5() {
		final Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.generate(db)).elem("test").end("test").commit();
		c1.documents().build(Name.generate(db)).elem("test").end("test").commit();
		assertEquals(2, c1.documents().size());
	}

	@Test public void buildDocument6() {
		final Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.create(db, "child/doc1")).elem("test").end("test").commit();
		assertEquals(0, c1.documents().size());
		assertEquals(1, db.getFolder("/top/child").documents().size());
	}

	@Test public void size1() {
		assertEquals(0, db.getFolder("/").documents().size());
	}

	@Test public void size2() {
		final Folder c1 = db.createFolder("/top/nested");
		c1.documents().build(Name.generate(db)).elem("test").end("test").commit();
		c1.documents().build(Name.generate(db)).elem("test").end("test").commit();
		c1.documents().build(Name.create(db, "doc1")).elem("test").end("test").commit();
		assertEquals(3, c1.documents().size());
		assertEquals(0, db.getFolder("/top").documents().size());
	}

	@Test public void childrenSize1() {
		final Folder c1 = db.createFolder("/top");
		assertEquals(0, c1.children().size());
	}

	@Test public void childrenSize2() {
		db.createFolder("/top/nested1");
		db.createFolder("/top/nested2");
		final Folder c1 = db.getFolder("/top");
		assertEquals(2, c1.children().size());
	}

	@Test public void childrenSize3() {
		db.createFolder("/top/nested1");
		db.createFolder("/top/nested2").documents().build(Name.generate(db)).elem("test").end("test").commit();
		final Folder c1 = db.getFolder("/top");
		c1.documents().build(Name.generate(db)).elem("test").end("test").commit();
		assertEquals(2, c1.children().size());
	}
	
	@Test public void iterateChildren1() {
		db.createFolder("/top1");
		db.createFolder("/top2");
		final Collection<Folder> children = new ArrayList<>();
		for (final Folder child : db.getFolder("/").children()) {
			children.add(child);
		}
		assertEquals(3, children.size());
	}

	@Test public void clear1() {
		final Folder c1 = db.createFolder("/top");
		c1.clear();
		assertEquals(0, c1.documents().size());
	}

	@Test public void clear2() {
		final Folder c1 = db.createFolder("/top");
		c1.documents().build(Name.generate(db)).elem("test").end("test").commit();
		c1.documents().build(Name.generate(db)).elem("test").end("test").commit();
		c1.clear();
		assertEquals(0, c1.documents().size());
	}

	@Test public void clear3() {
		db.createFolder("/top/nested1");
		db.createFolder("/top/nested2");
		final Folder c1 = db.getFolder("/top");
		c1.clear();
		assertEquals(0, c1.children().size());
	}

	@Test public void clear4() {
		db.createFolder("/top/nested1");
		db.createFolder("/top/nested1/more");
		db.createFolder("/top/nested2");
		final Folder c1 = db.getFolder("/top");
		c1.documents().build(Name.generate(db)).elem("test").end("test").commit();
		c1.documents().build(Name.generate(db)).elem("test").end("test").commit();
		c1.clear();
		assertEquals(0, c1.documents().size());
		assertEquals(0, c1.children().size());
	}

	@Test public void delete1() {
		final Folder c1 = db.createFolder("/top/nested");
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
		final Folder c1 = db.getFolder("/top/nested");
		db.getFolder("/top/nested/more");
		c1.delete();
		db.getFolder("/top/nested/more");
	}
	
	@Test public void deleteRoot1() {
		db.getFolder("/").delete();
		assertEquals(0, db.getFolder("/").documents().size());
		assertEquals(0, db.getFolder("/").children().size());
	}
	
	@Test public void deleteRoot2() {
		db.getFolder("/").documents().load(Name.create(db, "foo"), Source.xml("<foo/>"));
		db.getFolder("/").delete();
		assertEquals(0, db.getFolder("/").documents().size());
		assertEquals(0, db.getFolder("/").children().size());
	}

	@Test public void deleteRoot3() {
		db.getFolder("/").documents().load(Name.create(db, "foo"), Source.xml("<foo/>"));
		db.getFolder("/").documents().load(Name.create(db, "bar"), Source.xml("<bar/>"));
		db.getFolder("/").delete();
		assertEquals(0, db.getFolder("/").documents().size());
		assertEquals(0, db.getFolder("/").children().size());
	}

	@Test public void getDocument1() {
		final Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create(db, "original")).elem("test").end("test").commit();
		final Document d = c1.documents().get("original");
		assertNotNull(d);
	}

	@Test public void getDocument2() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c1/c2");
		c2.documents().build(Name.create(db, "original")).elem("test").end("test").commit();
		final Document d = c1.documents().get("c2/original");
		assertNotNull(d);
	}

	@Test public void containsDocument1() {
		final Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create(db, "original")).elem("test").end("test").commit();
		assertTrue(c1.documents().contains("original"));
	}

	@Test public void containsDocument2() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c1/c2");
		c2.documents().build(Name.create(db, "original")).elem("test").end("test").commit();
		assertTrue(c1.documents().contains("c2/original"));
	}

	@Test public void query1() {
		final Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create(db, "original")).elem("test").end("test").commit();
		c1.query().single("/test");
	}

	@Test public void query2() {
		final Folder c1 = db.createFolder("/c1");
		c1.namespaceBindings().put("", "http://example.com");
		c1.documents().build(Name.create(db, "original")).elem("test").end("test").commit();
		c1.query().single("/test");
	}
	
	@Test public void queryGetFreshService() {
		final Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create(db, "original")).namespace("", "foo").elem("test").end("test").commit();
		c1.query().namespace("", "foo").single("/test");
		assertFalse(c1.query().exists("/test"));	// namespace bindings not propagated from previous query
	}

	@Test public void queryBaseUri() {
		final Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create(db, "original")).elem("test").end("test").commit();
		assertTrue(c1.query().single("doc-available('original')").booleanValue());
	}

	@Test public void convertToSequence() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		c1.documents().build(Name.create(db, "one")).elem("test").end("test").commit();
		c1.children().create("sub").documents().build(Name.create(db, "another"))	.elem("test").end("test").commit();
		assertEquals(0, c2.query().all("/test").size());
		assertEquals(2, c1.query().all("/test").size());
		assertEquals(2, c2.query().all("$_1/test", new Object[] { c1 }).size());
	}

	@Test public void convertDocumentsToSequence() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		c1.documents().build(Name.create(db, "one")).elem("test").end("test").commit();
		c1.children().create("sub").documents().build(Name.create(db, "another")).elem("test").end("test").commit();
		assertEquals(0, c2.query().all("/test").size());
		assertEquals(1, c2.query().all("$_1/test", new Object[] { c1.documents() }).size());
	}

	@Test public void move1() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		final Folder f = c1.children().create("f");
		f.move(c2, Name.keepCreate(db));
		assertEquals("/c2/f", f.path());
		assertEquals(c2, f.parent());
	}

	@Test public void move2() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		final Folder f = c1.children().create("f");
		f.move(c2, Name.create(db, "g"));
		assertEquals("/c2/g", f.path());
		assertEquals(c2, f.parent());
	}

	@Test public void move3() {
		final Folder c1 = db.createFolder("/c1");
		final Folder f = c1.children().create("f");
		f.move(f.parent(), Name.create(db, "g"));
		assertEquals("/c1/g", f.path());
		assertEquals(c1, f.parent());
	}

	@Test public void copy1() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		final Folder f1 = c1.children().create("f");
		final Folder f2 = f1.copy(c2, Name.keepCreate(db));
		assertEquals("/c1/f", f1.path());
		assertEquals(c1, f1.parent());
		assertEquals("/c2/f", f2.path());
		assertEquals(c2, f2.parent());
	}

	@Test public void copy2() {
		final Folder c1 = db.createFolder("/c1");
		final Folder f1 = c1.children().create("f1");
		final Folder f2 = f1.copy(f1.parent(), Name.create(db, "f2"));
		assertEquals("/c1/f1", f1.path());
		assertEquals(c1, f1.parent());
		assertEquals("/c1/f2", f2.path());
		assertEquals(c1, f2.parent());
	}
}
