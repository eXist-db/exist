package org.exist.fluent;

import org.junit.Test;
import static org.junit.Assert.*;

public class ItemListTest extends DatabaseTestCase {
	@Test public void equals1() {
		final ItemList list1 = db.query().all("(1, 2, 3)");
		final ItemList list2 = db.query().all("(1, 2, 3)");
		assertEquals(list1, list2);
		assertEquals(list1.hashCode(), list2.hashCode());
	}
	
	@Test public void equals2() {
		final ItemList list1 = db.query().all("(1, 2, 3)");
		final ItemList list2 = db.query().all("(1, 2, 4)");
		assertNotEquals(list1, list2);
		// can't assert anything about their hashcodes
	}
	
	@Test public void equals3() {
		final ItemList list1 = db.query().all("(1, 2, 3)");
		final ItemList list2 = db.query().all("(1, 2)");
		assertNotEquals(list1, list2);
		// can't assert anything about their hashcodes
	}
	
	@Test public void equals4() {
		final ItemList list1 = db.query().all("(1, 2)");
		final ItemList list2 = db.query().all("(1, 2, 3)");
		assertNotEquals(list1, list2);
		// can't assert anything about their hashcodes
	}

	@Test public void nodesEquals1() {
		final ItemList.NodesFacet list1 = db.query().all("(1, 2, 3)").nodes();
		final ItemList.NodesFacet list2 = db.query().all("(1, 2, 3)").nodes();
		assertEquals(list1, list2);
		assertEquals(list1.hashCode(), list2.hashCode());
	}

	@Test public void valuesEquals1() {
		final ItemList.ValuesFacet list1 = db.query().all("(1, 2, 3)").values();
		final ItemList.ValuesFacet list2 = db.query().all("(1, 2, 3)").values();
		assertEquals(list1, list2);
		assertEquals(list1.hashCode(), list2.hashCode());
	}

	@Test	public void convertToSequence() {
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
		final ItemList res = doc.query().all("//(b|d)");
		assertEquals(2, doc.query().all("$_1//c", res).size());
	}
	
	@Test(expected=DatabaseException.class) public void stale1() {
		final XMLDocument doc = db.createFolder("/top").documents().load(Name.generate(db), Source.xml(
				"<foo><bar1/><bar2/></foo>"));
		final ItemList list = doc.query().all("/foo/*");
		doc.query().all("//bar1").deleteAllNodes();
		doc.query().all("$_1", list);
	}
	
	@Test public void stale2() {
		final XMLDocument doc = db.createFolder("/top").documents().load(Name.generate(db), Source.xml(
				"<foo><bar1/><bar2/></foo>"));
		final ItemList list = doc.query().all("/foo/*");
		doc.query().all("//bar1").deleteAllNodes();
		list.removeDeletedNodes();
		assertEquals(1, list.size());
		assertEquals(1, doc.query().all("$_1", list).size());
	}
		
	@Test public void deleteAllNodes1() {
		final XMLDocument doc = db.createFolder("/top").documents().load(Name.generate(db), Source.xml(
				"<foo><bar><bar/></bar></foo>"));
		doc.query().all("//bar").deleteAllNodes();
		assertEquals("<foo/>", doc.contentsAsString());
	}

	@Test public void deleteAllNodes2() {
		final XMLDocument doc = db.createFolder("/top").documents().load(Name.generate(db), Source.xml(
				"<bar><bar><bar/></bar></bar>"));
		doc.query().all("//bar").deleteAllNodes();
		assertEquals(0, db.getFolder("/top").documents().size());
	}
}
