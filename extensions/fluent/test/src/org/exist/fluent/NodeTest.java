package org.exist.fluent;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

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
	
	@Test(expected = DatabaseException.class)
	public void comparableValueMemtree() {
		db.query().single("<foo/>").comparableValue();
	}

	@Test(expected = DatabaseException.class)
	public void comparableValue() {
		db.getFolder("/").documents().load(Name.generate(db), Source.xml("<foo/>")).root().comparableValue();
	}

	@Test
	public void equals1() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create(db,"foo"))
				.elem("top").elem("child").end("child").end("top").commit();
		final Object o1 = doc.query().single("//child");
		final Object o2 = doc.query().single("//child");
		assertTrue(o1.equals(o2));
		assertEquals(o1.hashCode(), o2.hashCode());
	}
	
	@Test
	public void equals2() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create(db,"foo"))
				.elem("top").elem("child").end("child").end("top").commit();
		final Object o1 = doc.query().single("//child");
		final Object o2 = doc.query().single("//top");
		assertFalse(o1.equals(o2));
		// can't assert unequal hashCodes, they're allowed to be the same
	}
	
	@Test
	public void equals3() {
		Folder folder = db.createFolder("/test");
		XMLDocument doc1 = folder.documents().build(Name.create(db,"foo1"))
				.elem("top").elem("child").end("child").end("top").commit();
		XMLDocument doc2 = folder.documents().build(Name.create(db,"foo2"))
				.elem("top").elem("child").end("child").end("top").commit();
		final Object o1 = doc1.query().single("//top");
		final Object o2 = doc2.query().single("//top");
		assertFalse(o1.equals(o2));
	}
	
	@Test
	public void compareDocumentOrderTo1() {
		final Node root = db.getFolder("/").documents().load(Name.generate(db), Source.xml(
				"<root><a><aa/></a><b><bb/></b><c><cc/></c></root>")).root();
		final Node a = root.query().single("//a").node();
		final Node aa = root.query().single("//aa").node();

		final Node b = root.query().single("//b").node();
		final Node bb = root.query().single("//bb").node();

		final Node c = root.query().single("//c").node();
		final Node cc = root.query().single("//cc").node();

		assertEquals(0, a.compareDocumentOrderTo(a));
		assertEquals(0, a.compareDocumentOrderTo(root.query().single("//a").node()));
		assertThat(a.compareDocumentOrderTo(b), lessThan(0));
		assertThat(c.compareDocumentOrderTo(b), greaterThan(0));
		assertThat(aa.compareDocumentOrderTo(a), greaterThan(0));
		assertThat(bb.compareDocumentOrderTo(cc), lessThan(0));
		assertThat(root.compareDocumentOrderTo(c), lessThan(0));
	}
	
	@Test
	public void compareDocumentOrderTo2() {
		final ItemList nodes = db.query().all("let $x := <root><a><aa/></a><b><bb/></b><c><cc/></c></root> return ($x//a, $x//aa, $x//b, $x//bb, $x//c, $x//cc, $x)");
		final Node root = nodes.get(6).node();

		final Node a = nodes.get(0).node();
		final Node aa = nodes.get(1).node();

		final Node b = nodes.get(2).node();
		final Node bb = nodes.get(3).node();

		final Node c = nodes.get(4).node();
		final Node cc = nodes.get(5).node();

		assertEquals(0, a.compareDocumentOrderTo(a));
		assertThat(a.compareDocumentOrderTo(b), lessThan(0));
		assertThat(c.compareDocumentOrderTo(b), greaterThan(0));
		assertThat(aa.compareDocumentOrderTo(a), greaterThan(0));
		assertThat(bb.compareDocumentOrderTo(cc), lessThan(0));
		assertThat(root.compareDocumentOrderTo(c), lessThan(0));
	}

	@Test(expected = DatabaseException.class)
	public void compareDocumentOrderTo3() {
		Node root1 = db.getFolder("/").documents().load(Name.generate(db), Source.xml("<root1/>")).root();
		Node root2 = db.getFolder("/").documents().load(Name.generate(db), Source.xml("<root2/>")).root();
		root1.compareDocumentOrderTo(root2);
	}

	@Test(expected = DatabaseException.class)
	public void compareDocumentOrderTo4() {
		Node root1 = db.getFolder("/").documents().load(Name.generate(db), Source.xml("<root1/>")).root();
		Node root2 = db.query().single("<root2/>").node();
		root1.compareDocumentOrderTo(root2);
	}

	@Test(expected = DatabaseException.class)
	public void compareDocumentOrderTo5() {
		Node root1 = db.query().single("<root1/>").node();
		Node root2 = db.query().single("<root2/>").node();
		root1.compareDocumentOrderTo(root2);
	}
	
	@Test
	public void append1() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create(db,"foo"))
				.elem("top").end("top").commit();
		Node node = doc.root().append().elem("child").end("child").commit();
		assertNotNull(node);
		assertEquals("child", node.name());
		assertEquals(1, doc.root().query().single("count(*)").intValue());
	}

	@Test
	public void append2() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create(db,"foo"))
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
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create(db,"foo"))
				.elem("top").end("top").commit();
		Node node = doc.root().append().elem("child").end("child").commit();
		node.delete();
		doc.root().append().elem("newchild").end("newchild").commit();
		node.update().attr("foo", "bar").commit();
	}

	@Test(expected = DatabaseException.class)
	public void afterDelete2() {
		XMLDocument doc = db.createFolder("/test").documents().build(Name.create(db,"foo"))
				.elem("top").end("top").commit();
		Node node = doc.root();
		doc.delete();
		doc = null;
		db.createFolder("/test").documents().build(Name.create(db,"bar"))
				.elem("ack").end("ack").commit();
		node.update().attr("foo", "bar").commit();
	}

	@Test(expected = DatabaseException.class)
	public void afterDelete3() {
		Folder folder = db.createFolder("/test");
		XMLDocument doc = folder.documents().build(Name.create(db,"foo"))
				.elem("top").end("top").commit();
		Node node = doc.root();
		folder.delete();
		db.createFolder("/test").documents().build(Name.create(db,"bar"))
				.elem("ack").end("ack").commit();
		node.update().attr("foo", "bar").commit();
	}
	
	@Test
	public void deleteRoot() {
		Node root = db.createFolder("/test").documents().load(Name.create(db,"test"), Source.xml("<foo/>")).root();
		root.delete();
		assertFalse(db.getFolder("/test").documents().contains("test"));
	}
	
	@Test public void replaceRoot() {
		Node root = db.createFolder("/test").documents().load(Name.create(db,"test"), Source.xml("<foo/>")).root();
		root.replace().elem("bar").end("bar").commit();
		assertEquals("bar", db.getFolder("/test").documents().get("test").xml().root().name());
	}
	
	@Test
	public void appendTriggersListeners() {
		final XMLDocument doc = db.getFolder("/").documents().load(Name.create(db,"foo"), Source.xml("<foo/>"));
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
		final XMLDocument doc = db.getFolder("/").documents().load(Name.create(db,"foo"), Source.xml("<foo><bar/></foo>"));
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
		final XMLDocument doc = db.getFolder("/").documents().load(Name.create(db,"foo"), Source.xml("<foo><bar/></foo>"));
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
		final XMLDocument doc = db.getFolder("/").documents().load(Name.create(db,"foo"), Source.xml("<foo><bar/></foo>"));
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
