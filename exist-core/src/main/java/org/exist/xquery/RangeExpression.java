/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import java.util.List;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * An XQuery range expression, like "1 to 10".
 * 
 * @author wolf
 */
public class RangeExpression extends PathExpr {
	
	Expression start;
	Expression end;

	//TODO : RangeExpression(XQueryContext context, Expressoin start, Expression end)
	//Needs parser refactoring
	public RangeExpression(XQueryContext context) {
		super(context);
	}
	
	//TODO : remove and use the other constructor
	public void setArguments(List<Expression> arguments) throws XPathException {
		start = arguments.get(0);
		end = arguments.get(1);        
	}
	
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	//TODO : static checks ?
    	/*
    	if (!Cardinality.checkCardinality(Cardinality.ZERO_OR_ONE, start.getCardinality()))
		    throw new XPathException(this, "Invalid cardinality for 1st argument");
    	if (!Cardinality.checkCardinality(Cardinality.ZERO_OR_ONE, end.getCardinality()))
		    throw new XPathException(this, "Invalid cardinality for 2nd argument");
    	if (start.returnsType() != Type.INTEGER)
		    throw new XPathException(this, "Invalid type for 1st argument");
    	if (end.returnsType() != Type.INTEGER)
		    throw new XPathException(this, "Invalid type for 2nd argument");
        */
    	inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
    	contextId = contextInfo.getContextId();
    	contextInfo.setParent(this);
    	start.analyze(contextInfo);
    	end.analyze(contextInfo);
    }
    
   
    /* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {		
    	Sequence result = null;
		final Sequence startSeq = start.eval(contextSequence, contextItem);
		final Sequence endSeq = end.eval(contextSequence, contextItem);
		if (startSeq.isEmpty())
		    {result = Sequence.EMPTY_SEQUENCE;}
		else if (endSeq.isEmpty())
			{result = Sequence.EMPTY_SEQUENCE;}
		else if (startSeq.hasMany())
			{throw new XPathException(this, ErrorCodes.XPTY0004, "The first operand must have at most one item", startSeq);}
		else if (endSeq.hasMany())
			{throw new XPathException(this, ErrorCodes.XPTY0004, "The second operand must have at most one item", endSeq);}
        else {
        	if (context.isBackwardsCompatible()) {
	        	NumericValue valueStart;
	        	try {
	        		//Currently breaks 1e3 to 3
	        		valueStart = (NumericValue)startSeq.itemAt(0).convertTo(Type.NUMERIC);
	        	} catch (final XPathException e) {
					throw new XPathException(this, ErrorCodes.FORG0006, "Required type is " +
							Type.getTypeName(Type.INTEGER) + " but got '" + Type.getTypeName(startSeq.itemAt(0).getType()) + "(" +
							startSeq.itemAt(0).getStringValue() + ")'", startSeq);
	        	}
	        	NumericValue valueEnd;
	        	try {
	        		//Currently breaks 3 to 1e3
	        		valueEnd = (NumericValue)endSeq.itemAt(0).convertTo(Type.NUMERIC);
	        	} catch (final XPathException e) {
					throw new XPathException(this, ErrorCodes.FORG0006, "Required type is " +
							Type.getTypeName(Type.INTEGER) + " but got '" + Type.getTypeName(endSeq.itemAt(0).getType()) + "(" +
							endSeq.itemAt(0).getStringValue() + ")'", endSeq);
	        	}
	        	//Implied by previous conversion
	        	if (valueStart.hasFractionalPart()) {
					throw new XPathException(this, ErrorCodes.FORG0006, "Required type is " +
							Type.getTypeName(Type.INTEGER) + " but got '" + Type.getTypeName(startSeq.itemAt(0).getType()) + "(" +
							startSeq.itemAt(0).getStringValue() + ")'", startSeq);
				}
	        	//Implied by previous conversion
	        	if (valueEnd.hasFractionalPart()) {
					throw new XPathException(this, ErrorCodes.FORG0006, "Required type is " +
							Type.getTypeName(Type.INTEGER) + " but got '" + Type.getTypeName(endSeq.itemAt(0).getType()) + "(" +
							startSeq.itemAt(0).getStringValue() + ")'", endSeq);
	        	}        	
//	        	result = new ValueSequence();
//				for(long i = ((IntegerValue)valueStart.convertTo(Type.INTEGER)).getLong(); 
//					i <= ((IntegerValue)valueEnd.convertTo(Type.INTEGER)).getLong(); i++) {
//					result.add(new IntegerValue(i));
//				}
				result = new RangeSequence((IntegerValue)valueStart.convertTo(Type.INTEGER), 
							(IntegerValue)valueEnd.convertTo(Type.INTEGER));
	        } else {
	        	//Quite unusual test : we accept integers but no other *typed* type 
	        	if (!Type.subTypeOf(startSeq.itemAt(0).atomize().getType(), Type.INTEGER) &&
	        		!Type.subTypeOf(startSeq.itemAt(0).atomize().getType(), Type.UNTYPED_ATOMIC))
					{throw new XPathException(this, ErrorCodes.FORG0006, "Required type is " +
							Type.getTypeName(Type.INTEGER) + " but got '" + Type.getTypeName(startSeq.itemAt(0).getType()) + "(" +
							startSeq.itemAt(0).getStringValue() + ")'", startSeq);}
	        	//Quite unusual test : we accept integers but no other *typed* type 
	        	if (!Type.subTypeOf(endSeq.itemAt(0).atomize().getType(), Type.INTEGER) &&
	        		!Type.subTypeOf(endSeq.itemAt(0).atomize().getType(), Type.UNTYPED_ATOMIC))
					{throw new XPathException(this, ErrorCodes.FORG0006, "Required type is " +
							Type.getTypeName(Type.INTEGER) + " but got '" + Type.getTypeName(endSeq.itemAt(0).getType()) + "(" +
							endSeq.itemAt(0).getStringValue() + ")'", endSeq);}
	        	final IntegerValue valueStart = (IntegerValue)startSeq.itemAt(0).convertTo(Type.INTEGER);
	        	final IntegerValue valueEnd = (IntegerValue)endSeq.itemAt(0).convertTo(Type.INTEGER);
//	       		result = new ValueSequence();
//				for (long i = valueStart.getLong();	i <= valueEnd.getLong(); i++) {
//					result.add(new IntegerValue(i));
//				}
	        	result = new RangeSequence(valueStart, valueEnd);
	        }
        }
		return result;
	}

    public void dump(ExpressionDumper dumper) {
        dumper.display(start);
        dumper.display(" to ");
        dumper.display(end);
    }
    
    public int returnsType() {
        return Type.INTEGER;
    }    
	
    public Expression simplify() {
    	return this;
    }
}
