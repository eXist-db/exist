/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * An XQuery range expression, like "1 to 10".
 * 
 * @author wolf
 */
public class RangeExpression extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("to", Function.BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE),
				new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE));

	/**
	 * @param context
	 */
	public RangeExpression(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {		
		Sequence result;
		Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if(seq.isEmpty())
			result = Sequence.EMPTY_SEQUENCE;
        else {
        	long start = ((IntegerValue)seq.convertTo(Type.INTEGER)).getLong();
        	seq = getArgument(1).eval(contextSequence, contextItem);
    		if(seq.isEmpty())
    			result = Sequence.EMPTY_SEQUENCE;
    		else {
    			long end = ((IntegerValue)seq.convertTo(Type.INTEGER)).getLong();        	
    			result = new ValueSequence();
    			for(long i = start; i <= end; i++) {
    				result.add(new IntegerValue(i));
    			}
    		}
        }
		return result;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.NO_DEPENDENCY;
	}
}
