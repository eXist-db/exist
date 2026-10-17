/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XMLStringTest {

    /*
     * Test for XMLString append(char[], int, int)
     */
    @Test
    void appendcharArrayintint() {
		final XMLString s = new XMLString();
		try {
			char ch[] = "Hello".toCharArray();
			s.append(ch, 0, 5);
            assertEquals("Hello", s.toString());
		} finally {
			s.reset();
		}
	}

    @Test
    void normalize() {
		final XMLString s = new XMLString();
		XMLString normalized =  null;
		try {
			final char ch[] = "\n	Hello World\r\n".toCharArray();
			s.append(ch, 0, ch.length);
			normalized = s.normalize(XMLString.SUPPRESS_BOTH);
			final String r = normalized.toString();
            assertEquals("Hello World", r);
		} finally {
			if (normalized != s) {
				normalized.reset();
			}
			s.reset();
		}
	}

    @Test
    void collapse() {
		final XMLString s = new XMLString();
		XMLString normalized =  null;
		try {
			final char ch[] = "\n	Hello   World\r\n".toCharArray();
			s.append(ch, 0, ch.length);
			normalized = s.normalize(XMLString.NORMALIZE);
			final String r = normalized.toString();
            assertEquals("Hello World", r);
		} finally {
			if (normalized != s) {
				normalized.reset();
			}
			s.reset();
		}
    }

    @Test
    void substring() {
		final XMLString s = new XMLString();
		XMLString normalized =  null;
		try {
			final char ch[] = "\n	Hello World\r\n".toCharArray();
			s.append(ch, 0, ch.length);
			normalized = s.normalize(XMLString.SUPPRESS_BOTH);
			final String r = normalized.substring(6, 5);
            assertEquals("World", r);
		} finally {
			if (normalized != s) {
				normalized.reset();
			}
			s.reset();
		}
	}

    @Test
    void insert() {
		final XMLString s = new XMLString();
		try {
			final char ch[] = "Hello World".toCharArray();
			s.append(ch, 0, ch.length);
			s.insert(5, " happy");
			String r = s.toString();
            assertEquals("Hello happy World", r);
			s.delete(5, 6);
			r = s.toString();
            assertEquals("Hello World", r);
		} finally {
			s.reset();
		}
	}
}
