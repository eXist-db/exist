package org.exist.fluent;

import static org.junit.Assert.*;


import org.exist.util.io.FastByteArrayOutputStream;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.IOException;

public class XMLDocumentTest extends DatabaseTestCase {
	@Test public void query1() {
		final Folder c1 = db.createFolder("/c1");
		final XMLDocument doc = c1.documents().build(Name.create(db,"original")).elem("test").end("test").commit();
		doc.query().single("/test");
	}

	@Test public void query2() {
		final Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create(db,"original")).elem("test").end("test").commit();
		final XMLDocument doc = c1.documents().get("original").xml();
		doc.query().single("/test");
	}

	@Test public void query3() {
		final Folder c1 = db.createFolder("/c1");
		final XMLDocument doc = c1.documents().build(Name.create(db,"original")).elem("test").end("test").commit();
		assertEquals(1, doc.query().all("/test").size());
	}

	@Test public void query4() {
		final Folder c1 = db.createFolder("/c1");
		final XMLDocument doc = c1.documents().build(Name.create(db,"original")).elem("test").end("test").commit();
		c1.documents().build(Name.create(db,"another")).elem("test").end("test").commit();
		doc.query().single("/test");
		assertEquals(2, c1.query().all("/test").size());
	}

	@Test public void copy1() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		final XMLDocument original = c1.documents().build(Name.create(db,"original")).elem("test").end("test").commit();
		final XMLDocument copy = original.copy(c2, Name.keepCreate(db));
		assertEquals(1, c1.documents().size());
		c1.query().single("/test");
		assertEquals(1, c2.documents().size());
		c2.query().single("/test");
		copy.query().single("/test");
	}

	@Test public void move1() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		final XMLDocument doc = c1.documents().build(Name.create(db,"original")).elem("test").end("test").commit();
		doc.move(c2, Name.keepCreate(db));
		assertEquals(0, c1.documents().size());
		assertFalse(c1.query().exists("/test"));
		assertEquals(1, c2.documents().size());
		c2.query().single("/test");
		doc.query().single("/test");
		assertEquals("/c2/original", doc.path());
	}
	
	@Test public void delete1() {
		final Folder c1 = db.createFolder("/c1");
		final XMLDocument doc = c1.documents().build(Name.create(db,"original")).elem("test").end("test").commit();
		doc.delete();
		assertEquals(0, c1.documents().size());
	}

	@Test public void delete2() {
		final Folder c1 = db.createFolder("/c1");
		final XMLDocument doc1 = c1.documents().build(Name.create(db,"doc1")).elem("test").attr("xml:id", "a").end("test").commit();
		final XMLDocument doc2 = c1.documents().build(Name.create(db,"doc2")).elem("test2").attr("xml:id", "b").end("test2").commit();
		doc1.delete();
		doc2.delete();
		assertEquals(0, c1.documents().size());
	}

	@Test public void convertToSequence() {
		final Folder c = db.createFolder("/top");
		c.documents().build(Name.create(db,"one")).elem("test").end("test").commit();
		final XMLDocument doc = c.documents().build(Name.create(db,"two")).elem("test").end("test").commit();
		assertEquals(2, c.query().all("/test").size());
		assertEquals(1, c.query().all("$_1/test", new Object[] { doc }).size());
	}
	
	@Test public void nameAndPathFromCreate() {
		final XMLDocument doc = db.createFolder("/top").documents().build(Name.create(db,"foo")).elem("root").end("root").commit();
		assertEquals("foo", doc.name());
		assertEquals("/top/foo", doc.path());
	}
	
	@Test public void nameAndPathFromLoad() {
		final XMLDocument doc = db.createFolder("/top").documents().load(Name.create(db,"foo"), Source.xml("<root/>"));
		assertEquals("foo", doc.name());
		assertEquals("/top/foo", doc.path());
	}

	@Test public void contentsAsStringFromCreate() {
		final XMLDocument doc = db.createFolder("/top").documents().build(Name.create(db,"foo")).elem("root").end("root").commit();
		assertEquals("<root/>", doc.contentsAsString());
	}

	@Test public void contentsAsStringFromLoad() {
		final XMLDocument doc = db.createFolder("/top").documents().load(Name.create(db,"foo"), Source.xml("<root/>"));
		assertEquals("<root/>", doc.contentsAsString());
	}

	@Test public void lengthFromCreate() {
		final XMLDocument doc = db.createFolder("/top").documents().build(Name.create(db,"foo")).elem("root").end("root").commit();
		assertThat(doc.length(), Matchers.greaterThan(0L));
	}

	@Test public void lengthFromLoad() {
		final XMLDocument doc = db.createFolder("/top").documents().load(Name.create(db,"foo"), Source.xml("<root/>"));
		assertThat(doc.length(), Matchers.greaterThan(0L));
	}
	
	@Test public void writeToOutputStream() throws IOException {
		final XMLDocument doc = db.createFolder("/top").documents().load(Name.create(db,"foo"), Source.xml("<root/>"));
		try (final FastByteArrayOutputStream out = new FastByteArrayOutputStream()) {
			doc.write(out);
			out.close();
			assertEquals("<root/>", out.toString());
		}
	}

}
