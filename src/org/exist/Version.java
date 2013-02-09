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
package org.exist;

import java.io.IOException;
import java.util.Properties;

import org.exist.xquery.functions.system.GetVersion;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public final class Version {

	private static final String NAME = "eXist";

	private static final String VERSION;
	private static final String BUILD;
	private static final String SVN_REVISION;
	
	static {
        final Properties properties = new Properties();
		try {
			properties.load(GetVersion.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
		} catch (final IOException e) {
		}
		
		VERSION 		= (String) properties.get("product-version");
		BUILD 			= (String) properties.get("product-build");
		SVN_REVISION 	= (String) properties.get("svn-revision");
	}
	
	public static String getProductName() {
		return NAME;
	}

	public static String getVersion() {
		return VERSION;
	}

	public static String getBuild() {
		return BUILD;
	}

	public static String getSvnRevision() {
		return SVN_REVISION;
	}
}
