/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
package org.exist.xquery.functions.system;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

public class SystemModule extends AbstractInternalModule {

	public static final String NAMESPACE_URI = "http://exist-db.org/xquery/system";
	
	public static final String PREFIX = "system";

    public static final FunctionDef[] functions = {
            new FunctionDef(FtIndexLookup.signature, FtIndexLookup.class),

            new FunctionDef(CountInstances.countInstancesMax, CountInstances.class),
            new FunctionDef(CountInstances.countInstancesActive, CountInstances.class),
            new FunctionDef(CountInstances.countInstancesAvailable, CountInstances.class),
            new FunctionDef(GetMemory.getMemoryMax, GetMemory.class),
            new FunctionDef(GetMemory.getMemoryTotal, GetMemory.class),
            new FunctionDef(GetMemory.getMemoryFree, GetMemory.class),
            new FunctionDef(GetVersion.signature, GetVersion.class),
            new FunctionDef(GetBuild.signature, GetBuild.class),
            new FunctionDef(GetRevision.signature, GetRevision.class),
            new FunctionDef(GetExistHome.signature, GetExistHome.class),
            new FunctionDef(Shutdown.signatures[0], Shutdown.class),
            new FunctionDef(Shutdown.signatures[1], Shutdown.class),
            new FunctionDef(GetModuleLoadPath.signature, GetModuleLoadPath.class),
            new FunctionDef(TriggerSystemTask.signature, TriggerSystemTask.class),
            new FunctionDef(AsUser.signature, AsUser.class),
            new FunctionDef(GetIndexStatistics.signature, GetIndexStatistics.class),
            new FunctionDef(UpdateStatistics.signature, UpdateStatistics.class)
    };
	
	public SystemModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "Functions to retrieve information about eXist and the system.";
	}
}
