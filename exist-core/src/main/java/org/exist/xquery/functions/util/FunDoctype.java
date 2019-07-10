/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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
 *  $Id: ExtDoctype.java 4403 2006-09-27 12:27:37Z wolfgang_m $
 */
package org.exist.xquery.functions.util;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.numbering.NodeId;
import org.exist.util.LockException;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.Iterator;
import org.exist.security.PermissionDeniedException;

/**
 * @author wolf
 */
public class FunDoctype extends Function {
	
	protected static final Logger logger = LogManager.getLogger(FunDoctype.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("doctype", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the document nodes of the documents with the given DOCTYPE(s).",
			new SequenceType[] {
				 new FunctionParameterSequenceType("doctype", Type.STRING, Cardinality.ONE_OR_MORE, "The DOCTYPE of the documents to find")},
			new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the document nodes"),
			true);

	public FunDoctype(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
        final MutableDocumentSet docs = new DefaultDocumentSet();
        for (int i = 0; i < getArgumentCount(); i++) {
            final Sequence seq = getArgument(i).eval(contextSequence, contextItem);
            for (final SequenceIterator j = seq.iterate(); j.hasNext();) {
                final String next = j.nextItem().getStringValue();
                try {
                    context.getBroker().getXMLResourcesByDoctype(next, docs);
                } catch(final PermissionDeniedException | LockException e) {
                    LOG.error(e.getMessage(), e);
                    throw new XPathException(this, e);
                }
            }
        }

        final NodeSet result = new ExtArrayNodeSet(1);
        for (final Iterator<DocumentImpl> i = docs.getDocumentIterator(); i.hasNext();) {
                result.add(new NodeProxy(i.next(), NodeId.DOCUMENT_NODE));
        }
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}        
        
        return result;
	}

}
