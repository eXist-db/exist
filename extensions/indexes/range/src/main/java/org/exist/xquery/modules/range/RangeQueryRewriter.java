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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.indexing.range.*;
import org.exist.storage.NodePath;
import org.exist.xquery.*;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;
// static imports for Dependency.* intentionally removed: no longer used
import java.util.*;

/**
 * Query rewriter for the range index. May replace path expressions like a[b = "c"] or a[b = "c"][d = "e"]
 * with either a[range:equals(b, "c")] or range:field-equals(...).
 */
public class RangeQueryRewriter extends QueryRewriter {

    private static final Logger LOG = LogManager.getLogger(RangeQueryRewriter.class);

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
            if (parentExpr instanceof RewritableExpression) {
                // Step 1: replace all optimizable expressions within predicates with
                // calls to the range functions. If those functions are used or not will
                // be decided at run time.

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
                        if (innerExpr instanceof final InternalFunctionCall internalFunctionCall
                                && internalFunctionCall.getFunction() instanceof final Lookup lookup) {

                            // innerExpr was already optimized, just update the contextPath if it is missing
                            if (lookup.getContextPath() == null) {
                                lookup.setContextPath(path);
                            }

                        } else {
                            // replace with call to lookup function
                            final Lookup func = rewrite(innerExpr, path);
                            if (func != null) {
                                // preserve original comparison: may need it for in-memory lookups
                                func.setFallback(innerExpr, axis);
                                func.setLocation(innerExpr.getLine(), innerExpr.getColumn());

                                pred.replace(innerExpr, new InternalFunctionCall(func));
                            }
                        }

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

    protected static @Nullable Lookup rewrite(final Expression expression, final NodePath path) throws XPathException {
        if (expression instanceof final GeneralComparison comparison) {
            final List<Expression> eqArgs = Arrays.asList(comparison.getLeft(), comparison.getRight());
            final Lookup lookup = Lookup.create(comparison.getContext(), getOperator(expression), path);
            if (lookup != null) {
                lookup.setArguments(eqArgs);
            }
            return lookup;
        } else if (expression instanceof final InternalFunctionCall fcall && fcall.getFunction() instanceof final org.exist.xquery.functions.fn.FunMatches funMatches) {
            final int argCount = funMatches.getArgumentCount();
            if (argCount > 3) {
                return null;
            }
            if (argCount == 3) {
                final Expression flagsExpr = funMatches.getArgument(2);
                if (flagsExpr instanceof LiteralValue value) {
                    final String flags = value.getValue().getStringValue();
                    for (int i = 0; i < flags.length(); i++) {
                        if (flags.charAt(i) != 'i') {
                            return null;
                        }
                    }
                }
            }
            final String pattern = getConstantPattern(funMatches.getArgument(1));
            if (pattern != null && !XPathToLuceneRegexTranslator.isTranslatable(pattern)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("fn:matches pattern '{}' not translatable to Lucene; skipping rewrite", pattern);
                }
                return null;
            }
            final List<Expression> matchArgs = argCount >= 3
                    ? Arrays.asList(funMatches.getArgument(0), funMatches.getArgument(1), funMatches.getArgument(2))
                    : Arrays.asList(funMatches.getArgument(0), funMatches.getArgument(1));
            final Lookup lookup = Lookup.create(funMatches.getContext(), RangeIndex.Operator.MATCH, path);
            if (lookup != null) {
                lookup.setArguments(matchArgs);
            }
            return lookup;
        } else if (expression instanceof final InternalFunctionCall fcall) {
            final Function function = fcall.getFunction();
            if (function instanceof final Lookup lookup && lookup.getContextPath() == null) {
                lookup.setContextPath(path);
                return lookup;
            }
        }

        return null;
    }

    protected static List<LocationStep> getStepsToOptimize(Expression expr) {
        if (expr instanceof GeneralComparison comparison) {
            return BasicExpressionVisitor.findLocationSteps(comparison.getLeft());
        } else if (expr instanceof InternalFunctionCall fcall) {
            Function function = fcall.getFunction();
            if (function instanceof org.exist.xquery.functions.fn.FunMatches funMatches) {
                return BasicExpressionVisitor.findLocationSteps(funMatches.getArgument(0));
            }
            if (function instanceof Lookup lookup) {
                if (function.isCalledAs("matches")) {
                    return BasicExpressionVisitor.findLocationSteps(function.getArgument(0));
                } else {
                    Expression original = lookup.getFallback();
                    return getStepsToOptimize(original);
                }
            }
        }
        return null;
    }

    /**
     * Infer the effective {@link RangeIndex.Operator} (EQ, NE, MATCH, etc.) from the given expression.
     * Handles GeneralComparison, direct Lookup calls and fn:matches (including Lookup with a fallback expression).
     */
    public static RangeIndex.Operator getOperator(Expression expr) {
        Expression current = expr;
        if (expr instanceof final InternalFunctionCall fcall) {
            final Function function = fcall.getFunction();
            if (function instanceof final Lookup lookup) {
                final Expression fallback = lookup.getFallback();
                current = Objects.requireNonNullElse(fallback, lookup);
            } else {
                current = function;
            }
        }

        RangeIndex.Operator operator = RangeIndex.Operator.EQ;
        if (current instanceof final GeneralComparison comparison) {
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
        }

        if (current instanceof org.exist.xquery.functions.fn.FunMatches || (current instanceof final Lookup lookup && lookup.isCalledAs("matches"))) {
            operator = RangeIndex.Operator.MATCH;
        }

        return operator;
    }

    /**
     * Extract constant pattern string from an expression when it is effectively a literal.
     * Returns null if the pattern is not a compile-time constant or depends on dynamic context.
     */
    private static @Nullable String getConstantPattern(final Expression expr) {
        try {
            Expression e = expr.simplify();
            // Unwrap common check wrappers (DynamicCardinalityCheck, etc.); guard against cycles
            final Set<Expression> seen = new HashSet<>();
            while (e.getSubExpressionCount() == 1) {
                final Expression next = e.getSubExpression(0);
                if (!seen.add(next)) {
                    return null;
                }
                e = next;
            }
            if (e instanceof LiteralValue literal) {
                return literal.getValue().getStringValue();
            }
            if (e instanceof PathExpr path) {
                final String v = path.getLiteralValue();
                return v.isEmpty() ? null : v;
            }
            // Fallback: eval if expression has no context dependencies
            if ((e.getDependencies() & (Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM | Dependency.VARS)) == 0) {
                final Sequence seq = e.eval(null, null);
                if (seq != null && !seq.isEmpty() && seq.getItemCount() == 1) {
                    return seq.itemAt(0).getStringValue();
                }
            }
        } catch (final XPathException ignored) {
            // not a constant string
        }
        return null;
    }

    protected static NodePath toNodePath(List<LocationStep> steps) {
        NodePath path = new NodePath();
        for (LocationStep step: steps) {
            if (step == null) {
                return null;
            }
            NodeTest test = step.getTest();
            if (test.isWildcardTest() && step.getAxis() == Constants.SELF_AXIS) {
                continue;
            }
            if (!test.isWildcardTest() && test.getName() != null) {
                int axis = step.getAxis();
                if (axis == Constants.DESCENDANT_AXIS || axis == Constants.DESCENDANT_SELF_AXIS) {
                    path.addComponent(NodePath.SKIP);
                } else if (axis == Constants.SELF_AXIS) {
                    // . or self::node() - path is context; no component to add
                    continue;
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
                prevSteps.addFirst(prevStep);
                previous = parent.getPrevious(previous);
            }
        }
        return prevSteps;
    }
}
