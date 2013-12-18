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

import org.exist.indexing.range.*;
import org.exist.storage.NodePath;
import org.exist.xquery.*;
import org.exist.xquery.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Query rewriter for the range index. May replace path expressions like a[b = "c"] or a[b = "c"][d = "e"]
 * with either a[range:equals(b, "c")] or range:field-equals(...).
 */
public class RangeQueryRewriter extends QueryRewriter {

    private final RangeIndexWorker worker;
    private final List<Object> configs;

    public RangeQueryRewriter(RangeIndexWorker worker, List<Object> configs, XQueryContext context) {
        super(context);
        this.worker = worker;
        this.configs = configs;
    }

    @Override
    public boolean rewriteLocationStep(LocationStep locationStep) throws XPathException {
        if (locationStep.hasPredicates()) {
            Expression parentExpr = locationStep.getParentExpression();
            if (!(parentExpr instanceof RewritableExpression)) {
                return true;
            }
            final List<Predicate> preds = locationStep.getPredicates();

            // get path of path expression before the predicates
            NodePath contextPath = toNodePath(getPrecedingSteps(locationStep));

            if (tryRewriteToFields(locationStep, (RewritableExpression) parentExpr, preds, contextPath)) {
                return false;
            }

            // Step 2: process the remaining predicates
            for (Predicate pred : preds) {
                if (pred.getLength() != 1) {
                    // can only optimize predicates with one expression
                    break;
                }

                Expression innerExpr = pred.getExpression(0);
                List<LocationStep> steps = getStepsToOptimize(innerExpr);
                if (steps == null) {
                    // no optimizable steps found
                    continue;
                }
                // compute left hand path
                NodePath innerPath = toNodePath(steps);
                if (innerPath == null) {
                    continue;
                }
                NodePath path;
                if (contextPath == null) {
                    path = innerPath;
                } else {
                    path = new NodePath(contextPath);
                    path.append(innerPath);
                }

                if (path.length() > 0) {
                    // find a range index configuration matching the full path to the predicate expression
                    RangeIndexConfigElement rice = findConfiguration(path, false);
                    if (rice != null && !rice.isComplex()) {
                        // found simple index configuration: replace with call to lookup function
                        // collect arguments
                        Lookup func = rewrite(innerExpr);
                        // preserve original comparison: may need it for in-memory lookups
                        func.setFallback(innerExpr);
                        func.setLocation(innerExpr.getLine(), innerExpr.getColumn());
                        // replace comparison with range:eq
                        pred.replace(innerExpr, new InternalFunctionCall(func));
                    }
                }
            }
        }
        return true;
    }

    private boolean tryRewriteToFields(LocationStep locationStep, RewritableExpression parentExpr, List<Predicate> preds, NodePath contextPath) throws XPathException {
        // without context path, we cannot rewrite the entire query
        if (contextPath != null) {
            List<Expression> args = null;
            SequenceConstructor arg0 = null;
            SequenceConstructor arg1 = null;

            List<Predicate> notOptimizable = new ArrayList<Predicate>(preds.size());

            // walk through the predicates attached to the current location step
            // check if expression can be optimized
            for (final Predicate pred : preds) {
                if (pred.getLength() != 1) {
                    // can only optimize predicates with one expression
                    notOptimizable.add(pred);
                    continue;
                }
                Expression innerExpr = pred.getExpression(0);
                List<LocationStep> steps = getStepsToOptimize(innerExpr);
                if (steps == null) {
                    notOptimizable.add(pred);
                    continue;
                }
                // compute left hand path
                NodePath innerPath = toNodePath(steps);
                if (innerPath == null) {
                    notOptimizable.add(pred);
                    continue;
                }
                NodePath path = new NodePath(contextPath);
                path.append(innerPath);

                if (path.length() > 0) {
                    // find a range index configuration matching the full path to the predicate expression
                    RangeIndexConfigElement rice = findConfiguration(path, true);
                    // found index configuration with sub-fields
                    if (rice != null && rice.isComplex() && rice.getNodePath().match(contextPath) && findConfiguration(path, false) == null) {
                        // check for a matching sub-path and retrieve field information
                        RangeIndexConfigField field = ((ComplexRangeIndexConfigElement) rice).getField(path);
                        if (field != null) {
                            if (args == null) {
                                // initialize args
                                args = new ArrayList<Expression>(4);
                                arg0 = new SequenceConstructor(getContext());
                                args.add(arg0);
                                arg1 = new SequenceConstructor(getContext());
                                args.add(arg1);
                            }
                            // field is added to the sequence in first parameter
                            arg0.add(new LiteralValue(getContext(), new StringValue(field.getName())));
                            // operator
                            arg1.add(new LiteralValue(getContext(), new StringValue(getOperator(innerExpr).toString())));
                            // append right hand expression as additional parameter
                            args.add(getKeyArg(innerExpr));
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
            if (args != null) {
                // the entire filter expression can be replaced
                RewritableExpression parent = parentExpr;
                // create range:field-equals function
                FieldLookup func = new FieldLookup(getContext(), FieldLookup.signatures[0]);
                func.setFallback(locationStep);
                func.setLocation(locationStep.getLine(), locationStep.getColumn());
                func.setArguments(args);

                Expression optimizedExpr = new InternalFunctionCall(func);
                if (notOptimizable.size() > 0) {
                    final FilteredExpression filtered = new FilteredExpression(getContext(), optimizedExpr);
                    for (Predicate pred : notOptimizable) {
                        filtered.addPredicate(pred);
                    }
                    optimizedExpr = filtered;
                }
                parent.replace(locationStep, optimizedExpr);

                return true;
            }
        }
        return false;
    }

    private Lookup rewrite(Expression expression) throws XPathException {
        ArrayList<Expression> eqArgs = new ArrayList<Expression>(2);
        if (expression instanceof GeneralComparison) {
            GeneralComparison comparison = (GeneralComparison) expression;
            eqArgs.add(comparison.getLeft());
            eqArgs.add(comparison.getRight());
            Lookup func = Lookup.create(comparison.getContext(), getOperator(expression));
            func.setArguments(eqArgs);
            return func;
        } else if (expression instanceof InternalFunctionCall) {
            InternalFunctionCall fcall = (InternalFunctionCall) expression;
            Function function = fcall.getFunction();
            if (function instanceof Lookup) {
                if (function.isCalledAs("matches")) {
                    eqArgs.add(function.getArgument(0));
                    eqArgs.add(function.getArgument(1));
                    Lookup func = Lookup.create(function.getContext(), RangeIndex.Operator.MATCH);
                    func.setArguments(eqArgs);
                    return func;
                }
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

    private List<LocationStep> getStepsToOptimize(Expression expr) {
        if (expr instanceof GeneralComparison) {
            GeneralComparison comparison = (GeneralComparison) expr;
            return BasicExpressionVisitor.findLocationSteps(comparison.getLeft());
        } else if (expr instanceof InternalFunctionCall) {
            InternalFunctionCall fcall = (InternalFunctionCall) expr;
            Function function = fcall.getFunction();
            if (function instanceof Lookup) {
                if (function.isCalledAs("matches")) {
                    return BasicExpressionVisitor.findLocationSteps(function.getArgument(0));
                }
            }
        }
        return null;
    }

    private RangeIndex.Operator getOperator(Expression expr) {
        RangeIndex.Operator operator = RangeIndex.Operator.EQ;
        if (expr instanceof GeneralComparison) {
            GeneralComparison comparison = (GeneralComparison) expr;
            int relation = comparison.getRelation();
            switch(relation) {
                case Constants.LT:
                    operator = RangeIndex.Operator.LT;
                    break;
                case Constants.GT:
                    operator = RangeIndex.Operator.GT;
                    break;
                case Constants.LTEQ:
                    operator = RangeIndex.Operator.LE;
                    break;
                case Constants.GTEQ:
                    operator = RangeIndex.Operator.GE;
                    break;
                case Constants.EQ:
                    switch (comparison.getTruncation()) {
                        case Constants.TRUNC_BOTH:
                            operator = RangeIndex.Operator.CONTAINS;
                            break;
                        case Constants.TRUNC_LEFT:
                            operator = RangeIndex.Operator.ENDS_WITH;
                            break;
                        case Constants.TRUNC_RIGHT:
                            operator = RangeIndex.Operator.STARTS_WITH;
                            break;
                        default:
                            operator = RangeIndex.Operator.EQ;
                            break;
                    }
                    break;
            }
        } else if (expr instanceof InternalFunctionCall) {
            InternalFunctionCall fcall = (InternalFunctionCall) expr;
            Function function = fcall.getFunction();
            if (function instanceof Lookup && function.isCalledAs("matches")) {
                operator = RangeIndex.Operator.MATCH;
            }
        }
        return operator;
    }

    /**
     * Scan all index configurations to find one matching path.
     */
    private RangeIndexConfigElement findConfiguration(NodePath path, boolean complex) {
        for (Object configObj : configs) {
            final RangeIndexConfig config = (RangeIndexConfig) configObj;
            final RangeIndexConfigElement rice = config.find(path);
            if (rice != null && ((complex && rice.isComplex()) ||
                    (!complex && !rice.isComplex()))) {
                return rice;
            }
        }
        return null;
    }

    private NodePath toNodePath(List<LocationStep> steps) {
        NodePath path = new NodePath();
        for (LocationStep step: steps) {
            if (step == null) {
                return null;
            }
            NodeTest test = step.getTest();
            if (test.isWildcardTest() && step.getAxis() == Constants.SELF_AXIS) {
                //return path;
                continue;
            }
            if (!test.isWildcardTest() && test.getName() != null) {
                int axis = step.getAxis();
                if (axis == Constants.DESCENDANT_AXIS || axis == Constants.DESCENDANT_SELF_AXIS) {
                    path.addComponent(NodePath.SKIP);
                } else if (axis != Constants.CHILD_AXIS && axis != Constants.ATTRIBUTE_AXIS) {
                    return null;  // not optimizable
                }
                path.addComponent(test.getName());
            }
        }
        return path;
    }

    private List<LocationStep> getPrecedingSteps(LocationStep current) {
        Expression parentExpr = current.getParentExpression();
        if (!(parentExpr instanceof RewritableExpression)) {
            return null;
        }
        final List<LocationStep> prevSteps = new ArrayList<LocationStep>();
        prevSteps.add(current);
        RewritableExpression parent = (RewritableExpression) parentExpr;
        Expression previous = parent.getPrevious(current);
        if (previous != null) {
            while (previous != null && previous != parent.getFirst() && previous instanceof LocationStep) {
                final LocationStep prevStep = (LocationStep) previous;
                prevSteps.add(0, prevStep);
                previous = parent.getPrevious(previous);
            }
        }
        return prevSteps;
    }
}
