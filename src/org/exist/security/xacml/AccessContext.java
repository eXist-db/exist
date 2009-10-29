/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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

package org.exist.security.xacml;

/**
 * This class represents the context from which an access is made.
 */
public final class AccessContext
{
	/**
	 * The postfix for all internal accesses.
	 */
	public static final String INTERNAL = "(internal)";
	/**
	 * This represents when access is attempted as a result of a trigger.
	 */
	public static final AccessContext TRIGGER = new AccessContext("Trigger");
	/**
	 * This represents when access is made through SOAP.
	 */
	public static final AccessContext SOAP = new AccessContext("SOAP");
	/**
	 * This represents when access is made through XML:DB.
	 */
	public static final AccessContext XMLDB = new AccessContext("XML:DB");
	/**
	 * This represents when access is made through XSLT
	 */
	public static final AccessContext XSLT = new AccessContext("XSLT");
	/**
	 * This represents when access is made through XQJ
	 */
	public static final AccessContext XQJ = new AccessContext("XQJ");
	/**
	 * The context for access through the REST-style interface. 
	 */
	public static final AccessContext REST = new AccessContext("REST");
	/**
	 * The context for remote access over XML-RPC.
	 */
	public static final AccessContext XMLRPC = new AccessContext("XML-RPC");

	/**
	 * The context for access through WEBDAV
	 */
	public static final AccessContext WEBDAV = new AccessContext("WebDAV");
	/**
	 * The context for access internally when the access is not made by any of the
	 * other contexts.  This should only be used if all actions
	 * are completely trusted, that is, no user input should be directly included
	 * in a query or any similar case. 
	 */
	public static final AccessContext INTERNAL_PREFIX_LOOKUP = new AccessContext("Prefix lookup " + INTERNAL);
	/**
	 * The context for trusted validation queries. 
	 */
	public static final AccessContext VALIDATION_INTERNAL = new AccessContext("Validation " + INTERNAL);
	/**
	 * The context for JUnit tests that directly make access not through the other
	 * contexts.
	 */
	public static final AccessContext TEST = new AccessContext("JUnit test");

	/**
	 * The context for evaluating XInclude paths.
	 */
	public static final AccessContext XINCLUDE = new AccessContext("XInclude");

    public static final AccessContext INITIALIZE = new AccessContext("Initialize " + INTERNAL);
    
    private final String value;
	
	private AccessContext()
	{
		throw new RuntimeException("The empty constructor is not supported.");
	}
	private AccessContext(String value)
	{
		if(value == null || value.length() == 0)
			throw new NullPointerException("Access context value cannot be null");
		this.value = value;
	}
	
	public String toString()
	{
		return value;
	}
}
