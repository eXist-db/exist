package org.exist.xquery.modules.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.lucene.queryParser.ParseException;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.storage.ElementValue;
import org.exist.xquery.AnalyzeContextInfo;
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
import org.exist.xquery.Optimizable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.lucene.LuceneModule;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;

public class Query extends Function implements Optimizable {
	
	protected static final Logger logger = Logger.getLogger(Query.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("query", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Queries a node set using a Lucene full text index; a lucene index " +
            "must already be defined on the nodes, because if no index is available " +
            "on a node, nothing will be found. Indexes on descendant nodes are not " +
            "used. The context of the Lucene query is determined by the given input " +
            "node set. The query is specified either as a query string based on " +
            "Lucene's default query syntax or as an XML fragment. " +
            "See http://exist-db.org/lucene.html#N1029E for complete documentation.",
            new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, 
                		"The node set to search using a Lucene full text index which is defined on those nodes"),
                new FunctionParameterSequenceType("query", Type.ITEM, Cardinality.EXACTLY_ONE, 
                		"The query to search for, provided either as a string or text in Lucene's default query " +
                		"syntax or as an XML fragment to bypass Lucene's default query parser")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "all nodes from the input node set matching the query. match highlighting information " +
                "will be available for all returned nodes. Lucene's match score can be retrieved via " +
                "the ft:score function.")
        );

    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected int axis = Constants.UNKNOWN_AXIS;
    private NodeSet preselectResult = null;
    protected boolean optimizeSelf = false;
    protected boolean optimizeChild = false;

    public Query(XQueryContext context) {
        super(context, signature);
    }

    public void setArguments(List<Expression> arguments) throws XPathException {
        Expression path = arguments.get(0);
        steps.add(path);

        Expression arg = arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, arg,
                new org.exist.xquery.util.Error(org.exist.xquery.util.Error.FUNC_PARAM_CARDINALITY, "2", mySignature));
        steps.add(arg);
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.PathExpr#analyze(org.exist.xquery.Expression)
    */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(new AnalyzeContextInfo(contextInfo));

        List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(getArgument(0));
        if (!steps.isEmpty()) {
            LocationStep firstStep = steps.get(0);
            LocationStep lastStep = steps.get(steps.size() - 1);
            if (steps.size() == 1 && firstStep.getAxis() == Constants.SELF_AXIS) {
                Expression outerExpr = contextInfo.getContextStep();
                if (outerExpr != null && outerExpr instanceof LocationStep) {
                    LocationStep outerStep = (LocationStep) outerExpr;
                    NodeTest test = outerStep.getTest();
                    if (!test.isWildcardTest() && test.getName() != null) {
                        contextQName = new QName(test.getName());
                        if (outerStep.getAxis() == Constants.ATTRIBUTE_AXIS || outerStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                            contextQName.setNameType(ElementValue.ATTRIBUTE);
                        contextStep = firstStep;
                        axis = outerStep.getAxis();
                        optimizeSelf = true;
                    }
                }
            } else {
                NodeTest test = lastStep.getTest();
                if (!test.isWildcardTest() && test.getName() != null) {
                    contextQName = new QName(test.getName());
                    if (lastStep.getAxis() == Constants.ATTRIBUTE_AXIS || lastStep.getAxis() == Constants.DESCENDANT_ATTRIBUTE_AXIS)
                        contextQName.setNameType(ElementValue.ATTRIBUTE);
                    axis = firstStep.getAxis();
                    optimizeChild = steps.size() == 1 &&
                        (axis == Constants.CHILD_AXIS || axis == Constants.ATTRIBUTE_AXIS);
                    contextStep = lastStep;
                }
            }
        }
    }

    public boolean canOptimize(Sequence contextSequence) {
        return contextQName != null;
    }

    public boolean optimizeOnSelf() {
        return optimizeSelf;
    }

    public boolean optimizeOnChild() {
        return optimizeChild;
    }

    public int getOptimizeAxis() {
        return axis;
    }

    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        long start = System.currentTimeMillis();
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        LuceneIndexWorker index = (LuceneIndexWorker)
                context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        DocumentSet docs = contextSequence.getDocumentSet();
        Item key = getKey(contextSequence, null);
        List<QName> qnames = new ArrayList<QName>(1);
        qnames.add(contextQName);
        try {
            if (Type.subTypeOf(key.getType(), Type.ELEMENT))
                preselectResult = index.query(context, getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null,
                    qnames, (Element) ((NodeValue)key).getNode(), NodeSet.DESCENDANT);
            else
                preselectResult = index.query(context, getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null,
                    qnames, key.getStringValue(), NodeSet.DESCENDANT);
        } catch (IOException e) {
            throw new XPathException(this, "Error while querying full text index: " + e.getMessage(), e);
        } catch (ParseException e) {
            throw new XPathException(this, "Error while querying full text index: " + e.getMessage(), e);
        }
        LOG.debug("Lucene query took " + (System.currentTimeMillis() - start));
        return preselectResult;
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	
        if (contextItem != null)
            contextSequence = contextItem.toSequence();

        NodeSet result;
        if (preselectResult == null) {
            Sequence input = getArgument(0).eval(contextSequence);
            if (input.isEmpty())
                result = NodeSet.EMPTY_SET;
            else {
                NodeSet inNodes = input.toNodeSet();
                DocumentSet docs = inNodes.getDocumentSet();
                LuceneIndexWorker index = (LuceneIndexWorker)
                        context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
                Item key = getKey(contextSequence, contextItem);
                List<QName> qnames = null;
                if (contextQName != null) {
                    qnames = new ArrayList<QName>(1);
                    qnames.add(contextQName);
                }
                try {
                    if (Type.subTypeOf(key.getType(), Type.ELEMENT))
                        result = index.query(context, getExpressionId(), docs, inNodes, qnames,
                                (Element)((NodeValue)key).getNode(), NodeSet.ANCESTOR);
                    else
                        result = index.query(context, getExpressionId(), docs, inNodes, qnames,
                                key.getStringValue(), NodeSet.ANCESTOR);
                } catch (IOException e) {
                    throw new XPathException(this, e.getMessage());
                } catch (ParseException e) {
                    throw new XPathException(this, e.getMessage());
                }
            }
        } else {
            contextStep.setPreloadedData(contextSequence.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence).toNodeSet();
        }
        return result;
    }

    private Item getKey(Sequence contextSequence, Item contextItem) throws XPathException {
        Sequence keySeq = getArgument(1).eval(contextSequence, contextItem);
        Item key = keySeq.itemAt(0);
        if (!(Type.subTypeOf(key.getType(), Type.STRING) || Type.subTypeOf(key.getType(), Type.NODE)))
            throw new XPathException(this, "Second argument to ft:query should either be a query string or " +
                    "an XML element describing the query. Found: " + Type.getTypeName(key.getType()));
        return key;
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

