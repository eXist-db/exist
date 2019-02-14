/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.util.serializer.encodings;

public abstract class CharacterSet {

	public abstract boolean inCharacterSet(char ch);
	
	public static CharacterSet getCharacterSet(String encoding) {
		if(encoding.equalsIgnoreCase("ASCII")) {
			return ASCIICharSet.getInstance();
		} else if(encoding.equalsIgnoreCase("US-ASCII")) {
			return ASCIICharSet.getInstance();
		} else if (encoding.equalsIgnoreCase("ISO-8859-1")) {
			return Latin1CharSet.getInstance();
		} else if (encoding.equalsIgnoreCase("ISO8859_1")) {
			return Latin1CharSet.getInstance();
		} else if (encoding.equalsIgnoreCase("UTF-8")) {
			return UnicodeCharSet.getInstance();
		} else if (encoding.equalsIgnoreCase("UTF8")) {
			return UnicodeCharSet.getInstance();
		} else if (encoding.equalsIgnoreCase("utf-16")) {
			return UnicodeCharSet.getInstance();
		} else if (encoding.equalsIgnoreCase("utf16")) {
			return UnicodeCharSet.getInstance();
		} else if (encoding.equalsIgnoreCase("iso-8859-2")) {
			return Latin2CharSet.getInstance();
		} else if (encoding.equalsIgnoreCase("ISO8859_2")) {
			return Latin2CharSet.getInstance();
		} else if (encoding.equalsIgnoreCase("KOI8-R")) {
			return KOI8RCharSet.getInstance();
		} else
			{return ASCIICharSet.getInstance();}
	}
}
