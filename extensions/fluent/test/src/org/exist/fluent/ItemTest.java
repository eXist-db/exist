package org.exist.fluent;

import static org.junit.Assert.*;

import org.junit.Test;

public class ItemTest extends DatabaseTestCase {
	
	@Test public void equals1() {
		final Item item1 = db.query().single("3");
		final Item item2 = db.query().single("3");
		assertTrue(item1.equals(item2));
		assertEquals(item1.hashCode(), item2.hashCode());
	}
	
	@Test public void equals2() {
		final Item item1 = db.query().single("2");
		final Item item2 = db.query().single("3");
		assertFalse(item1.equals(item2));
		// can't assert anything about their hashcodes
	}
	
	@Test public void equals3() {
		final Item item1 = db.query().single("2");
		final Item item2 = db.query().single("'foo'");
		assertFalse(item1.equals(item2));
		// can't assert anything about their hashcodes
	}
	
	@Test public void equals4() {
		final XMLDocument doc = db.createFolder("/top").documents().build(Name.create(db, "test"))
			.elem("root")
				.elem("text1").text("foo").end("text1")
				.elem("text2").text("foo").end("text2")
			.end("root").commit();
		final Item item1 = doc.query().single("xs:string(//text1/text())");
		final Item item2 = doc.query().single("xs:string(//text2/text())");
		assertTrue(item1.equals(item2));
		assertEquals(item1.hashCode(), item2.hashCode());
	}

	@Test public void convertToSequence() {
		final XMLDocument doc = db.createFolder("/top").documents().build(Name.create(db, "test"))
			.elem("a")
				.elem("b")
					.elem("c").end("c")
				.end("b")
				.elem("d")
					.elem("c").end("c")
				.end("d")
				.elem("c").end("c")
			.end("a").commit();
		assertEquals(3, doc.query().all("//c").size());
		final Item res = doc.query().single("//b");
		assertEquals(1, doc.query().all("$_1//c", res).size());
	}
	
	@Test public void toItemList() {
		final Item item = db.query().single("3");
		final ItemList list = item.toItemList();
		assertEquals(1, list.size());
		assertEquals(item, list.get(0));
	}
	
	@Test public void comparableValue() {
		final Item item1 = db.query().single("3");
		final Item item2 = db.query().single("4");
		assertTrue(item1.comparableValue().compareTo(item2.comparableValue()) < 0);
	}
	
}
