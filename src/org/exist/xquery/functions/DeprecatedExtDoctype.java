/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
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

import org.apache.log4j.Logger;

import org.exist.dom.*;
import org.exist.numbering.NodeId;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import java.util.Iterator;

/**
 * @author wolf
 */
public class DeprecatedExtDoctype extends Function {
	protected static final Logger logger = Logger.getLogger(DeprecatedExtDoctype.class);
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("doctype", Function.BUILTIN_FUNCTION_NS),
			"Returns the document nodes of the documents based on the DOCTYPE.",
			new SequenceType[] {
				 new FunctionParameterSequenceType("doctype", Type.STRING, Cardinality.ONE_OR_MORE, "one or more DOCTYPE names"),
			},
			new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the document nodes matching the DOCTYPE names"),
			"This function is eXist-specific and deprecated. It should not be in the standard functions namespace. Please " +
			"use util:doctype() instead."
		);

	/**
	 * @param context
	 */
	public DeprecatedExtDoctype(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        logger.error("Use of deprecated function fn:doctype(). " +
                     "It will be removed soon. Please " +
                     "use util:doctype() instead.");
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		MutableDocumentSet docs = new DefaultDocumentSet();
		for (int i = 0; i < getArgumentCount(); i++) {
			Sequence seq = getArgument(i).eval(contextSequence, contextItem);
			for (SequenceIterator j = seq.iterate(); j.hasNext();) {
				String next = j.nextItem().getStringValue();
				context.getBroker().getXMLResourcesByDoctype(next, docs);
			}
		}
        
		NodeSet result = new ExtArrayNodeSet(1);
		for (Iterator i = docs.getDocumentIterator(); i.hasNext();) {
			result.add(new NodeProxy((DocumentImpl) i.next(), NodeId.DOCUMENT_NODE));
		}
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);        
        
        return result;
	}

}
