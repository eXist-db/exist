package org.exist.xmldb;

import org.exist.security.Permission;

public class DocumentProxy implements Comparable {

	private String name;
	private String type;
	private Permission permissions = null;
	
	public DocumentProxy(String name) {
		this(name, "XMLResource");
	}
	
	public DocumentProxy(String name, String type) {
		this.name = name;
		this.type = type;
	}
	
	public void setPermissions(Permission perms) {
		permissions = perms;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public String getType() {
		return type;
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
