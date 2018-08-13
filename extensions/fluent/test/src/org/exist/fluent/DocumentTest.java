package org.exist.fluent;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DocumentTest extends DatabaseTestCase {

	@Test public void nameAndPathFromLoad() {
		final Document doc = db.createFolder("/top").documents().load(Name.create(db, "foo"), Source.blob("helloworld"));
		assertEquals("foo", doc.name());
		assertEquals("/top/foo", doc.path());
	}
	
	@Test public void contentsAsStringFromLoad() {
		final Document doc = db.createFolder("/top").documents().load(Name.create(db, "foo"), Source.blob("helloworld"));
		assertEquals("helloworld", doc.contentsAsString());
	}

	@Test public void lengthFromLoad1() {
		final Document doc = db.createFolder("/top").documents().load(Name.create(db, "foo"), Source.blob("helloworld"));
		assertEquals(10, doc.length());
	}

	@Test public void lengthFromLoad2() {
		final Document doc = db.createFolder("/top").documents().load(Name.create(db, "foo"), Source.blob(""));
		assertEquals(0, doc.length());
	}

	@Test public void copy1() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		final Document original = c1.documents().load(Name.create(db, "original"), Source.blob("helloworld"));
		final Document copy = original.copy(c2, Name.keepCreate(db));
		assertEquals(1, c1.documents().size());
		assertEquals(1, c2.documents().size());
		assertEquals("helloworld", original.contentsAsString());
		assertEquals("helloworld", copy.contentsAsString());
	}
    
	@Test public void copy2() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		final Document original = c1.documents().load(Name.create(db, "original.xml"), Source.xml("<original/>"));
		final Document copy = original.copy(c2, Name.keepCreate(db));
		assertEquals(1, c1.documents().size());
		assertEquals(1, c2.documents().size());
		assertEquals("<original/>", original.contentsAsString());
		assertEquals("<original/>", copy.contentsAsString());
	}

	@Test public void move1() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		final Document doc = c1.documents().load(Name.create(db, "original"), Source.blob("helloworld"));
		doc.move(c2, Name.keepCreate(db));
		assertEquals(0, c1.documents().size());
		assertEquals(1, c2.documents().size());
		assertEquals("/c2/original", doc.path());
		assertEquals("helloworld", doc.contentsAsString());
	}
    
	@Test public void move2() {
		final Folder c1 = db.createFolder("/c1");
		final Folder c2 = db.createFolder("/c2");
		final Document doc = c1.documents().load(Name.create(db, "original.xml"), Source.xml("<original/>"));
		doc.move(c2, Name.keepCreate(db));
		assertEquals(0, c1.documents().size());
		assertEquals(1, c2.documents().size());
		assertEquals("/c2/original.xml", doc.path());
		assertEquals("<original/>", doc.contentsAsString());
	}

}
