package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
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


	public final static FunctionSignature FN_FILTER =
		new FunctionSignature(
			new QName("filter", Function.BUILTIN_FUNCTION_NS),
			"Returns those items from the sequence $sequence for which the supplied function $function returns true.",
			new SequenceType[] {
				new FunctionParameterSequenceType("sequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "the sequence to filter"),
				new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "the function to call")
			},
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
				"result of filtering the sequence")
	);

	public final static FunctionSignature FN_FOLD_LEFT =
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
	        		"result of the fold-left operation")
		);

	public final static FunctionSignature FN_FOLD_RIGHT =
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
	        		"result of the fold-right operation")
		);

	public final static FunctionSignature FN_APPLY = new FunctionSignature(
			new QName("apply", Function.BUILTIN_FUNCTION_NS),
			"Processes the supplied sequence from right to left, applying the supplied function repeatedly to each " +
					"item in turn, together with an accumulated result value.",
			new SequenceType[] {
				new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "the function to call"),
				new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "an array containing the arguments to pass to the function")
			},
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
				"return value of the function call")
	);
    
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
        if (isCalledAs("for-each")) {
            try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
				ref.analyze(cachedContextInfo);
				for (final SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
					final Item item = i.nextItem();
					final Sequence r = ref.evalFunction(null, null, new Sequence[]{item.toSequence()});
					result.addAll(r);
				}
			}
        } else if (isCalledAs("filter")) {
            final FunctionReference refParam;
            final Sequence seq;
            // Hack: switch parameters for backwards compatibility
            if (Type.subTypeOf(args[1].getItemType(), Type.FUNCTION_REFERENCE)) {
                refParam = (FunctionReference) args[1].itemAt(0);
                seq = args[0];
            } else {
                refParam = (FunctionReference) args[0].itemAt(0);
                seq = args[1];
            }

            try (final FunctionReference ref = refParam) {
				ref.analyze(cachedContextInfo);
				for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
					final Item item = i.nextItem();
					final Sequence r = ref.evalFunction(null, null, new Sequence[]{item.toSequence()});
					if (r.effectiveBooleanValue()) {
						result.add(item);
					}
				}
			}
        } else if (isCalledAs("fold-left")) {
            try (final FunctionReference ref = (FunctionReference) args[2].itemAt(0)) {
				ref.analyze(cachedContextInfo);
				final Sequence seq = args[0];
				final Sequence zero = args[1];
				result = foldLeft(ref, zero, seq.iterate());
			}
        } else if (isCalledAs("fold-right")) {
            try (final FunctionReference ref = (FunctionReference) args[2].itemAt(0)) {
				ref.analyze(cachedContextInfo);
				final Sequence zero = args[1];
				final Sequence seq = args[0];
				if (seq instanceof ValueSequence) {
					result = foldRightNonRecursive(ref, zero, ((ValueSequence) seq).iterateInReverse());
				} else if (seq instanceof RangeSequence) {
					result = foldRightNonRecursive(ref, zero, ((RangeSequence) seq).iterateInReverse());
				} else {
					result = foldRight(ref, zero, seq);
				}
			}
        } else if (isCalledAs("for-each-pair")) {
            try (final FunctionReference ref = (FunctionReference) args[2].itemAt(0)) {
				ref.analyze(cachedContextInfo);
				final SequenceIterator i1 = args[0].iterate();
				final SequenceIterator i2 = args[1].iterate();
				while (i1.hasNext() && i2.hasNext()) {
					final Sequence r = ref.evalFunction(null, null,
							new Sequence[]{i1.nextItem().toSequence(), i2.nextItem().toSequence()});
					result.addAll(r);
				}
			}
        } else if (isCalledAs("apply")) {
			try (final FunctionReference ref = (FunctionReference) args[0].itemAt(0)) {
				ref.analyze(cachedContextInfo);
				final ArrayType array = (ArrayType) args[1].itemAt(0);
				if (!ref.getSignature().isOverloaded() && ref.getSignature().getArgumentCount() != array.getSize()) {
					throw new XPathException(this, ErrorCodes.FOAP0001, "Number of arguments supplied to fn:apply does not match function signature. Expected: " +
							ref.getSignature().getArgumentCount() + ", got: " + array.getSize());
				}
				final Sequence[] fargs = array.toArray();
				return ref.evalFunction(null, null, fargs);
			}
		}
		return result;
	}

    private Sequence foldLeft(final FunctionReference ref, Sequence accum, final SequenceIterator seq) throws XPathException {
        final Sequence refArgs[] = new Sequence[2];
        while(seq.hasNext()) {
            refArgs[0] = accum;
            refArgs[1] = seq.nextItem().toSequence();
            accum = ref.evalFunction(null, null, refArgs);
        }
        return accum;
    }

    /**
     * High performance non-recursive implementation of fold-right
     * relies on the provided iterator moving in reverse
     *
     * @param seq An iterator which moves from right to left
     */
    private Sequence foldRightNonRecursive(final FunctionReference ref, Sequence accum, final SequenceIterator seq) throws XPathException {
        final Sequence refArgs[] = new Sequence[2];
        while (seq.hasNext()) {
            refArgs[0] = seq.nextItem().toSequence();
            refArgs[1] = accum;
            accum = ref.evalFunction(null, null, refArgs);
        }
        return accum;
    }

	private Sequence foldRight(final FunctionReference ref, final Sequence zero, final Sequence seq) throws XPathException {
        if (seq.isEmpty()) {
            return zero;
        }
		final Sequence head = seq.itemAt(0).toSequence();
		final Sequence tailResult = foldRight(ref, zero, seq.tail());
		return ref.evalFunction(null, null, new Sequence[] { head, tailResult });
	}
}
