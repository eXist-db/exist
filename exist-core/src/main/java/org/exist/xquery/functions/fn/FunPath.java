package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import static org.exist.xquery.FunctionDSL.functionSignature;

public class FunPath extends BasicFunction {

    private static final QName FN_PATH_NAME = new QName("path", Function.BUILTIN_FUNCTION_NS);
    private static final String FN_PATH_DESCRIPTION =
            "Returns a path expression that can be used to select the supplied node " +
                    "relative to the root of its containing document.";
    private static final FunctionReturnSequenceType FN_PATH_RETURN = new FunctionReturnSequenceType(
            Type.STRING, Cardinality.ZERO_OR_ONE, "The path expression, or any empty sequence");

    public static final FunctionSignature[] FS_PATH_SIGNATURES = {
            functionSignature(FunPath.FN_PATH_NAME, FunPath.FN_PATH_DESCRIPTION, FunPath.FN_PATH_RETURN),
            functionSignature(FunPath.FN_PATH_NAME, FunPath.FN_PATH_DESCRIPTION, FunPath.FN_PATH_RETURN,
                    new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ZERO_OR_ONE, "The node for which to calculate a path expression"))
    };

    public FunPath(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        return Sequence.EMPTY_SEQUENCE;
    }
}
