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

package org.exist.xquery;

import org.exist.dom.NodeSet;
import org.exist.storage.DBBroker;
import org.exist.xquery.value.ComputableValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * numeric operation on two operands by +, -, *, div, mod etc..
 *
 */
public class OpNumeric extends BinaryOp {

	protected int operator = Constants.PLUS;
	protected int returnType;
	protected NodeSet temp = null;
	protected DBBroker broker;

	public OpNumeric(XQueryContext context, int operator) {
		super(context);
		this.operator = operator;
	}

	public OpNumeric(
		XQueryContext context,
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
		if (Type.subTypeOf(ltype, Type.DATE) || Type.subTypeOf(ltype, Type.TIME)) {
			returnType = ltype;
			// select best return type for numeric operands
		} else {
			if (Type.subTypeOf(ltype, Type.NUMBER)) {
				if (Type.subTypeOf(rtype, Type.NUMBER)) {
					if (ltype > rtype) {
						right = new UntypedValueCheck(context, ltype, right);
						returnType = ltype;
					} else if (rtype > ltype) {
						left = new UntypedValueCheck(context, rtype, left);
						returnType = rtype;
					}
				}
			} else if (Type.subTypeOf(rtype, Type.NUMBER)) {
				if (Type.subTypeOf(ltype, Type.NUMBER)) {
					if (rtype > ltype) {
						left = new UntypedValueCheck(context, rtype, left);
						returnType = rtype;
					} else if (rtype > ltype) {
						right = new UntypedValueCheck(context, ltype, right);
						returnType = ltype;
					}
				}
			}
		}

		// if we still have no return type, use the return type of the left expression
		if (returnType == Type.ATOMIC)
			returnType = left.returnsType();
		add(left);
		add(right);
	}

	public int returnsType() {
		return returnType;
	}

	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		Sequence lseq = getLeft().eval(contextSequence);
		if (lseq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		Sequence rseq = getRight().eval(contextSequence);
		if (rseq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;

		Item lvalue = lseq.itemAt(0), rvalue = rseq.itemAt(0);
		
		try {
			// runtime type checks:
			if (!(lvalue instanceof ComputableValue))
				lvalue =
					operator == Constants.IDIV
						? lvalue.convertTo(Type.INTEGER)
						: lvalue.convertTo(Type.DOUBLE);
			if (!(rvalue instanceof ComputableValue))
				rvalue =
					operator == Constants.IDIV
						? rvalue.convertTo(Type.INTEGER)
						: rvalue.convertTo(Type.DOUBLE);

			int ltype = lvalue.getType(), rtype = rvalue.getType();
			if (Type.subTypeOf(ltype, Type.NUMBER)) {
				if (!Type.subTypeOf(rtype, Type.NUMBER)) {
					rvalue = rvalue.convertTo(ltype);
				} else {
					if (ltype > rtype) {
						rvalue = rvalue.convertTo(ltype);
					} else if (rtype > ltype) {
						lvalue = lvalue.convertTo(rtype);
					}
				}
			} else if (Type.subTypeOf(rtype, Type.NUMBER)) {
				if (!Type.subTypeOf(ltype, Type.NUMBER)) {
					lvalue = lvalue.convertTo(rtype);
				} else {
					if (rtype > ltype) {
						lvalue = lvalue.convertTo(rtype);
					} else if (rtype > ltype) {
						rvalue = rvalue.convertTo(ltype);
					}
				}
			}

			if (operator == Constants.IDIV)
				return ((IntegerValue) lvalue).idiv((NumericValue) rvalue);
			else
				return applyOperator((ComputableValue) lvalue, (ComputableValue) rvalue);
		} catch (XPathException e) {
			e.setASTNode(getASTNode());
			throw e;
		}
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
				return ((NumericValue) left).mod((NumericValue) right);
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
