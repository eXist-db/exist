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
	
	public static CharacterSet getCharacterSet(String encoding) {
		if("ASCII".equalsIgnoreCase(encoding)) {
			return ASCIICharSet.getInstance();
		} else if("US-ASCII".equalsIgnoreCase(encoding)) {
			return ASCIICharSet.getInstance();
		} else if ("ISO-8859-1".equalsIgnoreCase(encoding)) {
			return Latin1CharSet.getInstance();
		} else if ("ISO8859_1".equalsIgnoreCase(encoding)) {
			return Latin1CharSet.getInstance();
		} else if ("UTF-8".equalsIgnoreCase(encoding)) {
			return UnicodeCharSet.getInstance();
		} else if ("UTF8".equalsIgnoreCase(encoding)) {
			return UnicodeCharSet.getInstance();
		} else if ("utf-16".equalsIgnoreCase(encoding)) {
			return UnicodeCharSet.getInstance();
		} else if ("utf16".equalsIgnoreCase(encoding)) {
			return UnicodeCharSet.getInstance();
		} else if ("iso-8859-2".equalsIgnoreCase(encoding)) {
			return Latin2CharSet.getInstance();
		} else if ("ISO8859_2".equalsIgnoreCase(encoding)) {
			return Latin2CharSet.getInstance();
		} else if ("KOI8-R".equalsIgnoreCase(encoding)) {
			return KOI8RCharSet.getInstance();
		} else
			{return ASCIICharSet.getInstance();}
	}
}
