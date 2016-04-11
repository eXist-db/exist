package org.exist.xquery.functions.fn;

import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeSet;
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

	public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("head", Function.BUILTIN_FUNCTION_NS),
            "The function returns the value of the expression $arg[1], i.e. the first item in the " +
            "passed in sequence.",
            new SequenceType[] {
                new FunctionParameterSequenceType("arg", Type.ITEM, Cardinality.ZERO_OR_MORE, "")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "the first item or the empty sequence")),
        new FunctionSignature(
                new QName("tail", Function.BUILTIN_FUNCTION_NS),
                "The function returns the value of the expression subsequence($sequence, 2), i.e. a new sequence containing " +
                "all items of the input sequence except the first.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("sequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "The source sequence")
                    },
                new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the resulting sequence")) };
	
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
		Sequence tmp;
		if (seq.isEmpty()) {
			tmp = Sequence.EMPTY_SEQUENCE;
		} else if (isCalledAs("head")) {
			tmp = seq.itemAt(0).toSequence();
		} else {
            tmp = seq.tail();
		}
		return tmp;
	}

}
