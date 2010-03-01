/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-09 The eXist Project
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
 * $Id$
 */
package org.exist.xquery.modules.svn;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * Module function definitions for subversion (svn) module.
 *
 * @author wolf
 * @author ljo
 *
 */
public class SVNModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/svn";

	public final static String PREFIX = "svn";
    public final static String INCLUSION_DATE = "2008-04-05";
    public final static String RELEASED_IN_VERSION = "eXist-1.4";


	private final static FunctionDef[] functions = {
            new FunctionDef(SVNLog.signature, SVNLog.class),
            new FunctionDef(SVNLatestRevision.signature, SVNLatestRevision.class)
	};

	public SVNModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for interaction with subversion (svn) repositories.";
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

}
