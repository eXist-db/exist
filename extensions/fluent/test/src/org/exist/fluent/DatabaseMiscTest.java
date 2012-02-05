package org.exist.fluent;

import org.junit.Test;
import static org.junit.Assert.*;

public class DatabaseMiscTest extends DatabaseTestCase {
	@Test	public void queryDocs1() {
		Folder c1 = db.createFolder("/c1");
		XMLDocument d1 = c1.documents().build(Name.generate(db)).elem("test1").end("test1").commit();
		XMLDocument d2 = c1.documents().build(Name.generate(db)).elem("test2").end("test2").commit();
		c1.documents().build(Name.generate(db)).elem("test3").end("test3").commit();
		assertTrue(db.query(d1, d2).exists("/test1"));
		assertTrue(db.query(d1, d2).exists("/test2"));
		assertFalse(db.query(d1, d2).exists("/test3"));
	}

	@Test	public void queryBaseUri() {
		Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create(db, "original")).elem("test").end("test").commit();
		assertFalse(db.query().single("doc-available('original')").booleanValue());
		assertTrue(db.query().single("doc-available('c1/original')").booleanValue());
	}
	
	@Test public void getDocument1() {
		Folder c1 = db.createFolder("/c1");
		XMLDocument d1 = c1.documents().build(Name.create(db, "doc")).elem("test1").end("test1").commit();
		Document d2 = db.getDocument("/c1/doc");
		assertEquals(d1, d2);
	}

	@Test public void getDocument2() {
		XMLDocument d1 = db.getFolder("/").documents().build(Name.create(db, "doc")).elem("test1").end("test1").commit();
		Document d2 = db.getDocument("/doc");
		assertEquals(d1, d2);
	}

	@Test(expected = IllegalArgumentException.class)
	public void getDocumentBadPath1() {
		db.getDocument("");
	}

	@Test(expected = IllegalArgumentException.class)
	public void getDocumentBadPath2() {
		db.getDocument("doc");
	}

	@Test(expected = IllegalArgumentException.class)
	public void getDocumentBadPath3() {
		db.getDocument("/doc/");
	}
	
	@Test public void containsDocument1() {
		Folder c1 = db.createFolder("/c1");
		c1.documents().build(Name.create(db, "doc")).elem("test1").end("test1").commit();
		assertTrue(db.contains("/c1/doc"));
	}

	@Test public void containsDocument2() {
		db.getFolder("/").documents().build(Name.create(db, "doc")).elem("test1").end("test1").commit();
		assertTrue(db.contains("/doc"));
	}

	@Test public void containsFolder1() {
		db.createFolder("/c1");
		assertTrue(db.contains("/c1"));
	}

	@Test public void containsFolder2() {
		db.createFolder("/c1/c2");
		assertTrue(db.contains("/c1/c2"));
	}

	@Test public void containsFolder3() {
		assertTrue(db.contains("/"));
	}

	@Test public void containsMissing() {
		assertFalse(db.contains("/c1"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void containsBadPath1() {
		db.contains("");
	}

	@Test(expected = IllegalArgumentException.class)
	public void containsBadPath2() {
		db.contains("doc");
	}

	@Test(expected = IllegalArgumentException.class)
	public void containsBadPath3() {
		db.contains("/doc/");
	}

}
