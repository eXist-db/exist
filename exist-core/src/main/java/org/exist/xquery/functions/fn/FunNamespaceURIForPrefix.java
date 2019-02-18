/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
package org.exist.xquery.functions.fn;

import java.util.Map;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunNamespaceURIForPrefix extends BasicFunction {
	
	protected static final String FUNCTION_DESCRIPTION =

		"Returns the namespace URI of one of the in-scope namespaces " +
		"for $element, identified by its namespace prefix.\n\n" +
		"If $element has an in-scope namespace whose namespace prefix " +
		"is equal to $prefix, it returns the namespace URI of that namespace. " +
		"If $prefix is the zero-length string or the empty sequence, it " +
		"returns the namespace URI of the default (unnamed) namespace. " +
		"Otherwise, it returns the empty sequence.\n\n" +

		"Prefixes are equal only if their Unicode code points match exactly.";


	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("namespace-uri-for-prefix", Function.BUILTIN_FUNCTION_NS),
			FUNCTION_DESCRIPTION,
			new SequenceType[] { 
				new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.ZERO_OR_ONE, "The namespace prefix"),
				new FunctionParameterSequenceType("element", Type.ELEMENT, Cardinality.EXACTLY_ONE, "The element")
			},
			new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_ONE, "the namespace URI"));
	
	public FunNamespaceURIForPrefix(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)	throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
        }
        
		String prefix;
		if (args[0].isEmpty())
			{prefix = "";}
		else
			{prefix = args[0].itemAt(0).getStringValue();}
        
		String namespace;
		if ("xml".equals(prefix)) {
			namespace = Namespaces.XML_NS;
		} else {
			final Map<String, String> prefixes = FunInScopePrefixes.collectPrefixes(context, (NodeValue) args[1].itemAt(0));
			
			namespace = prefixes.get(prefix);
		}

		Sequence result;
		if (namespace == null)
            {result = Sequence.EMPTY_SEQUENCE;}            
        else
            {result = new AnyURIValue(namespace);}
            
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;  
        
	}
}
