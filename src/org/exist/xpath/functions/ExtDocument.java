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
package org.exist.xpath.functions;

import java.util.Iterator;

import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Dependency;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

/**
 * Implements eXist's document() function.
 * 
 * This will be replaced by XQuery's fn:doc() function.
 * 
 * @author wolf
 */
public class ExtDocument extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("document", BUILTIN_FUNCTION_NS),
            "Includes one or more documents " +
            "into the input sequence. This function is specific to eXist and " +
            "will be replaced with the corresponding fn:doc function. Currently, " +
            "eXist interprets each argument as an absolute path pointing to a " +
            "document in the database, as for example, '/db/shakespeare/plays/hamlet.xml'. " +
            "If no arguments are specified, the function will load all documents in the " +
            "database.",
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			true);

	/**
	 * @param context
	 * @param signature
	 */
	public ExtDocument(StaticContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		DocumentSet docs = new DocumentSet();
		getParent().resetState();
		if (getArgumentCount() == 0)
			context.getBroker().getAllDocuments(docs);
		else {
			for(int i = 0; i < getArgumentCount(); i++) {
				Sequence seq = getArgument(i).eval(contextSequence, contextItem);
				for(SequenceIterator j = seq.iterate(); j.hasNext(); ) {
					String next = j.nextItem().getStringValue();
					if(next.length() == 0)
						throw new XPathException("Invalid argument to fn:doc function: empty string is not allowed here.");
					if(next.charAt(0) != '/')
						next = context.getBaseURI() + '/' + next;
					try {
						DocumentImpl doc = (DocumentImpl) context.getBroker().getDocument(next);
						docs.add(doc);
					} catch (PermissionDeniedException e) {
						throw new XPathException("Permission denied: unable to load document " + next);
					}
				}
			}
		}
		NodeSet result = new ExtArrayNodeSet(1);
		for (Iterator i = docs.iterator(); i.hasNext();) {
			result.add(new NodeProxy((DocumentImpl) i.next(), -1));
		}
		return result;
	}
}
