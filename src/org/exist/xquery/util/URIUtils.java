/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-05 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Utilities for URI related functions
 * 
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */

public class URIUtils {
	
	public static String encodeForURI(String uriPart) throws UnsupportedEncodingException {
		String result = URLEncoder.encode(uriPart, "UTF-8");
		result.replaceAll("%23", "#");
		result.replaceAll("%2D", "-");
		result.replaceAll("%5F", "_");
		result.replaceAll("%2E", ".");
		result.replaceAll("%21", "!");
		result.replaceAll("%7E", "~");
		result.replaceAll("%2A", "*");
		result.replaceAll("%27", "'");
		result.replaceAll("%28", "(");
		result.replaceAll("%29", ")");		
		return result;
	}
	
	public static String iriToURI(String uriPart) throws UnsupportedEncodingException {
		String result = URLEncoder.encode(uriPart, "UTF-8");
		result.replaceAll("%23", "#");
		result.replaceAll("%2D", "-");
		result.replaceAll("%5F", "_");
		result.replaceAll("%2E", ".");
		result.replaceAll("%21", "!");
		result.replaceAll("%7E", "~");
		result.replaceAll("%2A", "*");
		result.replaceAll("%27", "'");
		result.replaceAll("%28", "(");
		result.replaceAll("%29", ")");
		result.replaceAll("%3B", ";");
		result.replaceAll("%2F", "/");
		result.replaceAll("%3F", "?");		
		result.replaceAll("%3A", ":");
		result.replaceAll("%40", "@");
		result.replaceAll("%26", "&");
		result.replaceAll("%3D", "=");		
		result.replaceAll("%2B", "+");
		result.replaceAll("%24", "$");
		result.replaceAll("%2C", ",");		
		result.replaceAll("%5B", "[");
		result.replaceAll("%5D", "])");		
		result.replaceAll("%25", "%");
		return result;
	}
	
	public static String escapeHtmlURI(String uri) throws UnsupportedEncodingException {
		String result = URLEncoder.encode(uri, "UTF-8");
		return result;
	}

}
