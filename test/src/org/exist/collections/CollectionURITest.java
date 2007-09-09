package org.exist.collections;

import static org.junit.Assert.*;

import org.junit.Test;

public class CollectionURITest {

	@Test
	public void append() {
		CollectionURI uri = new CollectionURI("db");
		uri.append("test1");
		System.out.println(uri);
		assertTrue(uri.equals(new CollectionURI("db/test1")));
		assertEquals(uri, "/db/test1");
		uri.append("test2");
		assertTrue(uri.equals(new CollectionURI("db/test1/test2")));
		assertEquals(uri, "/db/test1/test2");
	}
}
