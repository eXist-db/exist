package org.exist.xquery.modules.sort;

import org.exist.EXistException;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.sort.SortIndex;
import org.exist.indexing.sort.SortIndexWorker;
import org.exist.util.LockException;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class RemoveIndex extends BasicFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                new QName("remove-index", SortModule.NAMESPACE_URI, SortModule.PREFIX),
                "Remove a sort index identified by its name.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("id", Type.STRING, Cardinality.EXACTLY_ONE,
                        "The name of the index to be removed.")
                 },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "")),
        new FunctionSignature(
                new QName("remove-index", SortModule.NAMESPACE_URI, SortModule.PREFIX),
                "Remove all sort index entries for the given document.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("id", Type.STRING, Cardinality.EXACTLY_ONE,
                        "The name of the index to be removed."),
                    new FunctionParameterSequenceType("document-node", Type.NODE, Cardinality.EXACTLY_ONE,
                        "A node from the document for which entries should be removed.")
                 },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "")),
    };

    public RemoveIndex(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        SortIndexWorker index = (SortIndexWorker)
            context.getBroker().getIndexController().getWorkerByIndexId(SortIndex.ID);
        String id = args[0].getStringValue();
        try {
            if (getArgumentCount() == 2) {
                NodeValue nv = (NodeValue) args[1].itemAt(0);
                if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE)
                    throw new XPathException(this, "Second argument to remove should be a persistent node, not " +
                        "an in-memory node.");
                NodeProxy proxy = (NodeProxy) nv;
                index.remove(id, proxy.getDocument());
            } else {
                index.remove(id);
            }
        } catch (EXistException e) {
            throw new XPathException(this, e.getMessage(), e);
        } catch (LockException e) {
            throw new XPathException(this, "Caught lock error while removing index. Giving up.", e);
        }

        return Sequence.EMPTY_SEQUENCE;
    }
}
