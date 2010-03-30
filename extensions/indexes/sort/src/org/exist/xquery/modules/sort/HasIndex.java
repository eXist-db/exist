package org.exist.xquery.modules.sort;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.indexing.sort.SortIndex;
import org.exist.indexing.sort.SortIndexWorker;
import org.exist.util.LockException;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

public class HasIndex extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("has-index", SortModule.NAMESPACE_URI, SortModule.PREFIX),
            "Look up a node in the sort index and return a number (&gt; 0) corresponding to the " +
            "position of that node in the ordered set which was created by a previous call to " +
            "the sort:create-index function. The function returns the empty sequence if the node " +
            "cannot be found in the index.",
            new SequenceType[] {
                new FunctionParameterSequenceType("id", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the index.")
             },
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "A number &gt; 0 or the empty " +
            "sequence if the $node argument was empty or the node could not be found in the index."));

    public HasIndex(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String id = args[0].getStringValue();
        SortIndexWorker index = (SortIndexWorker)
            context.getBroker().getIndexController().getWorkerByIndexId(SortIndex.ID);
        try {
            return BooleanValue.valueOf(index.hasIndex(id));
        } catch (EXistException e) {
            throw new XPathException(this, e.getMessage(), e);
        } catch (LockException e) {
            throw new XPathException(this, "Caught lock error while searching index. Giving up.", e);
        }
    }
}
