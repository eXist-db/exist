/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2009 The eXist Project
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

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * unparsed-entity-uri($entity-name as xs:string) as xs:anyURI 
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Unparsed_entity_uri extends BasicFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName("unparsed-entity-uri", XSLTModule.NAMESPACE_URI, XSLTModule.PREFIX),
				"The function returns the URI of the unparsed entity whose name is given by the value of the $entity-name argument, in the document containing the context node.",
				new SequenceType[] {
						new SequenceType(Type.STRING, Cardinality.ONE)},
				new SequenceType(Type.ANY_URI, Cardinality.ONE)
		)
	};
	
	/**
	 * @param context
	 */
	public Unparsed_entity_uri(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		// TODO Auto-generated method stub
		throw new RuntimeException("Method is not implemented");
	}

}
