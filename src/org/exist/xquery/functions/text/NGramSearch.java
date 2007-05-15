package org.exist.xquery.functions.text;

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.NodeSetIterator;
import org.exist.dom.QName;
import org.exist.indexing.impl.NGramIndex;
import org.exist.indexing.impl.NGramIndexWorker;
import org.exist.storage.ElementValue;
import org.exist.xquery.Atomize;
import org.exist.xquery.BasicExpressionVisitor;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.DynamicCardinalityCheck;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.LocationStep;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: 28-Feb-2007
 * Time: 15:18:59
 * To change this template use File | Settings | File Templates.
 */
public class NGramSearch extends Function {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("ngram-contains", TextModule.NAMESPACE_URI, TextModule.PREFIX),
                    "",
                    new SequenceType[]{
                            new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
                    },
                    new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            );

    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected int axis = Constants.UNKNOWN_AXIS;

    public NGramSearch(XQueryContext context) {
        super(context, signature);
    }

    public void setArguments(List arguments) throws XPathException {
        Expression path = (Expression) arguments.get(0);
        steps.add(path);

        Expression arg = (Expression) arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, arg,
                new org.exist.xquery.util.Error(Error.FUNC_PARAM_CARDINALITY, "2", mySignature));
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            arg = new Atomize(context, arg);
        steps.add(arg);

        List steps = BasicExpressionVisitor.findLocationSteps(path);
        if (!steps.isEmpty()) {
            LocationStep firstStep = (LocationStep) steps.get(0);
            LocationStep lastStep = (LocationStep) steps.get(steps.size() - 1);
            NodeTest test = lastStep.getTest();
            if (!test.isWildcardTest() && test.getName() != null) {
                contextQName = new QName(test.getName());
                if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                    contextQName.setNameType(ElementValue.ATTRIBUTE);
                axis = firstStep.getAxis();
                contextStep = lastStep;
            }
        }
    }


    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
			contextSequence = contextItem.toSequence();
        Sequence input = getArgument(0).eval(contextSequence, contextItem);
        NodeSet result = null;
        if (input.isEmpty())
            result = NodeSet.EMPTY_SET;
        else {
            NodeSet inNodes = input.toNodeSet();
            DocumentSet docs = inNodes.getDocumentSet();
            NGramIndexWorker index = (NGramIndexWorker)
              context.getBroker().getIndexController().getIndexWorkerById(NGramIndex.ID);
        	//Alternate design
        	//NGramIndexWorker index = (NGramIndexWorker)context.getBroker().getBrokerPool().getIndexManager().getIndexById(NGramIndex.ID).getWorker();
            
            String key = getArgument(1).eval(contextSequence, contextItem).getStringValue();
            String[] ngrams = index.getDistinctNGrams(key);
            List qnames = null;
            if (contextQName != null) {
                qnames = new ArrayList(1);
                qnames.add(contextQName);
            }
            for (int i = 0; i < ngrams.length; i++) {
                NodeSet nodes = index.search(getExpressionId(), docs, qnames, ngrams[i], context, inNodes, NodeSet.ANCESTOR);
                if (result == null)
                    result = nodes;
                else {
                    NodeSet temp = new ExtArrayNodeSet();
                    for (NodeSetIterator iterator = nodes.iterator(); iterator.hasNext();) {
                        NodeProxy next = (NodeProxy) iterator.next();
                        NodeProxy before = result.get(next);
                        if (before != null) {
                            Match match = null;
                            boolean found = false;
                            Match mb = before.getMatches();
                            while (mb != null && !found) {
                                Match mn = next.getMatches();
                                while (mn != null && !found) {
                                    if ((match = mb.isAfter(mn)) != null) {
                                        found = true;
                                    }
                                    mn = mn.getNextMatch();
                                }
                                mb = mb.getNextMatch();
                            }
                            if (found) {
                                Match m = next.getMatches();
                                next.setMatches(null);
                                while (m != null) {
                                    if (m.getContextId() != getExpressionId())
                                        next.addMatch(m);
                                    m = m.getNextMatch();
                                }
                                next.addMatch(match);
                                temp.add(next);
                            }
                        }
                    }
                    result = temp;
                    if (LOG.isDebugEnabled())
                        LOG.debug("Found " + temp.getLength() + " for: " + ngrams[i]);
                }
            }
        }
        return result;
    }

    public int getDependencies() {
        final Expression stringArg = getArgument(0);
        if (Type.subTypeOf(stringArg.returnsType(), Type.NODE) &&
            !Dependency.dependsOn(stringArg, Dependency.CONTEXT_ITEM)) {
            return Dependency.CONTEXT_SET;
        } else {
            return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
        }
    }

    public int returnsType() {
        return Type.NODE;
    }
}
