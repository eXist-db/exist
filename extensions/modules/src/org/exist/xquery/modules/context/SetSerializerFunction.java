/*
 *  eXist Context Module Extension SetSerializerFunction
 *  Copyright (C) 2006 Adam Retter <adam.retter@devon.gov.uk>
 *  www.adamretter.co.uk
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

package org.exist.xquery.modules.context;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 * eXist Context Module Extension SetSerializerFunction 
 * 
 * The Serializer Setting functionality of the eXist Context Module Extension that
 * allows the eXist XQuery Serializer to be set from within the executing XQuery
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-03-01
 * @version 1.3
 *
 * @see org.exist.xquery.Function
 */
public class SetSerializerFunction extends Function {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("set-serializer", ContextModule.NAMESPACE_URI, ContextModule.PREFIX),
			"Set's the Serializer named in $a to use for output. $b indicates whether output should be indented and $c indicates whether the xml declaration should be omitted.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)
	);
	
	/**
	 * SetSerializerFunction Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public SetSerializerFunction(XQueryContext context)
	{
		super(context, signature);
	}

	/**
	 * evaluate the call to the xquery set-serializer() function,
	 * it is really the main entry point of this class
	 * 
	 * @param contextSequence	the Context Sequence to operate on
	 * @param contextItem		the Context Item to operate on
	 * @return		An Empty Sequence
	 * 
	 * @see org.exist.xquery.Function#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException
	{
		String name = getArgument(0).eval(contextSequence, contextItem).getStringValue();
		boolean indent = getArgument(1).eval(contextSequence, contextItem).effectiveBooleanValue(); 
		boolean omitxmldeclaration = getArgument(2).eval(contextSequence, contextItem).effectiveBooleanValue();
		
		context.setXQuerySerializer(name, indent, omitxmldeclaration);
		
		return Sequence.EMPTY_SEQUENCE;
	}

}
