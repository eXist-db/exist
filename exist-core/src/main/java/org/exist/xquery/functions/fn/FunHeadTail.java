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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

public class FunHeadTail extends BasicFunction {

	public final static FunctionSignature FN_HEAD = new FunctionSignature(
		new QName("head", Function.BUILTIN_FUNCTION_NS),
		"The function returns the value of the expression $arg[1], i.e. the first item in the " +
		"passed in sequence.",
		new SequenceType[] {
			new FunctionParameterSequenceType("arg", Type.ITEM, Cardinality.ZERO_OR_MORE, "")
		},
		new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "the first item or the empty sequence"));

	public final static FunctionSignature FN_TAIL = new FunctionSignature(
		new QName("tail", Function.BUILTIN_FUNCTION_NS),
		"The function returns the value of the expression subsequence($sequence, 2), i.e. a new sequence containing " +
		"all items of the input sequence except the first.",
		new SequenceType[] {
			new FunctionParameterSequenceType("sequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "The source sequence")
		},
		new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the resulting sequence"));

	public final static FunctionSignature FN_FOOT = new FunctionSignature(
		new QName("foot", Function.BUILTIN_FUNCTION_NS),
		"Returns the last item in a sequence.",
		new SequenceType[] {
			new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence")
		},
		new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "the last item or the empty sequence"));

	public final static FunctionSignature FN_TRUNK = new FunctionSignature(
		new QName("trunk", Function.BUILTIN_FUNCTION_NS),
		"Returns all but the last item in a sequence.",
		new SequenceType[] {
			new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence")
		},
		new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "all items except the last"));

	public final static FunctionSignature[] signatures = { FN_HEAD, FN_TAIL, FN_FOOT, FN_TRUNK };

	public FunHeadTail(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		super.analyze(contextInfo);
		if (getContext().getXQueryVersion()<30) {
			throw new XPathException(this, ErrorCodes.EXXQDY0003, "Function " +
					getSignature().getName() + " is only supported for xquery version \"3.0\" and later.");
		}
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		final Sequence seq = args[0];
		if (seq.isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}
		if (isCalledAs("head")) {
			return seq.itemAt(0).toSequence();
		} else if (isCalledAs("tail")) {
			return seq.tail();
		} else if (isCalledAs("foot")) {
			return seq.itemAt(seq.getItemCount() - 1).toSequence();
		} else {
			// trunk: all items except the last
			final int count = seq.getItemCount();
			if (count <= 1) {
				return Sequence.EMPTY_SEQUENCE;
			}
			final ValueSequence result = new ValueSequence(count - 1);
			for (int i = 0; i < count - 1; i++) {
				result.add(seq.itemAt(i));
			}
			return result;
		}
	}

}
