/*
 * Created on Oct 17, 2003
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.exist.util;

/**
 * @author wolf
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class CharTest {

	public static void main(String[] args) {
		String s = "&<>{}/\"'";
		for(int i = 0; i < s.length(); i++) {
			System.out.println(Integer.toHexString((int)s.charAt(i)));
		}
	}
}
