/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist-db Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 */
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Cardinality;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Built-in function fn:lang().
 *
 */
public class FunLang extends Function {
	
	private static final String queryString = "(ancestor-or-self::*/@xml:lang)[position() = last()]";
	public CompiledXQuery query; 
	
    protected static final String FUNCTION_DESCRIPTION_1_PARAM =
        "Tests whether the language of the context item ";
	protected static final String FUNCTION_DESCRIPTION_2_PARAMS =

		"Tests whether the language of $node ";
	protected static final String FUNCTION_DESCRIPTION_BOTH =
        "as specified by xml:lang attributes is the " +
		"same as, or is a sublanguage of, the language specified by $lang. The " +
		"behavior of the function if the second argument is omitted is exactly the " +
		"same as if the context item (.) had been passed as the second argument. The " +
		"language of the argument node, or the context item if the second argument is " +
		"omitted, is determined by the value of the xml:lang attribute on the node, " + 
		"or, if the node has no such attribute, by the value of the xml:lang attribute " +
		"on the nearest ancestor of the node that has an xml:lang attribute. If there " +
		"is no such ancestor, then the function returns false().\n\n" +
		
		"The following errors may be raised: if the context item is undefined [err:XPDY0002]XP; " +
		"if the context item is not a node [err:XPTY0004]XP.\n\n" +

		"If $lang is the empty sequence it is interpreted as the zero-length string.";

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("lang", Function.BUILTIN_FUNCTION_NS),
			FUNCTION_DESCRIPTION_1_PARAM + FUNCTION_DESCRIPTION_BOTH,
			new SequenceType[] {
				 new FunctionParameterSequenceType("lang", Type.STRING, Cardinality.ZERO_OR_ONE, "The language code")
			},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ONE, "true if the language code matches, false otherwise")
		),
		new FunctionSignature(
				new QName("lang", Function.BUILTIN_FUNCTION_NS),
				FUNCTION_DESCRIPTION_2_PARAMS + FUNCTION_DESCRIPTION_BOTH,
				new SequenceType[] {
					 new FunctionParameterSequenceType("lang", Type.STRING, Cardinality.ZERO_OR_ONE, "The language code"),
					 new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE, "The node")
				},
				new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ONE, "true if the language code matches, false otherwise")
		)		
	};

	public FunLang(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		super.analyze(contextInfo);
		try {
			query = context.getBroker().getBrokerPool().getXQueryService().compile(context.getBroker(), context, queryString);
		} catch (final PermissionDeniedException e) {
			throw new XPathException(this, e);
		}		
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
		if (contextItem != null)
			{contextSequence = contextItem.toSequence();}
		
		if (getArgumentCount() == 2) 
            {contextSequence = getArgument(1).eval(contextSequence);}
		
		if (contextSequence == null)
			{throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context item");}
		
        Sequence result; 
		if (!(Type.subTypeOf(contextSequence.getItemType(), Type.NODE)))
			{throw new XPathException(this, ErrorCodes.XPTY0004, "Context item is not a node");}
        else {
			final String lang = getArgument(0).eval(contextSequence).getStringValue();
			
			Sequence seq = query.eval(contextSequence);
			if (seq.isEmpty()) {
				result = BooleanValue.FALSE ;
			} else if (seq.hasOne()) {
			    String langValue = seq.getStringValue();
	            
			    boolean include = lang.equalsIgnoreCase(langValue);
			    if (!include) {
				final int hyphen = langValue.indexOf('-');
				if (hyphen != Constants.STRING_NOT_FOUND) {
				    langValue = langValue.substring(0, hyphen);
				    include = lang.equalsIgnoreCase(langValue);
				}
			    }
			    result = new BooleanValue(include);
			}
            else 
            	{throw new XPathException(this, ErrorCodes.XPTY0004, "Sequence returned more than one item !");}
        }
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;          
	}

}
