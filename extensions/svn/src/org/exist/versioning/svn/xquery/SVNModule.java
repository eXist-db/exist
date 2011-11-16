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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.versioning.svn.xquery;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * Module function definitions for subversion (svn) module.
 *
 * @author <a href="mailto:amir.akhmedov@gmail.com">Amir Akhmedov</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SVNModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/versioning/svn";

	public final static String PREFIX = "subversion";
    public final static String INCLUSION_DATE = "2010-05-06";
    public final static String RELEASED_IN_VERSION = "eXist-1.5";


	private final static FunctionDef[] functions = {
        	new FunctionDef(SVNAdd.signature, SVNAdd.class),
            new FunctionDef(SVNCleanup.signature, SVNCleanup.class),
            new FunctionDef(SVNCheckOut.signature[0], SVNCheckOut.class),
            new FunctionDef(SVNCheckOut.signature[1], SVNCheckOut.class),
            new FunctionDef(SVNCommit.signature, SVNCommit.class),
            new FunctionDef(SVNDelete.signature, SVNDelete.class),
            new FunctionDef(SVNInfo.signature, SVNInfo.class),
            new FunctionDef(SVNLatestRevision.signature, SVNLatestRevision.class),
            new FunctionDef(SVNList.signature, SVNList.class),
            new FunctionDef(SVNLock.signature, SVNLock.class),
            new FunctionDef(SVNLog.signature, SVNLog.class),
            new FunctionDef(SVNStatus.signature, SVNStatus.class),
            new FunctionDef(SVNUnlock.signature, SVNUnlock.class),
            new FunctionDef(SVNUpdate.signature, SVNUpdate.class),
            new FunctionDef(SVNRevert.signature, SVNRevert.class)
	};

	public SVNModule(Map<String, List<? extends Object>> parameters) {
		super(functions, parameters);
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
