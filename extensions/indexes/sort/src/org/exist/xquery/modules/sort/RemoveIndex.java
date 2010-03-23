package org.exist.xquery.modules.sort;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.indexing.sort.SortIndex;
import org.exist.indexing.sort.SortIndexWorker;
import org.exist.util.LockException;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class RemoveIndex extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
                new QName("remove", SortModule.NAMESPACE_URI, SortModule.PREFIX),
                "Remove a sort index identified by its name.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("id", Type.STRING, Cardinality.EXACTLY_ONE,
                        "The name of the index to be removed.")
                 },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, ""));

    public RemoveIndex(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String id = args[0].getStringValue();
        SortIndexWorker index = (SortIndexWorker)
            context.getBroker().getIndexController().getWorkerByIndexId(SortIndex.ID);
        try {
            index.remove(id);
        } catch (EXistException e) {
            throw new XPathException(this, e.getMessage(), e);
        } catch (LockException e) {
            throw new XPathException(this, "Caught lock error while removing index. Giving up.", e);
        }

        return Sequence.EMPTY_SEQUENCE;
    }
}
