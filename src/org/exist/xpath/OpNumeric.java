/* eXist Open Source Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.storage.DBBroker;
import org.exist.xpath.value.ComputableValue;
import org.exist.xpath.value.IntegerValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.NumericValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

/**
 * numeric operation on two operands by +, -, *, div, mod etc..
 *
 */
public class OpNumeric extends BinaryOp {

	protected int operator = Constants.PLUS;
	protected int returnType;
	protected NodeSet temp = null;
	protected DBBroker broker;

	public OpNumeric(StaticContext context, int operator) {
		super(context);
		this.operator = operator;
	}

	public OpNumeric(
		StaticContext context,
		Expression left,
		Expression right,
		int operator) {
		super(context);
		this.operator = operator;
		returnType = Type.ATOMIC;
		int ltype = left.returnsType();
		int rtype = right.returnsType();
		if (!Type.subTypeOf(ltype, Type.ATOMIC)) {
			left = new Atomize(context, left);
			ltype = Type.ATOMIC;
		}
		if (!Type.subTypeOf(rtype, Type.ATOMIC)) {
			right = new Atomize(context, right);
			rtype = Type.ATOMIC;
		}
		
		// check for date and time operands
		if(Type.subTypeOf(ltype, Type.DATE) || Type.subTypeOf(ltype, Type.TIME)) {	
			returnType = ltype;
		// select best return type for numeric operands
		} else {
			if (ltype == Type.ATOMIC) {
				left = (operator == Constants.IDIV ? new UntypedValueCheck(context, Type.INTEGER, left) :
					new UntypedValueCheck(context, Type.DOUBLE, left));
				ltype = left.returnsType();
			}
			if (rtype == Type.ATOMIC) {
				right = (operator == Constants.IDIV ? new UntypedValueCheck(context, Type.INTEGER, right) :
					new UntypedValueCheck(context, Type.DOUBLE, right));
				rtype = right.returnsType();
			}
			
			if(Type.subTypeOf(ltype, Type.NUMBER)) {
				if(!Type.subTypeOf(rtype, Type.NUMBER)) {
					right = new UntypedValueCheck(context, ltype, right);
					returnType = ltype;
				} else {
					if(ltype > rtype)
						right = new UntypedValueCheck(context, ltype, right);
					else if(rtype > ltype)
						left = new UntypedValueCheck(context, rtype, left);
				}
			} else if(Type.subTypeOf(rtype, Type.NUMBER)) {
				if(!Type.subTypeOf(ltype, Type.NUMBER)) {
					left = new UntypedValueCheck(context, rtype, left);
					returnType = rtype;
				} else {
					if(rtype > ltype)
						left = new UntypedValueCheck(context, rtype, left);
					else if(rtype > ltype)
						right = new UntypedValueCheck(context, ltype, right);
				}
			}
		}
		
		// if we still have no return type, use the return type of the left expression
		if(returnType == Type.ATOMIC)
			returnType = left.returnsType();
		add(left);
		add(right);
	}

	public int returnsType() {
		return returnType;
	}

	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		Sequence lseq =
			getLeft().eval(docs, contextSequence);
		if(lseq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		Sequence rseq =
			getRight().eval(docs, contextSequence);
		if(rseq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		
		ComputableValue lvalue = (ComputableValue)lseq.itemAt(0);
		ComputableValue rvalue = (ComputableValue)rseq.itemAt(0);
		
		if(operator == Constants.IDIV)
			return ((IntegerValue)lvalue).idiv((NumericValue)rvalue);
		else
			return applyOperator((ComputableValue)lvalue, (ComputableValue)rvalue);
	}

	public ComputableValue applyOperator(ComputableValue left, ComputableValue right)
		throws XPathException {
		switch (operator) {
			case Constants.MINUS :
				return left.minus(right);
			case Constants.PLUS :
				return left.plus(right);
			case Constants.MULT :
				return left.mult(right);
			case Constants.DIV :
				return left.div(right);
			case Constants.MOD :
				return ((NumericValue)left).mod((NumericValue)right);
			default :
				return null;
		}
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(getLeft().pprint());
		buf.append(' ');
		buf.append(Constants.OPS[operator]);
		buf.append(' ');
		buf.append(getRight().pprint());
		return buf.toString();
	}
}
