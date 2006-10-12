/*
 *  eXist Context Module Extension SetVarFunction
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
 * eXist Context Module Extension SetVarFunction 
 * 
 * The Variable Setting functionality of the eXist Context Module Extension that
 * allows variables to be set in the Context of the executing XQuery, they may then
 * be retreived with get-var()  
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-03-01
 * @version 1.2
 *
 * @see org.exist.xquery.Function
 */
public class SetVarFunction extends Function {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("set-var", ContextModule.NAMESPACE_URI, ContextModule.PREFIX),
			"set's a variable named $a with the item $b in the current context",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)
	);
	
	/**
	 * SetVarFunction Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public SetVarFunction(XQueryContext context)
	{
		super(context, signature);
	}

	/**
	 * evaluate the call to the xquery set-var() function,
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
		Sequence value = getArgument(1).eval(contextSequence, contextItem);
		
		context.setXQueryContextVar(name, value);
		
		return Sequence.EMPTY_SEQUENCE;
	}

}
