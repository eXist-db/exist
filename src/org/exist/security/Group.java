/*
 * Group.java - Mar 14, 2003
 * 
 * @author wolf
 */
package org.exist.security;

import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

public class Group implements Comparable {

	private String name;
	private int id;
	
	public Group(String name, int id) {
		this.name = name;
		this.id = id;
	}

	public Group(Element element) throws DatabaseConfigurationException {
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
	
	public String getName() {
		return name;
	}
	
	public int getId() {
		return id;
	}

	public int compareTo(Object other) {
		if(!(other instanceof Group))
			throw new IllegalArgumentException("wrong type");
		return name.compareTo(((Group)other).name);
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
