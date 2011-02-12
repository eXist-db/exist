/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
package org.exist.security.internal;

import org.exist.security.Credential;
import org.exist.security.MessageDigester;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Password implements Credential {

	public static String MD5 = "MD5";
	
	//private
	public String pw;
	private String algorithm = "";
	
	public Password(String password) {
		if (password == null)
			this.pw = null;
		else if (password.startsWith("{MD5}")) {
			this.pw = password.substring(5);
			this.algorithm = MD5;
		} else {
			this.pw = crypt(password);
			this.algorithm = MD5;
		}
	}
	
	private String crypt(String str) {
		return MessageDigester.md5(str, true);
	}

    public boolean check(Object credentials) {
    	
    	if (credentials == this) return true;
    	
    	//workaround old style, remove -shabanovd 
    	if (credentials == null)
    		credentials = "";
    	
    	if (credentials instanceof Password || credentials instanceof String) {
			return equals(credentials);
		}
    	
    	if (credentials instanceof char[]) {
			return equals(String.valueOf((char[]) credentials));
		}
    	
    	return false;
    	
    }

    public boolean equals(Object obj) {
    	
    	if (obj == this) return true;
    	
    	if (obj == null) return false;
    	
    	if (obj instanceof Password) {
			Password p = (Password) obj;
			return (pw == p.pw || (pw != null && pw.equals(p.pw)));
		}
    	
    	if (obj instanceof String) {
			return (crypt((String) obj)).equals(pw);
		}
    	
    	return false;
    }
    
    public String toString() {
    	return "{"+algorithm+"}"+pw;
    }
}
