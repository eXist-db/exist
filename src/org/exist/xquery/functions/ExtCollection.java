/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import java.util.Iterator;

import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class ExtCollection extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("collection", BUILTIN_FUNCTION_NS),
            "Includes the documents contained in the specified collection " +
            "into the input sequence. eXist interprets the arguments as absolute paths " +
            "pointing to database collections, as for example, '/db/test'. Documents " +
            "located in subcollections of a collection are included into the input " +
            "set.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			true);

	private boolean includeSubCollections = false;
	
	/**
	 * @param context
	 * @param signature
	 */
	public ExtCollection(XQueryContext context) {
		this(context, signature, true);
	}

	public ExtCollection(XQueryContext context, FunctionSignature signature, boolean inclusive) {
		super(context, signature);
		includeSubCollections = inclusive;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		DocumentSet docs = new DocumentSet();
		for (int i = 0; i < getArgumentCount(); i++) {
			Sequence seq =
				getArgument(i).eval(contextSequence, contextItem);
			for (SequenceIterator j = seq.iterate(); j.hasNext();) {
				String next = j.nextItem().getStringValue();
				try {
					context.getBroker().getDocumentsByCollection(next, docs, includeSubCollections);
				} catch (PermissionDeniedException e) {
					throw new XPathException(
						"Permission denied: unable to load document " + next);
				}
			}
		}
		NodeSet result = new ExtArrayNodeSet(docs.getLength(), 1);
		for (Iterator i = docs.iterator(); i.hasNext();) {
			result.add(new NodeProxy((DocumentImpl) i.next(), -1));
		}
		return result;
	}
}
