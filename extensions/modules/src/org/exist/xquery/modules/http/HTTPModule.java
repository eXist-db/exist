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
 *  $Id: ExampleModule.java 1173 2005-04-20 11:15:18Z wolfgang_m $
 */
package org.exist.xquery.modules.http;

import org.apache.commons.httpclient.Cookie;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk
 */
public class HTTPModule extends AbstractInternalModule
{
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/http";
	
	public final static String PREFIX = "http";
	
	public final static String HTTP_MODULE_PERSISTENT_COOKIES = "_eXist_http_module_cookies";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(POSTFunction.signature, POSTFunction.class)
	};
	
	public HTTPModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for performing HTTP requests";
	}
	
	/**
	 * Merges two cookie arrays together
	 * 
	 * If cookies are equal (same name, path and comain) then the incoming cookie is favoured over the current cookie
	 * 
	 * @param current	The cookies already known
	 * @param incoming	The new cookies
	 * 
	 * 
	 */
	protected static Cookie[] mergeCookies(Cookie[] current, Cookie[] incoming)
	{
		if(current == null)
		{
			if(incoming == null)
				return null;
			else if(incoming.length == 0)
				return null;
			else
				return incoming;
		}
		
		if(incoming == null)
			return current;
		
		
		java.util.HashMap replacements = new java.util.HashMap();
		java.util.Vector additions = new java.util.Vector();
		
		for(int i = 0; i < incoming.length; i++)
		{
			boolean cookieExists = false;
			
			for(int c = 0; c < current.length; i++)
			{
				if(current[c].equals(incoming[i]))
				{
					//replacement				
					replacements.put(c, incoming[i]);
					cookieExists = true;
					break;
				}
			}
			
			if(!cookieExists)
			{
				//add
				additions.add(incoming[i]);
			}
		}
		
		Cookie[] merged = new Cookie[current.length + additions.size()];
		//resolve replacements/copies
		for(int c = 0; c < current.length; c++)
		{
			if(replacements.containsKey(c))
			{
				//replace
				merged[c] = (Cookie)replacements.get(c);
			}
			else
			{
				//copy
				merged[c] = current[c];
			}
		}
		//resolve additions
		for(int a = 0; a < additions.size(); a++)
		{
			int offset = current.length + a;
			merged[offset] = (Cookie)additions.get(a);
		}
		
		return merged;
	}
}
