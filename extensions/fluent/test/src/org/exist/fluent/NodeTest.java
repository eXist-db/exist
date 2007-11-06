package org.exist.fluent;

import static org.junit.Assert.*;

import org.junit.Test;

public class NodeTest extends DatabaseTestCase {
	@Test
	public void append1() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create("foo"))
				.elem("top").end("top").commit();
		Node node = doc.root().append().elem("child").end("child").commit();
		assertNotNull(node);
		assertEquals("child", node.name());
		assertEquals(1, doc.root().query().single("count(*)").intValue());
	}

	@Test
	public void append2() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create("foo"))
				.elem("top").end("top").commit();
		Node node = doc.root().append()
				.elem("child").attr("blah", "ick").elem("subchild").end("subchild").end("child").commit();
		assertNotNull(node);
		assertEquals("child", node.name());
		assertEquals(1, doc.root().query().single("count(*)").intValue());
		assertEquals("ick", node.query().single("@blah").value());
		assertEquals("subchild", node.query().single("*").node().name());
	}

	@Test(expected = DatabaseException.class)
	public void afterDelete1() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create("foo"))
				.elem("top").end("top").commit();
		Node node = doc.root().append().elem("child").end("child").commit();
		node.delete();
		doc.root().append().elem("newchild").end("newchild").commit();
		node.update().attr("foo", "bar").commit();
	}

	@Test(expected = DatabaseException.class)
	public void afterDelete2() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create("foo"))
				.elem("top").end("top").commit();
		Node node = doc.root();
		doc.delete();
		doc = null;
		db.createFolder("/test").documents().build(Name.create("bar"))
				.elem("ack").end("ack").commit();
		node.update().attr("foo", "bar").commit();
	}

	@Test(expected = DatabaseException.class)
	public void afterDelete3() {
		Folder folder = db.createFolder("/test");
		XMLDocument doc = folder.documents().build(Name.create("foo"))
				.elem("top").end("top").commit();
		Node node = doc.root();
		folder.delete();
		db.createFolder("/test").documents().build(Name.create("bar"))
				.elem("ack").end("ack").commit();
		node.update().attr("foo", "bar").commit();
	}
}
