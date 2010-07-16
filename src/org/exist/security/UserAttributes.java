/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.security;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class UserAttributes {

	public static String _FIRTSNAME = "FirstName";
	public static String _LASTNAME = "LastName";
	public static String _FULLNAME = "FullName";
	public static String _EMAIL = "Email";
	public static String _COUNTRY = "Country"; 
	public static String _LANGUAGE = "Language"; 
	public static String _TIMEZONE = "Timezone"; 

	public static String FIRTSNAME = "http://axschema.org/namePerson/first";
	public static String LASTNAME = "http://axschema.org/namePerson/last";
	public static String FULLNAME = "http://axschema.org/namePerson";
	public static String EMAIL = "http://axschema.org/contact/email";
	public static String COUNTRY = "http://axschema.org/contact/country/home"; 
	public static String LANGUAGE = "http://axschema.org/pref/language";
	public static String TIMEZONE = "http://axschema.org/pref/timezone";
	
		
	//alias -> axschema url
    public static Map<String, String> alias = new HashMap<String, String>();

    static {
    	addAlias(_FIRTSNAME, FIRTSNAME);
    	addAlias(_LASTNAME, LASTNAME);
    	addAlias(_FULLNAME, FULLNAME);
    	addAlias(_EMAIL, EMAIL);
    	addAlias(_COUNTRY, COUNTRY);
    	addAlias(_LANGUAGE, LANGUAGE);
    	addAlias(_TIMEZONE, TIMEZONE);
    };
    
    private static void addAlias(String key, String value) {
    	alias.put(key, value);
    	alias.put(key.toLowerCase(), value);
    }

}
