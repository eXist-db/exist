package org.exist.security;

import java.security.Principal;

/**
 * @author mdiggory
 */
public interface XmldbPrincipal extends Principal {

	public String getName();
	
	@Deprecated
	public String getPassword();
	
	public boolean hasRole(String role);

}