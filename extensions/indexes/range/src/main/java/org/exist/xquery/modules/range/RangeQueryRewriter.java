/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.range;

import org.exist.indexing.range.*;
import org.exist.storage.NodePath;
import org.exist.xquery.*;
import org.exist.xquery.Constants.Comparison;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Query rewriter for the range index. May replace path expressions like a[b = "c"] or a[b = "c"][d = "e"]
 * with either a[range:equals(b, "c")] or range:field-equals(...).
 */
public class RangeQueryRewriter extends QueryRewriter {

    public RangeQueryRewriter(XQueryContext context) {
        super(context);
    }

    @Override
    public Pragma rewriteLocationStep(final LocationStep locationStep) throws XPathException {
        int axis = locationStep.getAxis();
        if (!(axis == Constants.CHILD_AXIS || axis == Constants.DESCENDANT_AXIS ||
                axis == Constants.DESCENDANT_SELF_AXIS || axis == Constants.ATTRIBUTE_AXIS ||
                axis == Constants.DESCENDANT_ATTRIBUTE_AXIS || axis == Constants.SELF_AXIS)) {
            return null;
        }

        @Nullable final Predicate[] preds = locationStep.getPredicates();
        if (preds != null) {
            final Expression parentExpr = locationStep.getParentExpression();
            if ((parentExpr instanceof RewritableExpression)) {
                // Step 1: replace all optimizable expressions within predicates with
                // calls to the range functions. If those functions are used or not will
                // be decided at run time.

                // will become true if optimizable expression is found
                boolean canOptimize = false;
                // get path of path expression before the predicates
                NodePath contextPath = toNodePath(getPrecedingSteps(locationStep));
                // process the remaining predicates
                for (Predicate pred : preds) {
                    if (pred.getLength() != 1) {
                        // can only optimize predicates with one expression
                        break;
                    }

                    Expression innerExpr = pred.getExpression(0);
                    List<LocationStep> steps = getStepsToOptimize(innerExpr);
                    if (steps == null || steps.isEmpty()) {
                        // no optimizable steps found
                        continue;
                    }

                    // check if inner steps are on an axis we can optimize
                    if (innerExpr instanceof InternalFunctionCall fcall) {
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
                        // replace with call to lookup function
                        // collect arguments
                        Lookup func = rewrite(innerExpr, path);
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
                    return new OptimizeFieldPragma(parentExpr, OptimizeFieldPragma.OPTIMIZE_RANGE_PRAGMA, null, getContext());
                }
            }
        }
        return null;
    }

    protected static Lookup rewrite(Expression expression, NodePath path) throws XPathException {
        ArrayList<Expression> eqArgs = new ArrayList<>(2);
        if (expression instanceof final GeneralComparison comparison) {
            eqArgs.add(comparison.getLeft());
            eqArgs.add(comparison.getRight());
            Lookup func = Lookup.create(comparison.getContext(), getOperator(expression), path);
            if (func != null) {
                func.setArguments(eqArgs);
                return func;
            }

        } else if (expression instanceof final InternalFunctionCall fcall) {
            Function function = fcall.getFunction();
            if (function instanceof Lookup) {
                if (function.isCalledAs("matches")) {
                    eqArgs.add(function.getArgument(0));
                    eqArgs.add(function.getArgument(1));
                    Lookup func = Lookup.create(function.getContext(), RangeIndex.Operator.MATCH, path);
                    func.setArguments(eqArgs);
                    return func;
                }
            }
        }
        return null;
    }

    protected static List<LocationStep> getStepsToOptimize(Expression expr) {
        if (expr instanceof GeneralComparison comparison) {
            return BasicExpressionVisitor.findLocationSteps(comparison.getLeft());
        } else if (expr instanceof InternalFunctionCall fcall) {
            Function function = fcall.getFunction();
            if (function instanceof Lookup) {
                if (function.isCalledAs("matches")) {
                    return BasicExpressionVisitor.findLocationSteps(function.getArgument(0));
                } else {
                    Expression original = ((Lookup)function).getFallback();
                    return getStepsToOptimize(original);
                }
            }
        }
        return null;
    }

    public static RangeIndex.Operator getOperator(Expression expr) {
        if (expr instanceof InternalFunctionCall fcall) {
            Function function = fcall.getFunction();
            if (function instanceof Lookup) {
                expr = ((Lookup)function).getFallback();
            }
        }
        RangeIndex.Operator operator = RangeIndex.Operator.EQ;
        if (expr instanceof GeneralComparison comparison) {
            final Comparison relation = comparison.getRelation();
            operator = switch (relation) {
                case LT -> RangeIndex.Operator.LT;
                case GT -> RangeIndex.Operator.GT;
                case LTEQ -> RangeIndex.Operator.LE;
                case GTEQ -> RangeIndex.Operator.GE;
                case EQ -> switch (comparison.getTruncation()) {
                    case BOTH -> RangeIndex.Operator.CONTAINS;
                    case LEFT -> RangeIndex.Operator.ENDS_WITH;
                    case RIGHT -> RangeIndex.Operator.STARTS_WITH;
                    default -> RangeIndex.Operator.EQ;
                };
                case NEQ -> RangeIndex.Operator.NE;
                default -> operator;
            };
        } else if (expr instanceof InternalFunctionCall fcall) {
            Function function = fcall.getFunction();
            if (function instanceof Lookup && function.isCalledAs("matches")) {
                operator = RangeIndex.Operator.MATCH;
            }
        } else if (expr instanceof Lookup && ((Function)expr).isCalledAs("matches")) {
            operator = RangeIndex.Operator.MATCH;
        }
        return operator;
    }

    protected static NodePath toNodePath(List<LocationStep> steps) {
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

    protected static List<LocationStep> getPrecedingSteps(LocationStep current) {
        Expression parentExpr = current.getParentExpression();
        if (!(parentExpr instanceof RewritableExpression parent)) {
            return null;
        }
        final List<LocationStep> prevSteps = new ArrayList<>();
        prevSteps.add(current);
        Expression previous = parent.getPrevious(current);
        if (previous != null) {
            while (previous != null && previous != parent.getFirst() && previous instanceof LocationStep prevStep) {
                prevSteps.add(0, prevStep);
                previous = parent.getPrevious(previous);
            }
        }
        return prevSteps;
    }
}
