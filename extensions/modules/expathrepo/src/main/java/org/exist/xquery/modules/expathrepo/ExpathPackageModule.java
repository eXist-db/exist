/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007-2010 The eXist Project
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
package org.exist.xquery.modules.expathrepo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.*;

import java.util.List;
import java.util.Map;


/**
 * XQuery Extension module for expath expathrepo
 *
 * @author <a href="mailto:jim.fuller@exist-db.org">James Fuller</a>
 * @author Wolfgang Meier
 * @author cutlass
 * @version 1.0
 */
public class ExpathPackageModule extends AbstractInternalModule {

    private final static Logger logger = LogManager.getLogger(ExpathPackageModule.class);

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/repo";

    public final static String PREFIX = "repo";
    public final static String INCLUSION_DATE = "2010-07-27";
    public final static String RELEASED_IN_VERSION = "eXist-2.0";
    
    private final static FunctionDef[] functions = {
    	new FunctionDef(Deploy.signatures[0], Deploy.class),
    	new FunctionDef(Deploy.signatures[1], Deploy.class),
        new FunctionDef(Deploy.signatures[2], Deploy.class),
        new FunctionDef(Deploy.signatures[3], Deploy.class),
        new FunctionDef(Deploy.signatures[4], Deploy.class),
        new FunctionDef(Deploy.signatures[5], Deploy.class),
        new FunctionDef(Deploy.signatures[6], Deploy.class),
        new FunctionDef(ListFunction.signature, ListFunction.class),
        new FunctionDef(InstallFunction.signatureInstall, InstallFunction.class),
        new FunctionDef(InstallFunction.signatureInstallFromDB, InstallFunction.class),
        new FunctionDef(RemoveFunction.signature, RemoveFunction.class),
        new FunctionDef(GetResource.signature, GetResource.class),
        new FunctionDef(GetAppRoot.signature, GetAppRoot.class)
    };

    public ExpathPackageModule(Map<String, List<?>> parameters) throws XPathException {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    public String getDescription() {
        return "A module for working with expath repository manager";
    }

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
