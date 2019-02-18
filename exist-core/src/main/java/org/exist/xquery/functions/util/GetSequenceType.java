/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist team
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
 *  $Id: Compile.java 5533 2007-03-26 13:55:42Z ellefj $
 */
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class GetSequenceType extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(GetSequenceType.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-sequence-type", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the string representation of the type of sequence.",
			new SequenceType[] {
				new FunctionParameterSequenceType("sequence-type", Type.ANY_TYPE, Cardinality.ZERO_OR_MORE, "The type of sequence")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the string representation of the type of sequence"));
	
	public GetSequenceType(XQueryContext context) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {		
		
		final Sequence seq = args[0];
		final StringValue stringValue = new StringValue(Type.getTypeName(seq.getItemType()));
		return stringValue;
	}

}
