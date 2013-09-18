/*
 * XMLStringTest.java - Aug 11, 2003
 * 
 * @author wolf
 */
package org.exist.util;


import junit.framework.TestCase;

public class XMLStringTest extends TestCase {

	public XMLStringTest(String arg0) {
		super(arg0);
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(XMLStringTest.class);
	}

	/*
	 * Test for XMLString append(char[], int, int)
	 */
	public void testAppendcharArrayintint() {
		XMLString s = new XMLString();
		char ch[] = "Hello".toCharArray();
		s.append(ch, 0, 5);
		assertEquals(s.toString(), "Hello");
	}

	public void testNormalize() {
		XMLString s = new XMLString();
		char ch[] = "\n	Hello World\r\n".toCharArray();
		s.append(ch, 0, ch.length);
		s = s.normalize(XMLString.SUPPRESS_BOTH);
		String r = s.toString();
		System.out.println('"' + r + '"');
		assertEquals(r, "Hello World");
	}

    public void testCollapse() {
        XMLString s = new XMLString();
        char ch[] = "\n	Hello   World\r\n".toCharArray();
        s.append(ch, 0, ch.length);
        s = s.normalize(XMLString.NORMALIZE);
        String r = s.toString();
        System.out.println('"' + r + '"');
        assertEquals(r, "Hello World");
    }

	public void testSubstring() {
		XMLString s = new XMLString();
		char ch[] = "\n	Hello World\r\n".toCharArray();
		s.append(ch, 0, ch.length);
		s = s.normalize(XMLString.SUPPRESS_BOTH);
		String r = s.substring(6, 5);
		System.out.println('"' + r + '"');
		assertEquals(r, "World");
	}

	public void testInsert() {
		XMLString s = new XMLString();
		char ch[] = "Hello World".toCharArray();
		s.append(ch, 0, ch.length);
		s.insert(5, " happy");
		String r = s.toString();
		System.out.println('"' + r + '"');
		assertEquals(r, "Hello happy World");
		s = s.delete(5, 6);
		r = s.toString();
		System.out.println('"' + r + '"');
		assertEquals(r, "Hello World");
	}
}
