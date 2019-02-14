/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.modules.range;

import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.indexing.range.*;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.*;

/**
 * A pragma which checks if an XPath expression could be replaced with a range field lookup.
 *
 * @author wolf
 */
public class OptimizeFieldPragma extends Pragma {

    public  final static QName OPTIMIZE_RANGE_PRAGMA = new QName("optimize-field", Namespaces.EXIST_NS, "exist");

    private final XQueryContext context;
    private Expression rewritten = null;
    private AnalyzeContextInfo contextInfo;
    private int axis;

    public OptimizeFieldPragma(QName qname, String contents, XQueryContext context) throws XPathException {
        super(qname, contents);
        this.context = context;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        this.contextInfo = contextInfo;
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (rewritten != null) {
            rewritten.analyze(contextInfo);
            return rewritten.eval(contextSequence, contextItem);
        }
        return null;
    }

    @Override
    public void before(XQueryContext context, Expression expression, Sequence contextSequence) throws XPathException {
        LocationStep locationStep = (LocationStep) expression;
        if (locationStep.hasPredicates()) {
            Expression parentExpr = locationStep.getParentExpression();
            if (!(parentExpr instanceof RewritableExpression)) {
                return;
            }
            final List<Predicate> preds = locationStep.getPredicates();

            // get path of path expression before the predicates
            NodePath contextPath = RangeQueryRewriter.toNodePath(RangeQueryRewriter.getPrecedingSteps(locationStep));

            rewritten = tryRewriteToFields(locationStep, preds, contextPath, contextSequence);
            axis = locationStep.getAxis();
        }
    }

    @Override
    public void after(XQueryContext context, Expression expression) throws XPathException {

    }

    private Expression tryRewriteToFields(LocationStep locationStep, List<Predicate> preds, NodePath contextPath, Sequence contextSequence) throws XPathException {
        // without context path, we cannot rewrite the entire query
        if (contextPath != null) {
            List<Predicate> notOptimizable = new ArrayList<Predicate>(preds.size());
            List<RangeIndexConfig> configs = getConfigurations(contextSequence);
            // walk through the predicates attached to the current location step
            // check if expression can be optimized

            final Map<Predicate, List<Expression>> predicateArgs = new IdentityHashMap<Predicate, List<Expression>>(preds.size());

            for (final Predicate pred : preds) {
                List<Expression> args = null;
                SequenceConstructor arg0 = null;
                SequenceConstructor arg1 = null;

                if (pred.getLength() != 1) {
                    // can only optimize predicates with one expression
                    notOptimizable.add(pred);
                    continue;
                }
                Expression innerExpr = pred.getExpression(0);
                List<LocationStep> steps = RangeQueryRewriter.getStepsToOptimize(innerExpr);
                if (steps == null) {
                    notOptimizable.add(pred);
                    continue;
                }
                // compute left hand path
                NodePath innerPath = RangeQueryRewriter.toNodePath(steps);
                if (innerPath == null) {
                    notOptimizable.add(pred);
                    continue;
                }
                NodePath path = new NodePath(contextPath);
                path.append(innerPath);

                if (path.length() > 0) {
                    // find all complex range index configurations matching the full path to the predicate expression
                    final List<ComplexRangeIndexConfigElement> rices = findConfigurations(path, configs);

                    // config with most conditions for path comes first
                    rices.sort(ComplexRangeIndexConfigElement.NUM_CONDITIONS_COMPARATOR);

                    if (rices.isEmpty()) {
                        notOptimizable.add(pred);
                        continue;
                    }

                    // found index configuration with sub-fields

                    ComplexRangeIndexConfigElement rice = null;
                    final List<Predicate> precedingPreds = preds.subList(0, preds.indexOf(pred));
                    final ArrayList<Predicate> matchedPreds = new ArrayList<Predicate>();

                    for (ComplexRangeIndexConfigElement testRice : rices) {

                        if (testRice.getNumberOfConditions() > 0) {
                            // find a config element where the conditions match preceding predicates

                            matchedPreds.clear();

                            for (Predicate precedingPred : precedingPreds ) {

                                if (testRice.findCondition(precedingPred)) {
                                    matchedPreds.add(precedingPred);
                                }

                            }

                            if (matchedPreds.size() == testRice.getNumberOfConditions()) {
                                // all conditions matched
                                rice = testRice;
                                // if any preceding predicates found to be part of a condition for this config
                                // had been matched to another config before, remove them as is is the correct match
                                predicateArgs.keySet().removeAll(matchedPreds);
                                // also do not re-add them after optimizing
                                notOptimizable.removeAll(matchedPreds);

                                break;
                            }

                        } else {
                            // no conditional configs for this node path, take the first one found if any
                            rice = testRice;
                        }
                    }

                    if (rice != null && rice.getNodePath().match(contextPath)) {
                        // check for a matching sub-path and retrieve field information
                        RangeIndexConfigField field = rice.getField(path);
                        if (field != null) {
                            if (args == null) {
                                // initialize args
                                args = new ArrayList<Expression>(4);
                                arg0 = new SequenceConstructor(context);
                                args.add(arg0);
                                arg1 = new SequenceConstructor(context);
                                args.add(arg1);
                            }
                            // field is added to the sequence in first parameter
                            arg0.add(new LiteralValue(context, new StringValue(field.getName())));
                            // operator
                            arg1.add(new LiteralValue(context, new StringValue(RangeQueryRewriter.getOperator(innerExpr).toString())));
                            // append right hand expression as additional parameter
                            args.add(getKeyArg(innerExpr));

                            // store the collected arguments with a reference to the predicate
                            // so they can be removed if a better match is found (if the predicate happens to be
                            // one of the conditions for the following predicate
                            predicateArgs.put(pred, args);
                        } else {
                            notOptimizable.add(pred);
                            continue;
                        }
                    } else {
                        notOptimizable.add(pred);
                        continue;
                    }

                } else {
                    notOptimizable.add(pred);
                    continue;
                }
            }

            if (!predicateArgs.isEmpty()) {

                // the entire filter expression can be replaced
                // create range:field-equals function
                FieldLookup func = new FieldLookup(context, FieldLookup.signatures[0]);
                func.setFallback(locationStep);
                func.setLocation(locationStep.getLine(), locationStep.getColumn());

                if (predicateArgs.size() == 1) {
                    func.setArguments(predicateArgs.entrySet().iterator().next().getValue());
                } else {
                    final List<Expression> mergedArgs = new ArrayList<Expression>(predicateArgs.size() * 4);
                    final SequenceConstructor arg0 = new SequenceConstructor(context);
                    mergedArgs.add(arg0);
                    final SequenceConstructor arg1 = new SequenceConstructor(context);
                    mergedArgs.add(arg1);
                    for (final List<Expression> args : predicateArgs.values()) {
                        arg0.add(args.get(0));
                        arg1.add(args.get(1));
                        mergedArgs.addAll(args.subList(2, args.size()));
                    }

                    func.setArguments(mergedArgs);
                }

                Expression optimizedExpr = new InternalFunctionCall(func);
                if (notOptimizable.size() > 0) {
                    final FilteredExpression filtered = new FilteredExpression(context, optimizedExpr);
                    for (Predicate pred : notOptimizable) {
                        filtered.addPredicate(pred);
                    }
                    optimizedExpr = filtered;
                }

                return optimizedExpr;
            }
        }
        return null;
    }

    private Expression getKeyArg(Expression expression) {
        if (expression instanceof GeneralComparison) {
            return ((GeneralComparison)expression).getRight();
        } else if (expression instanceof InternalFunctionCall) {
            InternalFunctionCall fcall = (InternalFunctionCall) expression;
            Function function = fcall.getFunction();
            if (function instanceof Lookup) {
                return function.getArgument(1);
            }
        }
        return null;
    }


    /**
     * Find all complex configurations matching the path
     */
    private List<ComplexRangeIndexConfigElement> findConfigurations(NodePath path, List<RangeIndexConfig> configs) {
        ArrayList<ComplexRangeIndexConfigElement> rices = new ArrayList<ComplexRangeIndexConfigElement>();

        for (RangeIndexConfig config : configs) {
            List<ComplexRangeIndexConfigElement> foundRices = config.findAll(path);
            if (rices.addAll(foundRices)) {
                break;
            }
        }

        return rices;
    }

    private List<RangeIndexConfig> getConfigurations(Sequence contextSequence) {
        List<RangeIndexConfig> configs = new ArrayList<RangeIndexConfig>();
        for (final Iterator<Collection> i = contextSequence.getCollectionIterator(); i.hasNext(); ) {
            final Collection collection = i.next();
            if (collection.getURI().startsWith(XmldbURI.SYSTEM_COLLECTION_URI)) {
                continue;
            }
            IndexSpec idxConf = collection.getIndexConfiguration(context.getBroker());
            if (idxConf != null) {
                final RangeIndexConfig config = (RangeIndexConfig) idxConf.getCustomIndexSpec(RangeIndex.ID);
                if (config != null) {
                    configs.add(config);
                }
            }
        }
        return configs;
    }
}
