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
 * key($key-name as xs:string, $key-value as xs:anyAtomicType*) as node()* 
 * key($key-name as xs:string, $key-value as xs:anyAtomicType*, $top as node()) as node()* 
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Key extends BasicFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName("key", XSLModule.NAMESPACE_URI, XSLModule.PREFIX),
				"The function does for keys what the id FO function does for IDs",
				new SequenceType[] {
						new SequenceType(Type.STRING, Cardinality.ONE),
						new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE)},
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
		),
		new FunctionSignature(
				new QName("key", XSLModule.NAMESPACE_URI, XSLModule.PREFIX),
				"The function does for keys what the id FO function does for IDs",
				new SequenceType[] {
						new SequenceType(Type.STRING, Cardinality.ONE),
						new SequenceType(Type.ATOMIC, Cardinality.ZERO_OR_MORE),
						new SequenceType(Type.NODE, Cardinality.ONE)},
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
		)
	};
	
	/**
	 * @param context
	 */
	public Key(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		// TODO Auto-generated method stub
		throw new RuntimeException("Method is not implemented");
	}

}
