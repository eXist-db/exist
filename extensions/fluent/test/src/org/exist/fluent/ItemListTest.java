package org.exist.fluent;

import org.junit.Test;
import static org.junit.Assert.*;

public class ItemListTest extends DatabaseTestCase {
	@Test public void equals1() {
		ItemList list1 = db.query().all("(1, 2, 3)"), list2 = db.query().all("(1, 2, 3)");
		assertTrue(list1.equals(list2));
		assertEquals(list1.hashCode(), list2.hashCode());
	}
	
	@Test public void equals2() {
		ItemList list1 = db.query().all("(1, 2, 3)"), list2 = db.query().all("(1, 2, 4)");
		assertFalse(list1.equals(list2));
		// can't assert anything about their hashcodes
	}
	
	@Test public void equals3() {
		ItemList list1 = db.query().all("(1, 2, 3)"), list2 = db.query().all("(1, 2)");
		assertFalse(list1.equals(list2));
		// can't assert anything about their hashcodes
	}
	
	@Test public void equals4() {
		ItemList list1 = db.query().all("(1, 2)"), list2 = db.query().all("(1, 2, 3)");
		assertFalse(list1.equals(list2));
		// can't assert anything about their hashcodes
	}

	@Test public void nodesEquals1() {
		ItemList.NodesFacet list1 = db.query().all("(1, 2, 3)").nodes(), list2 = db.query().all("(1, 2, 3)").nodes();
		assertTrue(list1.equals(list2));
		assertEquals(list1.hashCode(), list2.hashCode());
	}

	@Test public void valuesEquals1() {
		ItemList.ValuesFacet list1 = db.query().all("(1, 2, 3)").values(), list2 = db.query().all("(1, 2, 3)").values();
		assertTrue(list1.equals(list2));
		assertEquals(list1.hashCode(), list2.hashCode());
	}

	@Test
	public void convertToSequence() {
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
		ItemList res = doc.query().all("//(b|d)");
		assertEquals(2, doc.query().all("$_1//c", new Object[] { res }).size());
	}
}
