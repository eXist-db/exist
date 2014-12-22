package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

public class FunHigherOrderFun extends BasicFunction {

    public final static FunctionSignature FN_FOR_EACH =
        new FunctionSignature(
            new QName("for-each", Function.BUILTIN_FUNCTION_NS),
            "Applies the function item $function to every item from the sequence " +
                    "$sequence in turn, returning the concatenation of the resulting sequences in order.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("sequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "the sequence on which to apply the function"),
                    new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "the function to call")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "result of applying the function to each item of the sequence")
        );

    public final static FunctionSignature FN_FOR_EACH_PAIR =
        new FunctionSignature(
            new QName("for-each-pair", Function.BUILTIN_FUNCTION_NS),
                "Applies the function item $f to successive pairs of items taken one from $seq1 and one from $seq2, " +
                "returning the concatenation of the resulting sequences in order.",
            new SequenceType[] {
                new FunctionParameterSequenceType("seq1", Type.ITEM, Cardinality.ZERO_OR_MORE, "first sequence to take items from"),
                new FunctionParameterSequenceType("seq2", Type.ITEM, Cardinality.ZERO_OR_MORE, "second sequence to take items from"),
                new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "the function to call")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "concatenation of resulting sequences")
        );

	public final static FunctionSignature signatures[] = {
	    new FunctionSignature(
	        new QName("map", Function.BUILTIN_FUNCTION_NS),
	        "Applies the function item $function to every item from the sequence " +
	        "$sequence in turn, returning the concatenation of the resulting sequences in order.",
	        new SequenceType[] {
	            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "the function to call"),
	            new FunctionParameterSequenceType("sequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "the sequence on which to apply the function")
	        },
	        new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, 
	        		"result of applying the function to each item of the sequence"),
            FN_FOR_EACH
        ),
        FN_FOR_EACH,
		new FunctionSignature(
	        new QName("filter", Function.BUILTIN_FUNCTION_NS),
	        "Returns those items from the sequence $sequence for which the supplied function $function returns true.",
	        new SequenceType[] {
                new FunctionParameterSequenceType("sequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "the sequence to filter"),
	            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "the function to call")
	        },
	        new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, 
        		"result of filtering the sequence")),
		new FunctionSignature(
	        new QName("fold-left", Function.BUILTIN_FUNCTION_NS),
	        "Processes the supplied sequence from left to right, applying the supplied function repeatedly to each " +
	        "item in turn, together with an accumulated result value.",
	        new SequenceType[] {
                new FunctionParameterSequenceType("sequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "the sequence to filter"),
                new FunctionParameterSequenceType("zero", Type.ITEM, Cardinality.ZERO_OR_MORE, "initial value to start with"),
	            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "the function to call")
	        },
	        new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, 
	        		"result of the fold-left operation")),
		new FunctionSignature(
	        new QName("fold-right", Function.BUILTIN_FUNCTION_NS),
	        "Processes the supplied sequence from right to left, applying the supplied function repeatedly to each " +
	        "item in turn, together with an accumulated result value.",
	        new SequenceType[] {
                new FunctionParameterSequenceType("sequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "the sequence to filter"),
                new FunctionParameterSequenceType("zero", Type.ITEM, Cardinality.ZERO_OR_MORE, "initial value to start with"),
	            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "the function to call"),
	        },
	        new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, 
	        		"result of the fold-right operation")),
		new FunctionSignature(
	        new QName("map-pairs", Function.BUILTIN_FUNCTION_NS),
	        "Applies the function item $f to successive pairs of items taken one from $seq1 and one from $seq2, " +
	        "returning the concatenation of the resulting sequences in order.",
	        new SequenceType[] {
	            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "the function to call"),
	            new FunctionParameterSequenceType("seq1", Type.ITEM, Cardinality.ZERO_OR_MORE, "first sequence to take items from"),
	            new FunctionParameterSequenceType("seq2", Type.ITEM, Cardinality.ZERO_OR_MORE, "second sequence to take items from")
	        },
	        new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, 
	        		"result of the map-pairs operation"),
            FN_FOR_EACH_PAIR
        ),
        FN_FOR_EACH_PAIR
	};
    
	private AnalyzeContextInfo cachedContextInfo;
	
	public FunHigherOrderFun(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    @Override
    protected void checkArguments() throws XPathException {
        // hack: order of parameters for filter and other functions has changed
        // in final XQ3 spec. This would cause some core apps (dashboard) to stop
        // working. We thus switch parameters dynamically until all users can be expected to
        // have updated to 2.2.
        if (!isCalledAs("filter")) {
            super.checkArguments();
        }
    }

    @Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(cachedContextInfo);
	}
	
	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
        Sequence result = new ValueSequence();
        if (isCalledAs("map")) {
            final FunctionReference ref = (FunctionReference) args[0].itemAt(0);
            ref.analyze(cachedContextInfo);
            for (final SequenceIterator i = args[1].iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                final Sequence r = ref.evalFunction(contextSequence, null, new Sequence[]{item.toSequence()});
                result.addAll(r);
            }
        } else if (isCalledAs("for-each")) {
            final FunctionReference ref = (FunctionReference) args[1].itemAt(0);
            ref.analyze(cachedContextInfo);
            for (final SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                final Sequence r = ref.evalFunction(contextSequence, null, new Sequence[]{item.toSequence()});
                result.addAll(r);
            }
        } else if (isCalledAs("filter")) {
            FunctionReference ref;
            Sequence seq;
            // Hack: switch parameters for backwards compatibility
            if (Type.subTypeOf(args[1].getItemType(), Type.FUNCTION_REFERENCE)) {
                ref = (FunctionReference) args[1].itemAt(0);
                seq = args[0];
            } else {
                ref = (FunctionReference) args[0].itemAt(0);
                seq = args[1];
            }

            ref.analyze(cachedContextInfo);
        	for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
	        	final Item item = i.nextItem();
	        	final Sequence r = ref.evalFunction(contextSequence, null, new Sequence[] { item.toSequence() });
	        	if (r.effectiveBooleanValue())
	        		{result.add(item);}
	        }
        } else if (isCalledAs("fold-left")) {
            final FunctionReference ref = (FunctionReference) args[2].itemAt(0);
            ref.analyze(cachedContextInfo);
        	Sequence zero = args[1];
        	Sequence input = args[0];
        	while (!input.isEmpty()) {
        		final SequenceIterator i = input.iterate();
        		zero = ref.evalFunction(contextSequence, null, new Sequence[] { zero, i.nextItem().toSequence() });
        		ValueSequence tail = new ValueSequence();
        		while (i.hasNext()) {
        			tail.add(i.nextItem());
        		}
        		input = tail;
        	}
        	result = zero;
        } else if (isCalledAs("fold-right")) {
            final FunctionReference ref = (FunctionReference) args[2].itemAt(0);
            ref.analyze(cachedContextInfo);
        	final Sequence zero = args[1];
        	final Sequence input = args[0];
        	result = foldRight(ref, zero, input, contextSequence);
        } else if (isCalledAs("map-pairs")) {
            final FunctionReference ref = (FunctionReference) args[0];
            ref.analyze(cachedContextInfo);
        	final SequenceIterator i1 = args[1].iterate();
        	final SequenceIterator i2 = args[2].iterate();
        	while (i1.hasNext() && i2.hasNext()) {
        		final Sequence r = ref.evalFunction(contextSequence, null, 
        				new Sequence[] { i1.nextItem().toSequence(), i2.nextItem().toSequence() });
        		result.addAll(r);
        	}
        } else if (isCalledAs("for-each-pair")) {
            final FunctionReference ref = (FunctionReference) args[2].itemAt(0);
            ref.analyze(cachedContextInfo);
            final SequenceIterator i1 = args[0].iterate();
            final SequenceIterator i2 = args[1].iterate();
            while (i1.hasNext() && i2.hasNext()) {
                final Sequence r = ref.evalFunction(contextSequence, null,
                        new Sequence[] { i1.nextItem().toSequence(), i2.nextItem().toSequence() });
                result.addAll(r);
            }
        }
		return result;
	}

	private Sequence foldRight(FunctionReference ref, Sequence zero, Sequence seq, Sequence contextSequence) throws XPathException {
		if (seq.isEmpty())
			{return zero;}
		final Sequence head = seq.itemAt(0).toSequence();
		final Sequence tailResult = foldRight(ref, zero, seq.tail(), contextSequence);
		return ref.evalFunction(contextSequence, null, new Sequence[] { head, tailResult });
	}
}
