package org.exist.fluent;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

public class MetadataTest extends DatabaseTestCase {
	@Test public void binaryDocumentCreationDate() {
		Date before = new Date();
		Document doc = db.getFolder("/").documents().load(Name.generate(db), Source.blob("hello"));
		Date after = new Date();
		assertTrue(before.compareTo(doc.metadata().creationDate()) <= 0);
		assertTrue(after.compareTo(doc.metadata().creationDate()) >= 0);
	}

	@Test public void xmlLoadDocumentCreationDate() {
		Date before = new Date();
		XMLDocument doc = db.getFolder("/").documents().load(Name.generate(db), Source.xml("<foo/>"));
		Date after = new Date();
		assertTrue(before.compareTo(doc.metadata().creationDate()) <= 0);
		assertTrue(after.compareTo(doc.metadata().creationDate()) >= 0);
	}

	@Test public void xmlBuildDocumentCreationDate() {
		Date before = new Date();
		XMLDocument doc = db.getFolder("/").documents().build(Name.generate(db)).elem("foo").end("foo").commit();
		Date after = new Date();
		assertTrue(before.compareTo(doc.metadata().creationDate()) <= 0);
		assertTrue(after.compareTo(doc.metadata().creationDate()) >= 0);
	}

	@Test public void folderCreationDate() {
		Date before = new Date();
		Folder folder = db.createFolder("/foo");
		Date after = new Date();
		assertTrue(before.compareTo(folder.metadata().creationDate()) <= 0);
		assertTrue(after.compareTo(folder.metadata().creationDate()) >= 0);
	}

	@Test public void xmlDocumentAppendLastModificationDate() throws InterruptedException {
		XMLDocument doc = db.getFolder("/").documents().load(Name.generate(db), Source.xml("<foo/>"));
		Thread.sleep(50);
		Date before = new Date();
		doc.root().append().elem("bar").end("bar").commit();
		Date after = new Date();
		assertTrue(before.compareTo(doc.metadata().lastModificationDate()) <= 0);
		assertTrue(after.compareTo(doc.metadata().lastModificationDate()) >= 0);
		assertTrue(doc.metadata().creationDate().compareTo(doc.metadata().lastModificationDate()) != 0);
	}

	@Test public void xmlDocumentReplaceLastModificationDate() throws InterruptedException {
		XMLDocument doc = db.getFolder("/").documents().load(Name.generate(db), Source.xml("<foo><bar/></foo>"));
		Thread.sleep(50);
		Date before = new Date();
		doc.query().single("//bar").node().replace().elem("baz").end("baz").commit();
		Date after = new Date();
		assertTrue(before.compareTo(doc.metadata().lastModificationDate()) <= 0);
		assertTrue(after.compareTo(doc.metadata().lastModificationDate()) >= 0);
		assertTrue(doc.metadata().creationDate().compareTo(doc.metadata().lastModificationDate()) != 0);
	}

	@Test public void xmlDocumentUpdateLastModificationDate() throws InterruptedException {
		XMLDocument doc = db.getFolder("/").documents().load(Name.generate(db), Source.xml("<foo/>"));
		Thread.sleep(50);
		Date before = new Date();
		doc.root().update().attr("bar", "baz").commit();
		Date after = new Date();
		assertTrue(before.compareTo(doc.metadata().lastModificationDate()) <= 0);
		assertTrue(after.compareTo(doc.metadata().lastModificationDate()) >= 0);
		assertTrue(doc.metadata().creationDate().compareTo(doc.metadata().lastModificationDate()) != 0);
	}
	
	@Test public void documentOwner() {
		Document doc = db.getFolder("/").documents().load(Name.generate(db), Source.xml("<foo/>"));
		assertEquals("admin", doc.metadata().owner());
		doc.metadata().owner("guest");
		assertEquals("guest", doc.metadata().owner());
	}

	@Test public void documentGroup() {
		Document doc = db.getFolder("/").documents().load(Name.generate(db), Source.xml("<foo/>"));
		assertEquals("dba", doc.metadata().group());
		doc.metadata().group("guest");
		assertEquals("guest", doc.metadata().group());
	}

	@Test public void folderOwner() {
		Folder folder = db.createFolder("/foo");
		assertEquals("admin", folder.metadata().owner());
		folder.metadata().owner("guest");
		assertEquals("guest", folder.metadata().owner());
	}

	@Test public void folderGroup() {
		Folder folder = db.createFolder("/foo");
		assertEquals("dba", folder.metadata().group());
		folder.metadata().group("guest");
		assertEquals("guest", folder.metadata().group());
	}

	@Test public void topFolderOwner() {
		Folder folder = db.getFolder("/");
		assertEquals("SYSTEM", folder.metadata().owner());
		folder.metadata().owner("guest");
		assertEquals("guest", folder.metadata().owner());
	}

	@Test public void topFolderGroup() {
		Folder folder = db.getFolder("/");
		assertEquals("dba", folder.metadata().group());
		folder.metadata().group("guest");
		assertEquals("guest", folder.metadata().group());
	}
	
	// TODO: test permissions stuff!

}
