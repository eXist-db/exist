package org.exist.xquery.modules.range;

import org.exist.indexing.range.*;
import org.exist.storage.NodePath;
import org.exist.xquery.*;
import org.exist.xquery.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Query rewriter for the range index. May replace path expressions like a[b = "c"] or a[b = "c"][d = "e"]
 * with either a[range:equals(b, "c")] or range:field-equals("
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
                if (!(innerExpr instanceof GeneralComparison)) {
                    break;
                }
                GeneralComparison comparison = (GeneralComparison) innerExpr;
                List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(comparison.getLeft());

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
                    RangeIndexConfigElement rice = findConfiguration(path);
                    if (rice != null && !rice.isComplex()) {
                        // found simple index configuration: replace with call to lookup function
                        // collect arguments
                        ArrayList<Expression> eqArgs = new ArrayList<Expression>(2);
                        eqArgs.add(comparison.getLeft());
                        eqArgs.add(comparison.getRight());
                        Lookup func = Lookup.create(comparison.getContext(), comparison.getRelation());
                        // preserve original comparison: may need it for in-memory lookups
                        func.setFallback(comparison);
                        func.setLocation(comparison.getLine(), comparison.getColumn());
                        func.setArguments(eqArgs);
                        // replace comparison with range:eq
                        pred.replace(comparison, new InternalFunctionCall(func));
                    }
                }
            }
        }
        return true;
    }

    private boolean tryRewriteToFields(LocationStep locationStep, RewritableExpression parentExpr, List<Predicate> preds, NodePath contextPath) throws XPathException {
        // without context path, we cannot rewrite the entire query
        if (contextPath != null) {
            int operator = -1;
            List<Expression> args = null;
            SequenceConstructor arg0 = null;

            // walk through the predicates attached to the current location step
            // check if expression can be optimized
            for (final Predicate pred : preds) {
                if (pred.getLength() != 1) {
                    // can only optimize predicates with one expression
                    return false;
                }
                Expression innerExpr = pred.getExpression(0);
                if (!(innerExpr instanceof GeneralComparison)) {
                    return false;
                }
                GeneralComparison comparison = (GeneralComparison) innerExpr;
                List<LocationStep> steps = BasicExpressionVisitor.findLocationSteps(comparison.getLeft());
                // compute left hand path
                NodePath innerPath = toNodePath(steps);
                if (innerPath == null) {
                    return false;
                }
                NodePath path = new NodePath(contextPath);
                path.append(innerPath);

                if (path.length() > 0) {
                    // find a range index configuration matching the full path to the predicate expression
                    RangeIndexConfigElement rice = findConfiguration(path);
                    // found index configuration with sub-fields
                    if (rice != null && rice.isComplex() && rice.getNodePath().match(contextPath)) {
                        // check for a matching sub-path and retrieve field information
                        RangeIndexConfigField field = ((ComplexRangeIndexConfigElement) rice).getField(path);
                        if (field != null) {
                            if (comparison.getRelation() != operator) {
                                if (operator > -1) {
                                    // wrong operator: cannot optimize. break out.
                                    operator = -1;
                                    args = null;
                                    return false;
                                } else {
                                    operator = comparison.getRelation();
                                }
                            }
                            if (args == null) {
                                // initialize args
                                args = new ArrayList<Expression>(4);
                                arg0 = new SequenceConstructor(getContext());
                                args.add(arg0);
                            }
                            // field is added to the sequence in first parameter
                            arg0.add(new LiteralValue(getContext(), new StringValue(field.getName())));
                            // append right hand expression as additional parameter
                            args.add(comparison.getRight());
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            if (args != null) {
                // the entire filter expression can be replaced
                RewritableExpression parent = parentExpr;
                // create range:field-equals function
                FieldLookup func = FieldLookup.create(locationStep.getContext(), operator);
                func.setFallback(locationStep);
                func.setLocation(locationStep.getLine(), locationStep.getColumn());
                func.setArguments(args);
                parent.replace(locationStep, new InternalFunctionCall(func));

                return true;
            }
        }
        return false;
    }

    /**
     * Scan all index configurations to find one matching path.
     */
    private RangeIndexConfigElement findConfiguration(NodePath path) {
        for (Object configObj : configs) {
            final RangeIndexConfig config = (RangeIndexConfig) configObj;
            final RangeIndexConfigElement rice = config.find(path);
            if (rice != null) {
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
