/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.http.webdav.methods;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.http.webdav.WebDAVMethod;
import org.exist.security.User;

/**
 * @author wolf
 */
public class Options implements WebDAVMethod {
	
	public void process(User user, HttpServletRequest request,
			HttpServletResponse response, String path) throws ServletException, IOException {
		response.addHeader("DAV", "1");
		response.addHeader("Allow", "OPTIONS, GET, HEAD, PUT, PROPFIND,MKCOL");
		
		// MS specific
		response.addHeader("MS-Author-Via", "DAV");
	}
}
