/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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
 *  $Id$
 */
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 *
 */
public class FunQName extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("QName", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:QName with the namespace URI given in $a. If $a is the empty " +
			"string or the empty sequence, it represents 'no namespace'. The prefix in $b " +
			"is retained in the returned xs:QName value. The local name in the result is " +
			"taken from the local part of $b",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.QNAME, Cardinality.EXACTLY_ONE));
	
	/**
	 * @param context
	 */
	public FunQName(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }  

        //TODO : currently useless (but for empty sequences) since the type is forced :-(
        if (!args[0].isEmpty() && args[0].getItemType() != Type.STRING)
        	throw new XPathException("err:XPTY0004: namespace URI is of type '" + 
        			Type.getTypeName(args[0].getItemType()) + "', 'xs:string' expected");
        String namespace;
		if (args[0].isEmpty())
			namespace = "";
		else
			namespace = args[0].getStringValue();
		
		String param = args[1].getStringValue();
		
		String prefix = null;
		String localName = null;		
		try {
			prefix = QName.extractPrefix(param);
			localName = QName.extractLocalName(param);
        } catch (IllegalArgumentException e) {
        	throw new XPathException("err:FOCA0002: invalid lexical form of either prefix or local name.");
		}
		
		if ((prefix != null && prefix.length() > 0) && (namespace == null || namespace.length() == 0))
        	throw new XPathException("err:FOCA0002: non-empty namespace prefix with empty namespace URI");
		
		QName qname = new QName(localName, namespace, prefix);
        if (prefix != null && namespace != null) {
            if (context.getURIForPrefix(prefix) == null) {
            	//TOCHECK : context.declareInScopeNamespace(prefix, uri) ?            	
                context.declareNamespace(prefix, namespace);
            }


            //context.declareInScopeNamespace(prefix, namespace);
        }
		Sequence result = new QNameValue(context, qname);

        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;
	}
}
