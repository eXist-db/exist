/*
 * eXist Context Module Extension
 *
 * Released under the BSD License
 *
 * Copyright (c) 2006, Adam retter <adam.retter@devon.gov.uk>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 		Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *  	Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *  	Neither the name of the Devon Portal Project nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 *  
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 *  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 *  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  $Id$
 */

package org.exist.xquery.modules.context;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * eXist Context Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows operations
 * on the Context of the executing XQuery to be performed
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-03-01
 * @version 1.3
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[])
 */
public class ContextModule extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/context";
	
	public final static String PREFIX = "context";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(SetSerializerFunction.signature, SetSerializerFunction.class),
		new FunctionDef(SetVarFunction.signature, SetVarFunction.class),
		new FunctionDef(GetVarFunction.signature, GetVarFunction.class)
	};
	
	public ContextModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for performing operations in the context of the current xquery";
	}
}
