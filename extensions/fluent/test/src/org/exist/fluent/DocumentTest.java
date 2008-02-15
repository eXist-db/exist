package org.exist.fluent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DocumentTest extends DatabaseTestCase {

	@Test public void nameAndPathFromLoad() {
		Document doc = db.createFolder("/top").documents().load(Name.create("foo"), Source.blob("helloworld"));
		assertEquals("foo", doc.name());
		assertEquals("/top/foo", doc.path());
	}
	
	@Test public void contentsAsStringFromLoad() {
		Document doc = db.createFolder("/top").documents().load(Name.create("foo"), Source.blob("helloworld"));
		assertEquals("helloworld", doc.contentsAsString());
	}

	@Test public void lengthFromLoad1() {
		Document doc = db.createFolder("/top").documents().load(Name.create("foo"), Source.blob("helloworld"));
		assertEquals(10, doc.length());
	}

	@Test public void lengthFromLoad2() {
		Document doc = db.createFolder("/top").documents().load(Name.create("foo"), Source.blob(""));
		assertEquals(0, doc.length());
	}

	@Test public void copy1() {
		Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
		Document original = c1.documents().load(Name.create("original"), Source.blob("helloworld"));
		Document copy = original.copy(c2, Name.keepCreate());
		assertEquals(1, c1.documents().size());
		assertEquals(1, c2.documents().size());
		assertEquals("helloworld", original.contentsAsString());
		assertEquals("helloworld", copy.contentsAsString());
	}

	@Test public void move1() {
		Folder c1 = db.createFolder("/c1"), c2 = db.createFolder("/c2");
		Document doc = c1.documents().load(Name.create("original"), Source.blob("helloworld"));
		doc.move(c2, Name.keepCreate());
		assertEquals(0, c1.documents().size());
		assertEquals(1, c2.documents().size());
		assertEquals("/c2/original", doc.path());
		assertEquals("helloworld", doc.contentsAsString());
	}

}
