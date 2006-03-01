/*
 * eXist Context Module Extension GetVarFunction
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

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.XPathException;
import org.exist.xquery.XPathUtil;
import org.exist.xquery.XQueryContext;

/**
 * eXist Context Module Extension GetVarFunction 
 * 
 * The Variable Getting functionality of the eXist Context Module Extension that
 * allows variables to be retreived from the Context of the executing XQuery that have previously
 * been set with set-var()  
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-03-01
 * @version 1.3
 *
 * @see org.exist.xquery.Function
 */
public class GetVarFunction extends Function {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("get-var", ContextModule.NAMESPACE_URI, ContextModule.PREFIX),
			"get's a variable named $a from the current context",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
	);
		
	/**
	 * GetVarFunction Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public GetVarFunction(XQueryContext context)
	{
		super(context, signature);
	}

	/**
	 * evaluate the call to the xquery get-var() function,
	 * it is really the main entry point of this class
	 * 
	 * @param contextSequence	the Context Sequence to operate on
	 * @param contextItem		the Context Item to operate on
	 * @return		The sequence stored in the context by set-var()
	 * 
	 * @see org.exist.xquery.Function#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException
	{
		String name = getArgument(0).eval(contextSequence, contextItem).getStringValue();
		
		Object o = context.getXQueryContextVar(name);
		if(o == null)
		{
			return Sequence.EMPTY_SEQUENCE;
		}
		return XPathUtil.javaObjectToXPath(o, context);
	}
}
