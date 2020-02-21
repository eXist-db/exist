package org.exist.xquery.functions.fn;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.jcip.annotations.NotThreadSafe;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Optional;
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
                    arity(
                            optParam("seed", Type.ATOMIC, "A seed value for the random generator")
                    )
            )
    );

    public FnRandomNumberGenerator(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        Optional<Long> seed;
        if (args.length < 1) {
            seed = Optional.empty();
        } else {
            final Sequence seedArg = getArgument(0).eval(contextSequence);
            if (seedArg.isEmpty()) {
                seed = Optional.empty();
            } else {
                try {
                    seed = Optional.of(seedArg.convertTo(Type.LONG).toJavaObject(long.class));
                } catch(final XPathException e) {
                    seed = Optional.empty();
                }
            }
        }

        final XORShiftRandom random = seed.map(XORShiftRandom::new).orElseGet(() -> new XORShiftRandom());

        return buildResult(context, random);
    }

    private static MapType buildResult(final XQueryContext context, XORShiftRandom random) throws XPathException {
        // NOTE: we must create a copy so that `Random#nextDouble` does not interfere with multiple `next()` calls on the same random number generator
        random = random.copy();

        final MapType result = new MapType(context);
        result.add(new StringValue("number"), new DoubleValue(random.nextDouble()));
        result.add(new StringValue("next"), nextFunction(context, random));
        result.add(new StringValue("permute"), permuteFunction(context, random));
        return result;
    }

    private static FunctionReference nextFunction(final XQueryContext context, final XORShiftRandom random) {
        final NextFunction nextFunction = new NextFunction(context, random);
        final FunctionCall nextFunctionCall = new FunctionCall(context, nextFunction);
        return new FunctionReference(nextFunctionCall);
    }

    private static FunctionReference permuteFunction(final XQueryContext context, final XORShiftRandom random) {
        final PermuteFunction permuteFunction = new PermuteFunction(context, random);
        final FunctionCall permuteFunctionCall = new FunctionCall(context, permuteFunction);
        return new FunctionReference(permuteFunctionCall);
    }

    private static class NextFunction extends UserDefinedFunction {
        private final XORShiftRandom random;

        public NextFunction(final XQueryContext context, final XORShiftRandom random) {
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
        private final XORShiftRandom random;

        public PermuteFunction(final XQueryContext context, final XORShiftRandom random) {
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

    @NotThreadSafe
    private static class XORShiftRandom extends Random implements Cloneable {
        private long seed;

        public XORShiftRandom() {
            this.seed = System.nanoTime();
        }

        public XORShiftRandom(final long seed) {
            this.seed = seed;
        }

        @Override
        protected int next(final int nbits) {
            long x = this.seed;
            x ^= (x << 21);
            x ^= (x >>> 35);
            x ^= (x << 4);
            this.seed = x;
            x &= ((1L << nbits) -1);
            return (int) x;
        }

        @Override
        public long nextLong() {
            long x = this.seed;
            x ^= (x << 21);
            x ^= (x >>> 35);
            x ^= (x << 4);
            this.seed = x;
            x &= ((1L << 64) -1);
            return x;
        }

        private void writeObject(final ObjectOutputStream out) throws IOException {
            out.writeLong(seed);
        }

        private void readObject(final ObjectInputStream in) throws IOException {
            this.seed = in.readLong();
        }

        private void readObjectNoData() {
            this.seed = System.nanoTime();
        }

        @Override
        protected Object clone() {
            return copy();
        }

        public XORShiftRandom copy() {
            return new XORShiftRandom(seed);
        }
    }
}
