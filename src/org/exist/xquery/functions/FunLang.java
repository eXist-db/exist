/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2006 the eXist team
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
 * $Id$
 */

package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Cardinality;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Built-in function fn:lang().
 *
 */
public class FunLang extends Function {
	
	public static String queryString = "(ancestor-or-self::*/@xml:lang)[last()]";
	public CompiledXQuery query; 

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("lang", Function.BUILTIN_FUNCTION_NS),
			"Returns true if the context items xml:lang attribute is equal " +
			"to the value of $a, false otherwise.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.BOOLEAN, Cardinality.ONE)),
		new FunctionSignature(
				new QName("lang", Function.BUILTIN_FUNCTION_NS),
				"Returns true if the context items xml:lang attribute is equal " +
				"to the value of $a, false otherwise.",
				new SequenceType[] {
					 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
					 new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)},
				new SequenceType(Type.BOOLEAN, Cardinality.ONE))		
		};

	public FunLang(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		super.analyze(contextInfo);
		query = context.getBroker().getXQueryService().compile(context, queryString);		
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		
		if (getArgumentCount() == 2) 
            contextSequence = getArgument(1).eval(contextSequence);
		
		if (contextSequence == null)
			throw new XPathException(getASTNode(), "XPDY0002: Undefined context item");
		
        Sequence result; 
		if (!(Type.subTypeOf(contextSequence.getItemType(), Type.NODE)))
			throw new XPathException(getASTNode(), "XPDY0004: Context item is not a node");
        else {
			String lang = getArgument(0).eval(contextSequence).getStringValue();
			Sequence seq = query.eval(contextSequence);
			if (seq.isEmpty()) {
				result = BooleanValue.FALSE ;   
			} else if (seq.hasOne()) {
				String langValue = seq.getStringValue();
	            boolean include = lang.equalsIgnoreCase(langValue);
				if (!include) {
	                int hyphen = langValue.indexOf('-');
					if (hyphen != Constants.STRING_NOT_FOUND) {
						langValue = langValue.substring(0, hyphen);
						include = lang.equalsIgnoreCase(langValue);
					}				
				}
				result = new BooleanValue(include);
			}
            else 
            	throw new XPathException(getASTNode(), "XPDY0004: Sequence returned more than one item !");
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;          
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}
}
