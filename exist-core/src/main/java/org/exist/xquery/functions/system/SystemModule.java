/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xquery.functions.system;

import java.util.List;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDSL;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

/**
 * Module function definitions for system module.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author ljo
 */
public class SystemModule extends AbstractInternalModule {

	public static final String NAMESPACE_URI = "http://exist-db.org/xquery/system";
	public static final String PREFIX = "system";
    public final static String INCLUSION_DATE = "2005-06-15";
    public final static String RELEASED_IN_VERSION = "eXist-1.0";

    public static final FunctionDef[] functions = {
            new FunctionDef(FnExport.signatures[0], FnExport.class),
            new FunctionDef(FnExport.signatures[1], FnExport.class),
            new FunctionDef(FnImport.signatures[0], FnImport.class),
            new FunctionDef(FnImport.signatures[1], FnImport.class),

            new FunctionDef(CountInstances.countInstancesMax, CountInstances.class),
            new FunctionDef(CountInstances.countInstancesActive, CountInstances.class),
            new FunctionDef(CountInstances.countInstancesAvailable, CountInstances.class),
            new FunctionDef(GetMemory.getMemoryMax, GetMemory.class),
            new FunctionDef(GetMemory.getMemoryTotal, GetMemory.class),
            new FunctionDef(GetMemory.getMemoryFree, GetMemory.class),
			new FunctionDef(GetProductName.signature, GetProductName.class),
            new FunctionDef(GetVersion.signature, GetVersion.class),
            new FunctionDef(GetBuild.signature, GetBuild.class),
            new FunctionDef(GetRevision.signature, GetRevision.class),
            new FunctionDef(GetExistHome.signature, GetExistHome.class),
            new FunctionDef(Shutdown.signatures[0], Shutdown.class),
            new FunctionDef(Shutdown.signatures[1], Shutdown.class),
            new FunctionDef(GetModuleLoadPath.signature, GetModuleLoadPath.class),
            new FunctionDef(TriggerSystemTask.signature, TriggerSystemTask.class),
            new FunctionDef(AsUser.FS_AS_USER, AsUser.class),
			new FunctionDef(AsUser.FS_FUNCTION_AS_USER, AsUser.class),
            new FunctionDef(GetIndexStatistics.signature, GetIndexStatistics.class),
            new FunctionDef(UpdateStatistics.signature, UpdateStatistics.class),
            new FunctionDef(GetRunningXQueries.signature, GetRunningXQueries.class),
            new FunctionDef(KillRunningXQuery.signatures[0], KillRunningXQuery.class),
            new FunctionDef(KillRunningXQuery.signatures[1], KillRunningXQuery.class),
            new FunctionDef(GetRunningJobs.signature, GetRunningJobs.class),
            new FunctionDef(GetScheduledJobs.signature, GetScheduledJobs.class),
            new FunctionDef(Restore.signature, Restore.class),
            new FunctionDef(FunctionTrace.signatures[0], FunctionTrace.class),
            new FunctionDef(FunctionTrace.signatures[1], FunctionTrace.class),
            new FunctionDef(FunctionTrace.signatures[2], FunctionTrace.class),
            new FunctionDef(FunctionTrace.signatures[3], FunctionTrace.class),
            new FunctionDef(FunctionTrace.signatures[4], FunctionTrace.class),
            new FunctionDef(GetUptime.signature, GetUptime.class),
            new FunctionDef(FunctionAvailable.signature, FunctionAvailable.class),
            
            new FunctionDef(ClearXQueryCache.signature, ClearXQueryCache.class)
    };
	
	public SystemModule(Map<String, List<? extends Object>> parameters) {
		super(functions, parameters);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for retrieving information about eXist and the system.";
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

	static FunctionSignature functionSignature(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
		return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, paramTypes);
	}

	static FunctionSignature[] functionSignatures(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType[][] variableParamTypes) {
		return FunctionDSL.functionSignatures(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, variableParamTypes);
	}
}
