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
		if (!Type.subTypeOf(left.returnsType(), Type.ATOMIC))
			left = new Atomize(context, left);
		if (!Type.subTypeOf(right.returnsType(), Type.ATOMIC))
			right = new Atomize(context, right);
		if (left.returnsType() == right.returnsType()) {
			returnType = left.returnsType();
		} else if (left.returnsType() == Type.DOUBLE) {
			right = new UntypedValueCheck(context, Type.DOUBLE, right);
			returnType = Type.DOUBLE;
		} else if (right.returnsType() == Type.DOUBLE) {
			left = new UntypedValueCheck(context, Type.DOUBLE, left);
			returnType = Type.DOUBLE;
		} else if (left.returnsType() == Type.FLOAT) {
			right = new UntypedValueCheck(context, Type.FLOAT, right);
			returnType = Type.FLOAT;
		} else if (right.returnsType() == Type.FLOAT) {
			left = new UntypedValueCheck(context, Type.FLOAT, left);
			returnType = Type.FLOAT;
		} else if (Type.subTypeOf(left.returnsType(), Type.DECIMAL)) {
			right = new UntypedValueCheck(context, left.returnsType(), right);
			returnType = left.returnsType();
		} else if (Type.subTypeOf(right.returnsType(), Type.DECIMAL)) {
			left = new UntypedValueCheck(context, right.returnsType(), left);
			returnType = right.returnsType();
		}
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
		NumericValue lvalue =
			(NumericValue) getLeft().eval(docs, contextSequence).convertTo(
				returnType);
		NumericValue rvalue =
			(NumericValue) getRight().eval(docs, contextSequence).convertTo(
				returnType);
		return applyOperator(lvalue, rvalue);
	}

	public NumericValue applyOperator(NumericValue left, NumericValue right)
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
				return left.mod(right);
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
