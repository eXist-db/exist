package org.exist.xquery.functions.system;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.NodeImpl;
import org.exist.memtree.SAXAdapter;
import org.exist.storage.statistics.IndexStatistics;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

public class GetIndexStatistics extends BasicFunction {

    protected final static Logger logger = Logger.getLogger(GetIndexStatistics.class);

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("get-index-statistics", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
        "Internal function",
        null,
        new FunctionParameterSequenceType("results", Type.NODE, Cardinality.ZERO_OR_ONE, "a resource containing the index statistics"));

    public GetIndexStatistics(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	logger.info("Entering " + SystemModule.PREFIX + ":" + getName().getLocalName());
        IndexStatistics index = (IndexStatistics) context.getBroker().getBrokerPool().
                getIndexManager().getIndexById(IndexStatistics.ID);
        if (index == null) {
        	logger.info("Exiting " + SystemModule.PREFIX + ":" + getName().getLocalName());
            // module may not be enabled
            return Sequence.EMPTY_SEQUENCE;
        }

        SAXAdapter adapter = new SAXAdapter(context);
        try {
            adapter.startDocument();
            index.toSAX(adapter);
            adapter.endDocument();
        } catch (SAXException e) {
            throw new XPathException(this, "Error caught while retrieving statistics: " + e.getMessage(), e);
        }
        DocumentImpl doc = (DocumentImpl) adapter.getDocument();
    	logger.info("Exiting " + SystemModule.PREFIX + ":" + getName().getLocalName());
        return (NodeImpl) doc.getFirstChild();
    }
}
