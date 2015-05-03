/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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

import org.exist.indexing.QueryableRangeIndex;
import org.exist.storage.NodePath;
import org.exist.xquery.*;
import org.exist.xquery.functions.fn.FunMatches;
import org.exist.xquery.Constants.Comparison;

import java.util.ArrayList;
import java.util.List;

/**
 * Query rewriter for the range index. May replace path expressions like a[b = "c"] or a[b = "c"][d = "e"]
 * with either a[range:equals(b, "c")] or range:field-equals(...).
 */
public class RangeQueryRewriter extends QueryRewriter {

    public RangeQueryRewriter(final XQueryContext context) {
        super(context);
    }

    @Override
    public Pragma rewriteLocationStep(final LocationStep locationStep) throws XPathException {
        if (locationStep.hasPredicates()) {
            final Expression parentExpr = locationStep.getParentExpression();
            if ((parentExpr instanceof RewritableExpression)) {
                // Step 1: replace all optimizable expressions within predicates with
                // calls to the range functions. If those functions are used or not will
                // be decided at run time.

                final List<Predicate> preds = locationStep.getPredicates();

                // will become true if optimizable expression is found
                boolean canOptimize = false;
                // get path of path expression before the predicates
                final NodePath contextPath = toNodePath(getPrecedingSteps(locationStep));
                // process the remaining predicates
                for (final Predicate pred : preds) {
                    if (pred.getLength() != 1) {
                        // can only optimize predicates with one expression
                        break;
                    }

                    final Expression innerExpr = pred.getExpression(0);
                    final List<LocationStep> steps = getStepsToOptimize(innerExpr);
                    if (steps == null || steps.size() == 0) {
                        // no optimizable steps found
                        continue;
                    }

                    // check if inner steps are on an axis we can optimize
                    final int axis;
                    if (innerExpr instanceof InternalFunctionCall) {
                        final InternalFunctionCall fcall = (InternalFunctionCall) innerExpr;
                        axis = ((Optimizable) fcall.getFunction()).getOptimizeAxis();
                    } else {
                        axis = ((Optimizable) innerExpr).getOptimizeAxis();
                    }
                    if (!(axis == Constants.CHILD_AXIS || axis == Constants.DESCENDANT_AXIS ||
                            axis == Constants.DESCENDANT_SELF_AXIS || axis == Constants.ATTRIBUTE_AXIS ||
                            axis == Constants.DESCENDANT_ATTRIBUTE_AXIS || axis == Constants.SELF_AXIS
                    )) {
                        continue;
                    }

                    // compute left hand path
                    final NodePath innerPath = toNodePath(steps);
                    if (innerPath == null) {
                        continue;
                    }
                    final NodePath path;
                    if (contextPath == null) {
                        path = innerPath;
                    } else {
                        path = new NodePath(contextPath);
                        path.append(innerPath);
                    }

                    if (path.length() > 0) {
                        // replace with call to lookup function
                        // collect arguments
                        final Lookup func = rewrite(innerExpr, path);
                        // preserve original comparison: may need it for in-memory lookups
                        func.setFallback(innerExpr, axis);
                        func.setLocation(innerExpr.getLine(), innerExpr.getColumn());

                        pred.replace(innerExpr, new InternalFunctionCall(func));
                        canOptimize = true;
                    }
                }

                if (canOptimize) {
                    // Step 2: return an OptimizeFieldPragma to handle field lookups and optimize the entire
                    // path expression. If the pragma can optimize the path expression, the original code will
                    // not be called.
                    return new OptimizeFieldPragma(OptimizeFieldPragma.OPTIMIZE_RANGE_PRAGMA, null, getContext());
                }
            }
        }
        return null;
    }

    protected static Lookup rewrite(final Expression expression, final NodePath path) throws XPathException {
        final List<Expression> eqArgs = new ArrayList<>(2);
        if (expression instanceof GeneralComparison) {
            final GeneralComparison comparison = (GeneralComparison) expression;
            eqArgs.add(comparison.getLeft());
            eqArgs.add(comparison.getRight());
            final Lookup func = Lookup.create(comparison.getContext(), getOperator(expression), path);
            func.setArguments(eqArgs);
            return func;
        } else if (expression instanceof InternalFunctionCall) {
            final InternalFunctionCall fcall = (InternalFunctionCall) expression;
            final Function function = fcall.getFunction();
            if (function instanceof Lookup || function instanceof FunMatches) {
                if (function.isCalledAs("matches")) {
                    eqArgs.add(function.getArgument(0));
                    eqArgs.add(function.getArgument(1));

                    final QueryableRangeIndex.Operator op;
                    if(fcall.getArgumentCount() == 3) {
                        final Expression flagsArg = function.getArgument(2);
                        eqArgs.add(flagsArg);
                        op = QueryableRangeIndex.OperatorFactory.match(flagsArg.eval(expression.getContext().getContextSequence()).itemAt(0).getStringValue());
                    } else {
                        op = QueryableRangeIndex.OperatorFactory.MATCHES_EXCLUDING_FLAGS;
                    }

                    final Lookup func = Lookup.create(function.getContext(), op, path);
                    func.setArguments(eqArgs);
                    return func;
                }
            }
        }
        return null;
    }

    protected static List<LocationStep> getStepsToOptimize(final Expression expr) {
        if (expr instanceof GeneralComparison) {
            final GeneralComparison comparison = (GeneralComparison) expr;
            return BasicExpressionVisitor.findLocationSteps(comparison.getLeft());
        } else if (expr instanceof InternalFunctionCall) {
            final InternalFunctionCall fcall = (InternalFunctionCall) expr;
            final Function function = fcall.getFunction();
            if (function instanceof Lookup || function instanceof FunMatches) {
                if (function.isCalledAs("matches")) {
                    return BasicExpressionVisitor.findLocationSteps(function.getArgument(0));
                } else {
                    final Expression original = ((Lookup)function).getFallback();
                    return getStepsToOptimize(original);
                }
            }
        }
        return null;
    }

    protected static QueryableRangeIndex.Operator getOperator(Expression expr) {
        if (expr instanceof InternalFunctionCall) {
            final InternalFunctionCall fcall = (InternalFunctionCall) expr;
            final Function function = fcall.getFunction();
            if (function instanceof Lookup) {
                expr = ((Lookup)function).getFallback();
            }
        }
        QueryableRangeIndex.Operator operator = QueryableRangeIndex.OperatorFactory.EQ;
        if (expr instanceof GeneralComparison) {
            final GeneralComparison comparison = (GeneralComparison) expr;
            final Comparison relation = comparison.getRelation();
            switch(relation) {
                case LT:
                    operator = QueryableRangeIndex.OperatorFactory.LT;
                    break;
                case GT:
                    operator = QueryableRangeIndex.OperatorFactory.GT;
                    break;
                case LTEQ:
                    operator = QueryableRangeIndex.OperatorFactory.LE;
                    break;
                case GTEQ:
                    operator = QueryableRangeIndex.OperatorFactory.GE;
                    break;
                case EQ:
                    switch (comparison.getTruncation()) {
                        case BOTH:
                            operator = QueryableRangeIndex.OperatorFactory.CONTAINS;
                            break;
                        case LEFT:
                            operator = QueryableRangeIndex.OperatorFactory.ENDS_WITH;
                            break;
                        case RIGHT:
                            operator = QueryableRangeIndex.OperatorFactory.STARTS_WITH;
                            break;
                        default:
                            operator = QueryableRangeIndex.OperatorFactory.EQ;
                            break;
                    }
                    break;
                case NEQ:
                    operator = QueryableRangeIndex.OperatorFactory.NE;
                    break;
            }
        } else if (expr instanceof InternalFunctionCall) {
            final InternalFunctionCall fcall = (InternalFunctionCall) expr;
            final Function function = fcall.getFunction();
            if ((function instanceof Lookup || function instanceof FunMatches) && function.isCalledAs("matches")) {
                if(function.getArgumentCount() == 3) {
                    final String flags = function.getArgument(2).eval(expr.getContext().getContextSequence()).itemAt(0).getStringValue();
                    operator = QueryableRangeIndex.OperatorFactory.match(flags);
                } else {
                    operator = QueryableRangeIndex.OperatorFactory.MATCHES_EXCLUDING_FLAGS;
                }
            }
        } else if ((expr instanceof Lookup || expr instanceof FunMatches) && ((Function)expr).isCalledAs("matches")) {
            if(((Function)expr).getArgumentCount() == 3) {
                final String flags = ((Function)expr).getArgument(2).eval(expr.getContext().getContextSequence()).itemAt(0).getStringValue();
                operator = QueryableRangeIndex.OperatorFactory.match(flags);
            } else {
                operator = QueryableRangeIndex.OperatorFactory.MATCHES_EXCLUDING_FLAGS;
            }
        }
        return operator;
    }

    protected static NodePath toNodePath(final List<LocationStep> steps) {
        final NodePath path = new NodePath();
        for (final LocationStep step: steps) {
            if (step == null) {
                return null;
            }
            final NodeTest test = step.getTest();
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

    protected static List<LocationStep> getPrecedingSteps(final LocationStep current) {
        final Expression parentExpr = current.getParentExpression();
        if (!(parentExpr instanceof RewritableExpression)) {
            return null;
        }
        final List<LocationStep> prevSteps = new ArrayList<>();
        prevSteps.add(current);
        final RewritableExpression parent = (RewritableExpression) parentExpr;
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
