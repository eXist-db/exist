/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions;

import org.exist.dom.*;
import org.exist.numbering.NodeId;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.Iterator;

/**
 * @author wolf
 */
public class ExtDoctype extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("doctype", Function.BUILTIN_FUNCTION_NS),
			"Returns the document nodes of the documents whose DOCTYPE is given by $a.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE),
			},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			"This function is eXist-specific and should not be in the standard functions namespace. Please " +
			"use util:doctype instead."
		);

	/**
	 * @param context
	 */
	public ExtDoctype(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
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
