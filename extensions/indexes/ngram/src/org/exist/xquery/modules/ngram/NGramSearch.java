/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007-09 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.xquery.modules.ngram;

import org.exist.dom.*;
import org.exist.indexing.ngram.NGramIndex;
import org.exist.indexing.ngram.NGramIndexWorker;
import org.exist.storage.ElementValue;
import org.exist.xquery.*;
import org.exist.xquery.NodeTest;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.List;

public class NGramSearch extends Function implements Optimizable {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("contains", NGramModule.NAMESPACE_URI, NGramModule.PREFIX),
            "Similar to the standard XQuery fn:contains function, but based on the NGram index. " +
            "Searches the given $queryString in the index defined on the input node set $nodes. " +
            "The string may appear at any position within the node content. String comparison " +
            "is case insensitive. Nodes need to have an ngram index to be searched.",
            new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "The input node set to search"),
                new FunctionParameterSequenceType("queryString", Type.STRING, Cardinality.ZERO_OR_ONE,
                    "The exact string to search for")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "a set of nodes from the input node set $nodes containing the query string " +
                "or the empty sequence")
        ),
        new FunctionSignature(
            new QName("ends-with", NGramModule.NAMESPACE_URI, NGramModule.PREFIX),
            "Similar to the standard XQuery fn:ends-with function, but based on the NGram index. " +
            "Searches the given $queryString in the index defined on the input node set $nodes. " +
            "The string has to appear at the end of the node's content. String comparison " +
            "is case insensitive. Nodes need to have an ngram index to be searched.",
            new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "The input node set to search"),
                new FunctionParameterSequenceType("queryString", Type.STRING, Cardinality.ZERO_OR_ONE,
                    "The exact string to search for")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "a set of nodes from the input node set $nodes ending with the query string " +
                "or the empty sequence")
        ),
        new FunctionSignature(
            new QName("starts-with", NGramModule.NAMESPACE_URI, NGramModule.PREFIX),
            "Similar to the standard XQuery fn:starts-with function, but based on the NGram index. " +
            "Searches the given $queryString in the index defined on the input node set $nodes. " +
            "The string may appear at any position within the node content. String comparison " +
            "is case insensitive. Nodes need to have an ngram index to be searched.",
            new SequenceType[] {
                new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE,
                    "The input node set to search"),
                new FunctionParameterSequenceType("queryString", Type.STRING, Cardinality.ZERO_OR_ONE,
                    "The exact string to search for")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "a set of nodes from the input node set $nodes starting with the query string " +
                "or the empty sequence")
        )
    };

    private LocationStep contextStep = null;
    protected QName contextQName = null;
    protected int axis = Constants.UNKNOWN_AXIS;
    private NodeSet preselectResult = null;
    protected boolean optimizeSelf = false;
    
    public NGramSearch(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public void setArguments(List<Expression> arguments) throws XPathException {
        Expression path = arguments.get(0);
        steps.add(path);

        Expression arg = arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, arg,
                new org.exist.xquery.util.Error(Error.FUNC_PARAM_CARDINALITY, "2", mySignature));
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            arg = new Atomize(context, arg);
        steps.add(arg);
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.PathExpr#analyze(org.exist.xquery.Expression)
    */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
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

    public int getOptimizeAxis() {
        return axis;
    }

    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;

        NGramIndexWorker index = (NGramIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(NGramIndex.ID);
        DocumentSet docs = contextSequence.getDocumentSet();
        String key = getArgument(1).eval(contextSequence).getStringValue();
        String[] ngrams = index.getDistinctNGrams(key);
        List<QName> qnames = new ArrayList<QName>(1);
        qnames.add(contextQName);
        preselectResult = processMatches(index, docs, qnames, ngrams, useContext ? contextSequence.toNodeSet() : null,
            NodeSet.DESCENDANT);
        return preselectResult;
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
			contextSequence = contextItem.toSequence();

        NodeSet result;
        if (preselectResult == null) {
            Sequence input = getArgument(0).eval(contextSequence, contextItem);
            if (input.isEmpty())
                result = NodeSet.EMPTY_SET;
            else {
                NodeSet inNodes = input.toNodeSet();
                DocumentSet docs = inNodes.getDocumentSet();
                NGramIndexWorker index = (NGramIndexWorker)
                  context.getBroker().getIndexController().getWorkerByIndexId(NGramIndex.ID);
                //Alternate design
                //NGramIndexWorker index = (NGramIndexWorker)context.getBroker().getBrokerPool().getIndexManager().getIndexById(NGramIndex.ID).getWorker();

                String key = getArgument(1).eval(contextSequence, contextItem).getStringValue();
                String[] ngrams = index.getDistinctNGrams(key);
                List<QName> qnames = null;
                if (contextQName != null) {
                    qnames = new ArrayList<QName>(1);
                    qnames.add(contextQName);
                }
                result = processMatches(index, docs, qnames, ngrams, inNodes, NodeSet.ANCESTOR);
            }
        } else {
            contextStep.setPreloadedData(contextSequence.getDocumentSet(), preselectResult);
            result = getArgument(0).eval(contextSequence).toNodeSet();
        }
        return result;
    }

    private NodeSet processMatches(NGramIndexWorker index, DocumentSet docs, List<QName> qnames, String[] ngrams, NodeSet nodeSet, int axis) throws TerminatedException {
        NodeSet result = null;
        for (int i = 0; i < ngrams.length; i++) {
            long start = System.currentTimeMillis();
            String ngram = ngrams[i];
            if (ngram.length() < index.getN() && i > 0) {
                // if this is the last ngram and its length is too small,
                // fill it up with characters from the previous ngram. too short
                // ngrams lead to a considerable performance loss.
                int fill = index.getN() - ngram.length();
                ngram = ngrams[i - 1].substring(index.getN() - fill) + ngram;
            }
            NodeSet nodes = index.search(getExpressionId(), docs, qnames, ngram, ngrams[i], context, nodeSet, axis);
            if (LOG.isTraceEnabled())
                LOG.trace("Found " + nodes.getLength() + " for " + ngram + " in " +
                    (System.currentTimeMillis() - start));
            if (result == null) {
            	if (isCalledAs("starts-with"))
            		result = startsWith(nodes);
            	else
            		result = nodes;
            } else {
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
            }
        }
        if (isCalledAs("starts-with"))
        	result = startsWith(result);
        else if (isCalledAs("ends-with"))
        	result = endsWith(result);
        return result;
    }

    private NodeSet startsWith(NodeSet nodes) {
    	NodeSet temp = new ExtArrayNodeSet();
        for (NodeSetIterator iterator = nodes.iterator(); iterator.hasNext();) {
            NodeProxy next = (NodeProxy) iterator.next();
            Match mn = next.getMatches();
            while (mn != null) {
            	if (mn.hasMatchAt(0)) {
            		temp.add(next);
            		break;
            	}
            	mn = mn.getNextMatch();
            }
        }
		return temp;
	}

    private NodeSet endsWith(NodeSet nodes) {
    	NodeSet temp = new ExtArrayNodeSet();
    	
    	if (LOG.isDebugEnabled())
    		LOG.debug("Filtering " + nodes.getLength());
        
    	for (NodeSetIterator iterator = nodes.iterator(); iterator.hasNext();) {
            NodeProxy next = (NodeProxy) iterator.next();
            String data = next.getNodeValue();
            int len = data.length();
            Match mn = next.getMatches();
            while (mn != null) {
            	if (mn.hasMatchAround(len)) {
            		temp.add(next);
            		break;
            	}
            	mn = mn.getNextMatch();
            }
        }
		return temp;
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