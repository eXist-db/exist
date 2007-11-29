package org.exist.fluent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class XMLDocumentTest extends DatabaseTestCase {
	@Test public void query1() {
		Folder c1 = db.createFolder("/c1");
		XMLDocument doc = c1.documents().build(Name.create("original")).elem("test").end("test").commit();
		doc.query().single("/test");
	}

	@Test public void query2() {
		Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create("original")).elem("test").end("test").commit();
		XMLDocument doc = c1.documents().get("original").xml();
		doc.query().single("/test");
	}

	@Test public void query3() {
		Folder c1 = db.createFolder("/c1");
		XMLDocument doc = c1.documents().build(Name.create("original")).elem("test").end("test").commit();
		assertEquals(1, doc.query().all("/test").size());
	}

	@Test public void query4() {
		Folder c1 = db.createFolder("/c1");
		XMLDocument doc = c1.documents().build(Name.create("original")).elem("test").end("test").commit();
		c1.documents().build(Name.create("another")).elem("test").end("test").commit();
		doc.query().single("/test");
		assertEquals(2, c1.query().all("/test").size());
	}

	@Test public void copy1() {
		Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
		XMLDocument original = c1.documents().build(Name.create("original")).elem("test").end("test").commit();
		XMLDocument copy = original.copy(c2, Name.keepCreate());
		assertEquals(1, c2.documents().size());
		copy.query().single("/test");
	}

	@Test public void convertToSequence() {
		Folder c = db.createFolder("/top");
		c.documents().build(Name.create("one")).elem("test").end("test").commit();
		XMLDocument doc = c.documents().build(Name.create("two")).elem("test").end("test").commit();
		assertEquals(2, c.query().all("/test").size());
		assertEquals(1, c.query().all("$_1/test", new Object[] { doc }).size());
	}
	
	@Test public void nameAndPathFromCreate() {
		XMLDocument doc = db.createFolder("/top").documents().build(Name.create("foo")).elem("root").end("root").commit();
		assertEquals("foo", doc.name());
		assertEquals("/top/foo", doc.path());
	}
	
	@Test public void nameAndPathFromLoad() {
		XMLDocument doc = db.createFolder("/top").documents().load(Name.create("foo"), Source.xml("<root/>"));
		assertEquals("foo", doc.name());
		assertEquals("/top/foo", doc.path());
	}

}
