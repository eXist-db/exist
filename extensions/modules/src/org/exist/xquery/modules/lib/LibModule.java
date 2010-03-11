/*
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
 *  $Id: XSLFOModule.java 9672 2009-08-06 10:31:51Z ellefj $
 */

package org.exist.xquery.modules.lib;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 */
public class LibModule extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/lib";
	public final static String PREFIX = "lib";
    public final static String INCLUSION_DATE = "2010-03-10";
    public final static String RELEASED_IN_VERSION = "eXist-1.5";

	private final static FunctionDef[] functions = {
			new FunctionDef(GetLibFunction.signatures[0], GetLibFunction.class),
			new FunctionDef(GetLibInfoFunction.signatures[0], GetLibInfoFunction.class)};

	public LibModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for webstart envirenment";
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
    
}
