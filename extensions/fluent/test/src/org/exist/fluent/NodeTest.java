package org.exist.fluent;

import static org.junit.Assert.*;

import java.util.EnumSet;

import org.jmock.*;
import org.jmock.integration.junit4.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class NodeTest extends DatabaseTestCase {
	private Mockery context = new JUnit4Mockery();
	
	@Test(expected = UnsupportedOperationException.class)
	public void appendMemtree() {
		db.query().single("<foo/>").node().append();
	}
	
	@Test(expected = UnsupportedOperationException.class)
	public void replaceMemtree() {
		db.query().single("<foo/>").node().replace();
	}
	
	@Test(expected = UnsupportedOperationException.class)
	public void updateMemtree() {
		db.query().single("<foo/>").node().update();
	}
	
	@Test
	public void equals1() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create("foo"))
				.elem("top").elem("child").end("child").end("top").commit();
		Object o1 = doc.query().single("//child"), o2 = doc.query().single("//child");
		assertTrue(o1.equals(o2));
		assertEquals(o1.hashCode(), o2.hashCode());
	}
	
	@Test
	public void equals2() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create("foo"))
				.elem("top").elem("child").end("child").end("top").commit();
		Object o1 = doc.query().single("//child"), o2 = doc.query().single("//top");
		assertFalse(o1.equals(o2));
		// can't assert unequal hashCodes, they're allowed to be the same
	}
	
	@Test
	public void equals3() {
		Folder folder = db.createFolder("/test");
		XMLDocument doc1 = folder.documents().build(Name.create("foo1"))
				.elem("top").elem("child").end("child").end("top").commit();
		XMLDocument doc2 = folder.documents().build(Name.create("foo2"))
				.elem("top").elem("child").end("child").end("top").commit();
		Object o1 = doc1.query().single("//top"), o2 = doc2.query().single("//top");
		assertFalse(o1.equals(o2));
	}

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
	
	@Test
	public void appendTriggersListeners() {
		final XMLDocument doc = db.getFolder("/").documents().load(Name.create("foo"), Source.xml("<foo/>"));
		final Document.Listener listener = context.mock(Document.Listener.class);
		context.checking(new Expectations() {{
			one(listener).handle(new Document.Event(Trigger.BEFORE_UPDATE, doc.path(), doc));
			one(listener).handle(new Document.Event(Trigger.AFTER_UPDATE, doc.path(), doc));
		}});
		db.getFolder("/").listeners().add(EnumSet.of(Trigger.BEFORE_UPDATE, Trigger.AFTER_UPDATE), listener);
		try {
			doc.root().append().elem("bar").end("bar").commit();
		} finally {
			db.getFolder("/").listeners().remove(listener);
		}
	}
	
	@Test
	public void deleteTriggersListeners() {
		final XMLDocument doc = db.getFolder("/").documents().load(Name.create("foo"), Source.xml("<foo><bar/></foo>"));
		final Document.Listener listener = context.mock(Document.Listener.class);
		context.checking(new Expectations() {{
			one(listener).handle(new Document.Event(Trigger.BEFORE_UPDATE, doc.path(), doc));
			one(listener).handle(new Document.Event(Trigger.AFTER_UPDATE, doc.path(), doc));
		}});
		db.getFolder("/").listeners().add(EnumSet.of(Trigger.BEFORE_UPDATE, Trigger.AFTER_UPDATE), listener);
		try {
			doc.root().query().single("bar").node().delete();
		} finally {
			db.getFolder("/").listeners().remove(listener);
		}
	}

	@Test
	public void replaceTriggersListeners() {
		final XMLDocument doc = db.getFolder("/").documents().load(Name.create("foo"), Source.xml("<foo><bar/></foo>"));
		final Document.Listener listener = context.mock(Document.Listener.class);
		context.checking(new Expectations() {{
			one(listener).handle(new Document.Event(Trigger.BEFORE_UPDATE, doc.path(), doc));
			one(listener).handle(new Document.Event(Trigger.AFTER_UPDATE, doc.path(), doc));
		}});
		db.getFolder("/").listeners().add(EnumSet.of(Trigger.BEFORE_UPDATE, Trigger.AFTER_UPDATE), listener);
		try {
			doc.root().query().single("bar").node().replace().elem("baz").end("baz").commit();
		} finally {
			db.getFolder("/").listeners().remove(listener);
		}
	}

	@Test
	public void updateTriggersListeners() {
		final XMLDocument doc = db.getFolder("/").documents().load(Name.create("foo"), Source.xml("<foo><bar/></foo>"));
		final Document.Listener listener = context.mock(Document.Listener.class);
		context.checking(new Expectations() {{
			one(listener).handle(new Document.Event(Trigger.BEFORE_UPDATE, doc.path(), doc));
			one(listener).handle(new Document.Event(Trigger.AFTER_UPDATE, doc.path(), doc));
		}});
		db.getFolder("/").listeners().add(EnumSet.of(Trigger.BEFORE_UPDATE, Trigger.AFTER_UPDATE), listener);
		try {
			doc.root().query().single("bar").node().update().attr("x", "y").commit();
		} finally {
			db.getFolder("/").listeners().remove(listener);
		}
	}

}
