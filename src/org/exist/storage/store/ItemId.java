/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.storage.store;


class ItemId {
	
	public static final short RELOCATED_MASK = (short) 0x8000;
	public static final short LINK_MASK = (short) 0x4000;
	public static final short ID_MASK = (short) 0x3FFF;
	
	public final static short getId(short id) {
		return (short) (id & ID_MASK);
	}
	
	public final static short setIsRelocated(short id) {
		return (short)(id | RELOCATED_MASK);
	}
	
	public final static boolean isLink(short id) {
		return (id & LINK_MASK) != 0;
	}
	
	public final static short setIsLink(short id) {
		return (short) (id | LINK_MASK);
	}
	
	public final static boolean isRelocated(short id) {
		return (id & RELOCATED_MASK) != 0;
	} 
}