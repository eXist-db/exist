package org.exist.collections;

import static org.junit.Assert.*;

import org.junit.Test;

public class CollectionURITest {

	@Test
	public void append() {
		CollectionURI uri = new CollectionURI("/db");
		uri.append("test1");
		assertTrue(uri.equals(new CollectionURI("/db/test1")));
		assertEquals(uri.toString(), "/db/test1");
        assertEquals(uri.hashCode(), new String("/db/test1").hashCode());
        uri.append("test2");
        assertTrue(uri.equals(new CollectionURI("/db/test1/test2")));
		assertEquals(uri.toString(), "/db/test1/test2");
        assertEquals(uri.hashCode(), new String("/db/test1/test2").hashCode());
    }

    @Test
    public void remove() {
        CollectionURI uri = new CollectionURI("/db/test1/test2");
        uri.removeLastSegment();
        assertTrue(uri.equals(new CollectionURI("/db/test1")));
        assertEquals(uri.toString(), "/db/test1");
        uri.removeLastSegment();
        assertTrue(uri.equals(new CollectionURI("/db")));
        assertEquals(uri.toString(), "/db");

        uri.append("testMe");
        assertTrue(uri.equals(new CollectionURI("/db/testMe")));
        assertEquals(uri.toString(), "/db/testMe");

        uri.removeLastSegment();
        assertTrue(uri.equals(new CollectionURI("/db")));
        assertEquals(uri.toString(), "/db");
    }
}
