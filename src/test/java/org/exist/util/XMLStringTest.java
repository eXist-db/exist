/*
 * XMLStringTest.java - Aug 11, 2003
 * 
 * @author wolf
 */
package org.exist.util;


import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XMLStringTest {

	/*
	 * Test for XMLString append(char[], int, int)
	 */
	@Test
	public void appendcharArrayintint() {
		XMLString s = new XMLString();
		char ch[] = "Hello".toCharArray();
		s.append(ch, 0, 5);
		assertEquals(s.toString(), "Hello");
	}

	@Test
	public void normalize() {
		XMLString s = new XMLString();
		char ch[] = "\n	Hello World\r\n".toCharArray();
		s.append(ch, 0, ch.length);
		s = s.normalize(XMLString.SUPPRESS_BOTH);
		String r = s.toString();
		assertEquals(r, "Hello World");
	}

	@Test
    public void collapse() {
        XMLString s = new XMLString();
        char ch[] = "\n	Hello   World\r\n".toCharArray();
        s.append(ch, 0, ch.length);
        s = s.normalize(XMLString.NORMALIZE);
        String r = s.toString();
        assertEquals(r, "Hello World");
    }

	@Test
	public void substring() {
		XMLString s = new XMLString();
		char ch[] = "\n	Hello World\r\n".toCharArray();
		s.append(ch, 0, ch.length);
		s = s.normalize(XMLString.SUPPRESS_BOTH);
		String r = s.substring(6, 5);
		assertEquals(r, "World");
	}

	@Test
	public void insert() {
		XMLString s = new XMLString();
		char ch[] = "Hello World".toCharArray();
		s.append(ch, 0, ch.length);
		s.insert(5, " happy");
		String r = s.toString();
		assertEquals(r, "Hello happy World");
		s = s.delete(5, 6);
		r = s.toString();
		assertEquals(r, "Hello World");
	}
}
