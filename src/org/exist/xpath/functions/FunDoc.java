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

import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Dependency;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.XQueryContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

/**
 * Implements the built-in fn:doc() function.
 * 
 * This will be replaced by XQuery's fn:doc() function.
 * 
 * @author wolf
 */
public class FunDoc extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("doc", BUILTIN_FUNCTION_NS),
			"Includes one or more documents "
				+ "into the input sequence. Currently, "
				+ "eXist interprets each argument as a path pointing to a "
				+ "document in the database, as for example, '/db/shakespeare/plays/hamlet.xml'. "
				+ "If the path is relative, it is resolved relative to the base URI property from the static context.",
			new SequenceType[] { new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE));

	/**
	 * @param context
	 * @param signature
	 */
	public FunDoc(XQueryContext context) {
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
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		DocumentSet docs = new DocumentSet();
		getParent().resetState();
		Sequence arg =
			getArgument(0).eval(contextSequence, contextItem);
		if(arg.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		String path = arg.itemAt(0).getStringValue();
		if (path.length() == 0)
			throw new XPathException("Invalid argument to fn:doc function: empty string is not allowed here.");
		if (path.charAt(0) != '/')
			path = context.getBaseURI() + '/' + path;
		try {
			DocumentImpl doc = (DocumentImpl) context.getBroker().getDocument(path);
			if(doc == null)
				return Sequence.EMPTY_SEQUENCE;
			return new NodeProxy(doc, -1);
		} catch (PermissionDeniedException e) {
			throw new XPathException(
				"Permission denied: unable to load document " + path);
		}
	}

}
