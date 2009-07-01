package org.exist.storage.util;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.IntegerValue;
import org.exist.dom.QName;

public class PauseFunction extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("pause", TestUtilModule.NAMESPACE_URI),
            "Pause for the specified number of seconds.",
            new SequenceType[] { new FunctionParameterSequenceType("seconds", Type.INT, Cardinality.EXACTLY_ONE, "Seconds to pause.") },
            new SequenceType(Type.ITEM, Cardinality.EMPTY)
        );

    public PauseFunction(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        int t = ((IntegerValue)args[0].itemAt(0)).getInt();
        synchronized (this) {
            try {
                wait(t * 1000);
            } catch (InterruptedException e) {
            }
        }
        return Sequence.EMPTY_SEQUENCE;
    }
}
