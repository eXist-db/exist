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

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

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
			new QName("doc", Module.BUILTIN_FUNCTION_NS),
			"Includes a document "
				+ "into the input sequence. "
				+ "eXist interprets the argument as a path pointing to a "
				+ "document in the database, as for example, '/db/shakespeare/plays/hamlet.xml'. "
				+ "If the path is relative, "
				+ "it is resolved relative to the base URI property from the static context."
				+ "Understands also standard URLs, starting with http:// , file:// , etc.",
			new SequenceType[] { new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE));
	
	/**
	 * @param context
	 * @param signature
	 */
	public FunDoc(XQueryContext context) {
		super(context, signature);
	}

	/**
	 * @see org.exist.xquery.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}

	/**
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		
		Sequence arg = getArgument(0).eval(contextSequence, contextItem);
		if (arg.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		
		String path = arg.itemAt(0).getStringValue();		
		
		try {
			Sequence seq = DocUtils.getDocument(this.context, path);
//			TODO: we still need a final decision about this. Also check base-uri.
//			if (seq == Sequence.EMPTY_SEQUENCE)
//				throw new XPathException(getASTNode(), path + " is not an XML document");
			return seq;
		}
		catch (Exception e) {
			throw new XPathException(getASTNode(), e.getMessage());			
		}
		
	}

	/**
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
	public void resetState() {
		getArgument(0).resetState();
	}
}
