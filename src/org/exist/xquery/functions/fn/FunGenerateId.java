package org.exist.xquery.functions.fn;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class FunGenerateId extends BasicFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("generate-id", Function.BUILTIN_FUNCTION_NS),
            "This function returns a string that uniquely identifies a given node. Without an argument, the node to identify is " +
            "taken from the current context item.",
            null,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "Unique identifier for the node")),
        new FunctionSignature(
            new QName("generate-id", Function.BUILTIN_FUNCTION_NS),
            "This function returns a string that uniquely identifies a given node.",
            new SequenceType[] {
                new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ZERO_OR_ONE,
                    "The node for which an identifier will be generated. If it is the empty sequence, the result will " +
                    "be the empty string")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "Unique identifier for the node"))
    };

    public FunGenerateId(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        NodeValue node = null;
        if (getArgumentCount() == 0) {
            if (contextSequence.isEmpty())
                {throw new XPathException(this, ErrorCodes.XPDY0002, "No context item available in call to generate-id");}
            final Item contextItem = contextSequence.itemAt(0);
            if (Type.subTypeOf(contextItem.getType(), Type.NODE))
                {throw new XPathException(this, ErrorCodes.XPTY0004, "Context item is not a node in call to generate-id");}
            node = (NodeValue) contextItem;
        } else {
            if (args[0].isEmpty())
                {return StringValue.EMPTY_STRING;}
            node = (NodeValue) args[0].itemAt(0);
        }

        final String id = node.getNodeId().toString();
        return new StringValue("N" + id);
    }
}
