package org.exist.xquery.modules.ngram;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.MatchListener;
import org.exist.indexing.ngram.NGramIndex;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.indexing.ngram.NGramMatchCallback;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

/**
 */
public class HighlightMatches extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("filter-matches", NGramModule.NAMESPACE_URI, NGramModule.PREFIX),
            "Highlight matching strings within text nodes that resulted from a ngram search. " +
            "The function takes a sequence of nodes as first argument $a and a callback function (defined with " +
            "util:function) as second parameter $b. Each node in $a will be copied into a new document fragment. " +
            "For each ngram match found while copying a node, the callback function in $b will be called once. The " +
            "callback function should take 2 arguments: 1) the matching text string as xs:string, 2) the node to which this " +
            "text string belongs. The callback function should return zero or more nodes, which will be inserted into the " +
            "resulting node set at the place where the matching text sequence occurred. " +
            "Note: a ngram match on mixed content may span multiple nodes. In this case, the callback function is called " +
            "once for every text node which is part of the matching text sequence.",
            new SequenceType[] {
                    new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                    new SequenceType(Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE));

    public HighlightMatches(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        FunctionReference func = (FunctionReference) args[1].itemAt(0);
        FunctionCall call = func.getFunctionCall();

        NGramIndexWorker index = (NGramIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(NGramIndex.ID);
        MemTreeBuilder builder = context.getDocumentBuilder();
        DocumentBuilderReceiver docBuilder = new DocumentBuilderReceiver(builder);
        MatchCallback matchCb = new MatchCallback(call, docBuilder);
        Serializer serializer = context.getBroker().getSerializer();
        serializer.reset();
        ValueSequence result = new ValueSequence();
        for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
            NodeValue v = (NodeValue) i.nextItem();
            try {
                int nodeNr = builder.getDocument().getLastNode();
                if (v.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                    ((NodeImpl)v).copyTo(context.getBroker(), docBuilder);
                } else {
                    NodeProxy p = (NodeProxy) v;
                    MatchListener ml = index.getMatchListener(p, matchCb);
                    Receiver receiver;
                    if (ml == null)
                        receiver = docBuilder;
                    else {
                        ml.setNextInChain(docBuilder);
                        receiver = ml;
                    }
                    serializer.setReceiver(receiver);
                    serializer.toReceiver((NodeProxy) v, false);
                }
                result.add(builder.getDocument().getNode(++nodeNr));
            } catch (SAXException e) {
                LOG.warn(e.getMessage(), e);
                throw new XPathException(getASTNode(), e.getMessage());
            }
        }
        return result;
    }

    private class MatchCallback implements NGramMatchCallback {
        private FunctionCall callback;
        private DocumentBuilderReceiver docBuilder;

        private MatchCallback(FunctionCall callback, DocumentBuilderReceiver docBuilder) {
            this.callback = callback;
            this.docBuilder = docBuilder;
        }

        public void match(Receiver receiver, String matchingText, NodeProxy node) throws XPathException, SAXException {
            Sequence params[] = {
                    new StringValue(matchingText),
                    node,
                    Sequence.EMPTY_SEQUENCE
            };
            context.pushDocumentContext();
            Sequence seq = callback.evalFunction(null, null, params);
            for (SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                next.copyTo(context.getBroker(), docBuilder);
            }
            context.popDocumentContext();
        }
    }
}
