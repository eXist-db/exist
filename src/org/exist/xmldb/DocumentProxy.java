/*
 * DocumentProxy.java - Aug 3, 2003
 * 
 * @author wolf
 */
package org.exist.xmldb;

import org.exist.security.Permission;

public class DocumentProxy implements Comparable {

	private String name;
	private Permission permissions = null;
	
	public DocumentProxy(String name) {
		this.name = name;
	}
	
	public void setPermissions(Permission perms) {
		permissions = perms;
	}
	
	public String getName() {
		return name;
	}
	
	public Permission getPermissions() {
		return permissions;
	}
	
	
	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		return name.compareTo(((DocumentProxy)o).name);
	}

}
