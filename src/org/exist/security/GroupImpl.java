/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2003-2010 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.security;

import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

public class GroupImpl implements Comparable<Object>, Group {

	private String name;
	private int id;
	
	public GroupImpl(String name, int id) {
		this.name = name;
		this.id = id;
	}

	public GroupImpl(Element element) throws DatabaseConfigurationException {
		this.name = element.getAttribute("name");
		String groupId = element.getAttribute("id");
		if(groupId == null)
			throw new DatabaseConfigurationException("attribute id missing");
		try {
			this.id = Integer.parseInt(groupId);
		} catch(NumberFormatException e) {
			throw new DatabaseConfigurationException("illegal user id: " + groupId);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.security.Group#getName()
	 */
	public String getName() {
		return name;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.security.Group#getId()
	 */
	public int getId() {
		return id;
	}

	public int compareTo(Object other) {
		if(!(other instanceof GroupImpl))
			throw new IllegalArgumentException("wrong type");
		return name.compareTo(((GroupImpl)other).name);
	}

	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("<group name=\"");
		buf.append(name);
		buf.append("\" id=\"");
		buf.append(Integer.toString(id));
		buf.append("\"/>");
		return buf.toString();
	}
}
