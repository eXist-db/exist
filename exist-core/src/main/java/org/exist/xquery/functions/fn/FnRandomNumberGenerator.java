package org.exist.xquery.functions.fn;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.util.Random;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;

public class FnRandomNumberGenerator extends BasicFunction {

    private static final String FS_RANDOM_NUMBER_GENERATOR_NAME = "random-number-generator";

    private static final FunctionReturnSequenceType FS_RANDOM_NUMBER_GENERATOR_RETURN_TYPE = returns(
            Type.MAP,
            "The function returns a random number generator. " +
            "A random number generator is represented as a map containing three entries. " +
            "The keys of each entry are strings: `number`, `next`, and `permute`.");

    static final FunctionSignature[] FS_RANDOM_NUMBER_GENERATOR = functionSignatures(
            FS_RANDOM_NUMBER_GENERATOR_NAME,
            "Returns a random number generator, which can be used to generate sequences of random numbers.",
            FS_RANDOM_NUMBER_GENERATOR_RETURN_TYPE,
            arities(
                    arity(),
                    arity(param("seed", Type.ATOMIC, "A seed value for the random generator"))
            )
    );

    public FnRandomNumberGenerator(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Random random;
        if (args.length == 1 && !args[0].isEmpty()) {
            random = new Random(args[0].itemAt(0).toJavaObject(long.class));
        } else {
            random = new Random();
        }

        return buildResult(context, random);
    }

    private static MapType buildResult(final XQueryContext context, final Random random) throws XPathException {
        final MapType result = new MapType(context);
        result.add(new StringValue("number"), new DoubleValue(random.nextDouble()));
        result.add(new StringValue("next"), nextFunction(context, random));
        result.add(new StringValue("permute"), permuteFunction(context, random));
        return result;
    }

    private static FunctionReference nextFunction(final XQueryContext context, final Random random) {
        final NextFunction nextFunction = new NextFunction(context, random);
        final FunctionCall nextFunctionCall = new FunctionCall(context, nextFunction);
        return new FunctionReference(nextFunctionCall);
    }

    private static FunctionReference permuteFunction(final XQueryContext context, final Random random) {
        final PermuteFunction permuteFunction = new PermuteFunction(context, random);
        final FunctionCall permuteFunctionCall = new FunctionCall(context, permuteFunction);
        return new FunctionReference(permuteFunctionCall);
    }

    private static class NextFunction extends UserDefinedFunction {
        private final Random random;

        public NextFunction(final XQueryContext context, final Random random) {
            super(context, functionSignature(
                    "random-number-generator-next",
                    "Gets the next random number generator.",
                    FS_RANDOM_NUMBER_GENERATOR_RETURN_TYPE));
            this.random = random;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
            return buildResult(context, random);
        }

        @Override
        public void accept(final ExpressionVisitor visitor) {
            if (visited) {
                return;
            }
            visited = true;
        }
    }

    private static class PermuteFunction extends UserDefinedFunction {
        private final Random random;

        public PermuteFunction(final XQueryContext context, final Random random) {
            super(context, functionSignature(
                    "random-number-generator-permute",
                    "Takes an arbitrary sequence as its argument, and returns a random permutation of that sequence.",
                    returnsOptMany(Type.ITEM),
                    optManyParam("arg", Type.ITEM, "An arbitrary sequence")));
            this.random = random;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
            final Sequence args[] = getCurrentArguments();
            if (args == null || args.length == 0) {
                return Sequence.EMPTY_SEQUENCE;
            }

            final Sequence in = args[0];
            if (in.isEmpty()) {
                return in;
            }

            int remaining = in.getItemCount();

            final IntList availableIndexes = new IntArrayList(remaining);
            for (int i = 0; i < remaining; i++) {
                availableIndexes.add(i);
            }

            final ValueSequence result = new ValueSequence(remaining);
            result.setIsOrdered(true);

            while (remaining > 0) {
                final int x = random.nextInt(remaining);
                final int idx = availableIndexes.removeInt(x);
                result.add(in.itemAt(idx));
                remaining--;
            }

            return result;
        }

        @Override
        public void accept(final ExpressionVisitor visitor) {
            if (visited) {
                return;
            }
            visited = true;
        }
    }
}
