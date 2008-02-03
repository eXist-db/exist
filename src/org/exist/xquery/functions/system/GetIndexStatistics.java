package org.exist.xquery.functions.system;

import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.storage.statistics.IndexStatistics;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

public class GetIndexStatistics extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("get-index-statistics", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
        "Internal function",
        null,
        new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE));

    public GetIndexStatistics(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        IndexStatistics index = (IndexStatistics) context.getBroker().getBrokerPool().
                getIndexManager().getIndexById(IndexStatistics.ID);
        if (index == null)
            // module may not be enabled
            return Sequence.EMPTY_SEQUENCE;

        SAXAdapter adapter = new SAXAdapter(context);
        try {
            adapter.startDocument();
            index.toSAX(adapter);
            adapter.endDocument();
        } catch (SAXException e) {
            throw new XPathException(getASTNode(), "Error caught while retrieving statistics: " + e.getMessage(), e);
        }
        DocumentImpl doc = (DocumentImpl) adapter.getDocument();
        return (NodeImpl) doc.getFirstChild();
    }
}
