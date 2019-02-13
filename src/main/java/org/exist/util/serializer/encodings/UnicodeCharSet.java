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

public class UnicodeCharSet extends CharacterSet {

	protected final static CharacterSet instance = new UnicodeCharSet();
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.encodings.CharacterSet#inCharacterSet(char)
	 */
	public boolean inCharacterSet(char ch) {
		// always return true since all characters are 
		// covered by this charset.
		return true;
	}

	public static CharacterSet getInstance() {
		return instance;
	}
}
