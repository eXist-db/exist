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

	@Test	public void convertToSequence() {
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
	
	@Test(expected=DatabaseException.class) public void stale1() {
		XMLDocument doc = db.createFolder("/top").documents().load(Name.generate(), Source.xml(
				"<foo><bar1/><bar2/></foo>"));
		ItemList list = doc.query().all("/foo/*");
		doc.query().all("//bar1").deleteAllNodes();
		doc.query().all("$_1", list);
	}
	
	@Test public void stale2() {
		XMLDocument doc = db.createFolder("/top").documents().load(Name.generate(), Source.xml(
				"<foo><bar1/><bar2/></foo>"));
		ItemList list = doc.query().all("/foo/*");
		doc.query().all("//bar1").deleteAllNodes();
		list.removeDeletedNodes();
		assertEquals(1, list.size());
		assertEquals(1, doc.query().all("$_1", list).size());
	}
		
	@Test public void deleteAllNodes1() {
		XMLDocument doc = db.createFolder("/top").documents().load(Name.generate(), Source.xml(
				"<foo><bar><bar/></bar></foo>"));
		doc.query().all("//bar").deleteAllNodes();
		assertEquals("<foo/>", doc.contentsAsString());
	}

	@Test public void deleteAllNodes2() {
		XMLDocument doc = db.createFolder("/top").documents().load(Name.generate(), Source.xml(
				"<bar><bar><bar/></bar></bar>"));
		doc.query().all("//bar").deleteAllNodes();
		assertEquals(0, db.getFolder("/top").documents().size());
	}
}
