/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
 *  http://exist-db.org
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
package org.exist.xslt.functions;

import org.exist.dom.INode;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

/**
 * generate-id() as xs:string 
 * generate-id($node as node()?) as xs:string
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class Generate_id extends BasicFunction {

	public final static FunctionSignature signatures[] = {
			new FunctionSignature(new QName("generate-id", XSLModule.NAMESPACE_URI, XSLModule.PREFIX),
					"The function returns a string that uniquely identifies a given node.", FunctionSignature.NO_ARGS, new SequenceType(Type.ITEM,
							Cardinality.EXACTLY_ONE)),
			new FunctionSignature(new QName("generate-id", XSLModule.NAMESPACE_URI, XSLModule.PREFIX),
					"The function returns a string that uniquely identifies a given node.", new SequenceType[] { new SequenceType(Type.NODE,
							Cardinality.ZERO_OR_ONE) }, new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE)) };

	/**
	 * @param context
	 */
	public Generate_id(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

		if (!contextSequence.isEmpty() && contextSequence.hasOne()) {
			INode docNode = (INode)args[0].itemAt(0);
			return new StringValue(docNode.getNodeId().toString());
		}

		return new StringValue("generate-id"); // XXX: error?
	}

}
