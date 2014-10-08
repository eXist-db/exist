package org.exist.xquery.modules.sort;

import org.exist.EXistException;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.sort.SortIndex;
import org.exist.indexing.sort.SortIndexWorker;
import org.exist.util.LockException;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class GetIndex extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("index", SortModule.NAMESPACE_URI, SortModule.PREFIX),
            "Look up a node in the sort index and return a number (&gt; 0) corresponding to the " +
            "position of that node in the ordered set which was created by a previous call to " +
            "the sort:create-index function. The function returns the empty sequence if the node " +
            "cannot be found in the index.",
            new SequenceType[] {
                new FunctionParameterSequenceType("id", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the index."),
                new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ZERO_OR_ONE, "The node to look up.")
             },
        new FunctionReturnSequenceType(Type.LONG, Cardinality.ZERO_OR_ONE, "A number &gt; 0 or the empty " +
            "sequence if the $node argument was empty or the node could not be found in the index."));

    public GetIndex(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[1].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        String id = args[0].getStringValue();
        NodeProxy node = (NodeProxy) args[1].itemAt(0);
        SortIndexWorker index = (SortIndexWorker)
            context.getBroker().getIndexController().getWorkerByIndexId(SortIndex.ID);
        long pos = 0;
        try {
            pos = index.getIndex(id, node);
        } catch (EXistException e) {
            throw new XPathException(this, e.getMessage(), e);
        } catch (LockException e) {
            throw new XPathException(this, "Caught lock error while searching index. Giving up.", e);
        }
        return pos < 0 ? Sequence.EMPTY_SEQUENCE : new IntegerValue(pos, Type.LONG);
    }
}
