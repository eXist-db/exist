/*
 * FunContains.java - Aug 29, 2003
 * 
 * @author wolf
 */
package org.exist.xpath.functions;

import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.BooleanValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

public class FunContains extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("contains", BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
				 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.ONE));

	public FunContains(StaticContext context) {
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
