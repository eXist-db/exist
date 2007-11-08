package org.exist.fluent;

import static org.junit.Assert.*;

import org.junit.Test;

public class ItemTest extends DatabaseTestCase {
	
	@Test public void equals1() {
		Item item1 = db.query().single("3"), item2 = db.query().single("3");
		assertTrue(item1.equals(item2));
		assertEquals(item1.hashCode(), item2.hashCode());
	}
	
	@Test public void equals2() {
		Item item1 = db.query().single("2"), item2 = db.query().single("3");
		assertFalse(item1.equals(item2));
		// can't assert anything about their hashcodes
	}
	
	@Test public void equals3() {
		Item item1 = db.query().single("2"), item2 = db.query().single("'foo'");
		assertFalse(item1.equals(item2));
		// can't assert anything about their hashcodes
	}
	
	@Test public void equals4() {
		XMLDocument doc = db.createFolder("/top").documents().build(Name.create("test"))
			.elem("root")
				.elem("text1").text("foo").end("text1")
				.elem("text2").text("foo").end("text2")
			.end("root").commit();
		Item item1 = doc.query().single("xs:string(//text1/text())");
		Item item2 = doc.query().single("xs:string(//text2/text())");
		assertTrue(item1.equals(item2));
		assertEquals(item1.hashCode(), item2.hashCode());
	}

	@Test public void convertToSequence() {
		XMLDocument doc = db.createFolder("/top").documents().build(Name.create("test"))
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
		Item res = doc.query().single("//b");
		assertEquals(1, doc.query().all("$_1//c", res).size());
	}
}
