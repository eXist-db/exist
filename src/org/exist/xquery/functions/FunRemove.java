package org.exist.xquery.functions;

import org.exist.dom.*;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.Function;
import org.exist.xquery.value.*;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Implements the fn:remove function.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class FunRemove extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("remove", Module.BUILTIN_FUNCTION_NS),
			"Returns a new sequence constructed from the value of the target sequence" +
			"with the item at the position specified removed.",
			new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.INTEGER, Cardinality.ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));



	public FunRemove(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if (seq.getLength() == 0) return Sequence.EMPTY_SEQUENCE;
		int pos = 
			((DoubleValue)getArgument(1).eval(contextSequence, contextItem).convertTo(Type.DOUBLE)).getInt();
		if (pos < 1 || pos > seq.getLength()) return seq;
		pos--;
		if (seq instanceof NodeSet) {
			ExtArrayNodeSet result = new ExtArrayNodeSet();
			result.addAll((NodeSet) seq);
			result.remove((NodeProxy) seq.itemAt(pos));
			return result;
		} else {
			Sequence result = new ValueSequence();
			for (int i = 0; i < seq.getLength(); i++) {
				if (i != pos) result.add(seq.itemAt(i));
			}
			return result;
		}
	}
}