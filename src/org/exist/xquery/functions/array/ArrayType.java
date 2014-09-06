package org.exist.xquery.functions.array;

import com.github.krukow.clj_ds.TransientVector;
import com.github.krukow.clj_lang.*;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implements the array type (XQuery 3.1). An array is also a function. This class thus extends
 * {@link FunctionReference} to allow the item to be called in a dynamic function
 * call.
 */
public class ArrayType extends FunctionReference {

    // the signature of the function which is evaluated if the map is called as a function item
    private static final FunctionSignature ACCESSOR =
        new FunctionSignature(
            new QName("get", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
            "Internal accessor function for arrays.",
            new SequenceType[]{
                new FunctionParameterSequenceType("n", Type.POSITIVE_INTEGER, Cardinality.EXACTLY_ONE, "the position of the item to retrieve from the array")
            },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));

    private InternalFunctionCall accessorFunc;

    private IPersistentVector<Sequence> vector;

    private XQueryContext context;

    public ArrayType(XQueryContext context, List<Sequence> items) {
        this(context);
        vector = PersistentVector.create(items);
    }

    public ArrayType(XQueryContext context, Sequence items) throws XPathException {
        this(context);

        List<Sequence> itemList = new ArrayList<Sequence>(items.getItemCount());
        for (SequenceIterator i = items.iterate(); i.hasNext(); ) {
            itemList.add(i.nextItem().toSequence());
        }
        vector = PersistentVector.create(itemList);
    }

    public ArrayType(XQueryContext context, IPersistentVector<Sequence> vector) {
        this(context);
        this.vector = vector;
    }

    private ArrayType(XQueryContext context) {
        super(null);
        this.context = context;
        final Function fn = new AccessorFunc(context);
        this.accessorFunc = new InternalFunctionCall(fn);
    }

    public Sequence get(int n) {
        return vector.nth(n);
    }

    public Sequence tail() throws XPathException {
        return new ArrayType(context, RT.subvec(vector, 1, vector.length() - 1));
    }

    public ArrayType subarray(int start, int length) throws XPathException {
        return new ArrayType(context, RT.subvec(vector, start, length));
    }

    public ArrayType append(Sequence seq) {
        return new ArrayType(this.context, vector.cons(seq));
    }

    public Sequence asSequence() throws XPathException {
        ValueSequence result = new ValueSequence(vector.length());
        for (int i = 0; i < vector.length(); i++) {
            result.addAll(vector.nth(i));
        }
        return result;
    }

    public int getSize() {
        return vector.length();
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        accessorFunc.analyze(contextInfo);
    }

    @Override
    public Sequence eval(Sequence contextSequence) throws XPathException {
        return accessorFunc.eval(contextSequence);
    }

    @Override
    public void setArguments(List<Expression> arguments) throws XPathException {
        accessorFunc.setArguments(arguments);
    }

    @Override
    public void resetState(boolean postOptimization) {
        accessorFunc.resetState(postOptimization);
    }

    @Override
    public int getType() {
        return Type.ARRAY;
    }

    @Override
    public int getItemType() {
        return Type.ARRAY;
    }

    /**
     * The accessor function which will be evaluated if the map is called
     * as a function item.
     */
    private class AccessorFunc extends BasicFunction {

        public AccessorFunc(XQueryContext context) {
            super(context, ACCESSOR);
        }

        public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
            final IntegerValue v = (IntegerValue) args[0].itemAt(0);
            final int n = v.getInt();
            if (n <= 0 || n > ArrayType.this.getSize()) {
                throw new XPathException(this, ErrorCodes.XQDY0138, "Position " + n + " does not exist in this array. Length is " + ArrayType.this.getSize());
            }
            return ArrayType.this.get(n - 1);
        }
    }
}
