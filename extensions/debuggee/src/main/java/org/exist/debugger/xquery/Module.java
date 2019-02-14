/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
package org.exist.debugger.xquery;

import java.util.List;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.ErrorCodes.ErrorCode;

/**
 * eXist debugger xquery module
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class Module extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://exist-db.org/debugger";
	public final static String PREFIX = "dbgr";
	private final static String RELEASED_IN_VERSION = "eXist-1.5";
	private final static String DESCRIPTION = "Module for interacting with the debuggee";

    public static ErrorCode DEBUG001 = new DebugErrorCode("DEBUG001", "Debugger command error.");

    public static class DebugErrorCode extends ErrorCode {
        private DebugErrorCode(String code, String description) {
            super(new QName(code, NAMESPACE_URI, PREFIX), description);
        }
    }

    private final static FunctionDef[] functions = {
		new FunctionDef(Init.signatures[0], Init.class),

		new FunctionDef(BreakpointList.signatures[0], BreakpointList.class),
		new FunctionDef(BreakpointSet.signatures[0], BreakpointSet.class),

		new FunctionDef(Run.signatures[0], Run.class),
		new FunctionDef(StepInto.signatures[0], StepInto.class),
		new FunctionDef(StepOut.signatures[0], StepOut.class),
		new FunctionDef(StepOver.signatures[0], StepOver.class),
		new FunctionDef(Stop.signatures[0], Stop.class),
		
		new FunctionDef(StackGet.signatures[0], StackGet.class),
		new FunctionDef(Source.signatures[0], Source.class),

		new FunctionDef(ContextGet.signatures[0], ContextGet.class)
	};

	public Module(Map<String, List<? extends Object>> parameters) {
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

	@Override
	public String getDescription() {
		return DESCRIPTION;
	}

	@Override
	public String getReleaseVersion() {
		return RELEASED_IN_VERSION;
	}
}