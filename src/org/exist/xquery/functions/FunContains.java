/*
 * FunContains.java - Aug 29, 2003
 * 
 * @author wolf
 */
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunContains extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("contains", BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.ONE));

	public FunContains(XQueryContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.BOOLEAN;
	}

	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		String s1 =
			getArgument(0)
				.eval(contextSequence)
				.getStringValue();
		String s2 =
			getArgument(1)
				.eval(contextSequence)
				.getStringValue();
		if (s1.indexOf(s2) > -1)
			return BooleanValue.TRUE;
		else
			return BooleanValue.FALSE;
	}
}
