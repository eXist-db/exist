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
package org.exist.util.serializer.encodings;

public abstract class CharacterSet {

	public abstract boolean inCharacterSet(char ch);
	
	public static CharacterSet getCharacterSet(final String encoding) {
		if (encoding == null) {
			return ASCIICharSet.getInstance();
		}
		return switch (encoding.toLowerCase()) {
			case "ascii", "us-ascii" -> ASCIICharSet.getInstance();
			case "utf8", "utf-8", "utf16", "utf-16" -> UnicodeCharSet.getInstance();
			case "iso-8859-1", "iso8859_1" -> Latin1CharSet.getInstance();
			case "iso-8859-2", "iso8859_2" -> Latin2CharSet.getInstance();
			case "koi8-r" -> KOI8RCharSet.getInstance();
			default -> ASCIICharSet.getInstance();
		};
	}
}
