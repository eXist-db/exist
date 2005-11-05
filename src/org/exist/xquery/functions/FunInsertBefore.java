package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements the fn:insert-before function.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunInsertBefore extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("insert-before", Function.BUILTIN_FUNCTION_NS),
			"Returns a new sequence constructed from the value of the target sequence" +
			"with the value of the sequence to insert inserted at the position specified.",
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.INTEGER, Cardinality.ONE),
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));

	public FunInsertBefore(XQueryContext context) {
		super(context, signature);
	}


	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence seq1 = getArgument(0).eval(contextSequence, contextItem);
		Sequence seq2 = getArgument(2).eval(contextSequence, contextItem);
		if (seq1.getLength() == 0) return seq2;
		if (seq2.getLength() == 0) return seq1;
		int pos = 
			((DoubleValue)getArgument(1).eval(contextSequence, contextItem).convertTo(Type.DOUBLE)).getInt();
		pos--;
		Sequence result = new ValueSequence();
		if (pos <= 0) {
			result.addAll(seq2);
			result.addAll(seq1);
		} else if (pos >= seq1.getLength()) {
			result.addAll(seq1);
			result.addAll(seq2);
		} else {
			for (int i=0; i<seq1.getLength(); i++) {
				if (i == pos) result.addAll(seq2);
				result.add(seq1.itemAt(i));
			}
		}
		return result;
	}

}
