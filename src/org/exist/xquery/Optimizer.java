/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
package org.exist.xquery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.exist.xquery.functions.ExtFulltext;
import org.exist.xquery.util.ExpressionDumper;

/**
 * Analyzes the query and marks optimizable expressions for the query engine.
 * This class just searches for potentially optimizable expressions in the query tree and
 * encloses those expressions with an (#exist:optimize#) pragma. The real optimization
 * work is not done by this class but by the pragma (see {@link org.exist.xquery.Optimize}).
 * The pragma may also decide that the optimization is not applicable and just execute
 * the expression without any optimization.
 *
 * Currently, the optimizer is disabled by default. To enable it, set attribute enable-query-rewriting
 * to yes in conf.xml:
 *
 *  &lt;xquery enable-java-binding="no" enable-query-rewriting="yes"&gt;...
 * 
 * To enable/disable the optimizer for a single query, use an option:
 *
 * <pre>declare option exist:optimize "enable=yes|no";</pre>
 *
 */
public class Optimizer extends DefaultExpressionVisitor {

    private static final Logger LOG = Logger.getLogger(Optimizer.class);

    private XQueryContext context;

    private int predicates = 0;

    private boolean hasOptimized = false;
    
    public Optimizer(XQueryContext context) {
        this.context = context;
    }

    public boolean hasOptimized() {
        return hasOptimized;
    }

    public void visitLocationStep(LocationStep locationStep) {
        super.visitLocationStep(locationStep);
        boolean optimize = false;
        // only location steps with predicates can be optimized:
        if (locationStep.hasPredicates()) {
            List preds = locationStep.getPredicates();
            // walk through the predicates attached to the current location step.
            // try to find a predicate containing an expression which is an instance
            // of Optimizable.
            for (Iterator i = preds.iterator(); i.hasNext(); ) {
                Predicate pred = (Predicate) i.next();
                FindOptimizable find = new FindOptimizable();
                pred.accept(find);
                List list = find.getOptimizables();
                if (list.size() > 0 && canOptimize(list)) {
                    optimize = true;
                    break;
                }
            }
        }
        if (optimize) {
            // we found at least one Optimizable. Rewrite the whole expression and
            // enclose it in an (#exist:optimize#) pragma.
            Expression parent = locationStep.getParent();
            if (!(parent instanceof PathExpr)) {
                LOG.warn("Parent expression of step is not a PathExpr: " + parent);
                return;
            }
            if (LOG.isTraceEnabled())
                LOG.trace("Rewriting expression: " + ExpressionDumper.dump(locationStep));
            hasOptimized = true;
            PathExpr path = (PathExpr) parent;
            try {
                // Create the pragma
                ExtensionExpression extension = new ExtensionExpression(context);
                extension.addPragma(new Optimize(context, Optimize.OPTIMIZE_PRAGMA, null, false));
                extension.setExpression(locationStep);
                // Replace the old expression with the pragma
                path.replaceExpression(locationStep, extension);
            } catch (XPathException e) {
                LOG.warn("Failed to optimize expression: " + locationStep + ": " + e.getMessage(), e);
            }
        }
    }

    public void visitAndExpr(OpAnd and) {
        if (predicates > 0) {
            Expression parent = and.getParent();
            if (!(parent instanceof PathExpr)) {
                LOG.warn("Parent expression of boolean operator is not a PathExpr: " + parent);
                return;
            }
            PathExpr path;
            Predicate predicate;
            if (parent instanceof Predicate) {
                predicate = (Predicate) parent;
                path = predicate;
            } else {
                path = (PathExpr) parent;
                parent = path.getParent();
                if (!(parent instanceof Predicate) || path.getLength() > 1) {
                    LOG.warn("Boolean operator is not a top-level expression in the predicate: " + parent.getClass().getName());
                    return;
                }
                predicate = (Predicate) parent;
            }
            if (LOG.isTraceEnabled())
                LOG.trace("Rewriting boolean expression: " + ExpressionDumper.dump(and));
            hasOptimized = true;
            LocationStep step = (LocationStep) predicate.getParent();
            Predicate newPred = new Predicate(context);
            newPred.add(and.getRight());
            step.insertPredicate(predicate, newPred);
            path.replaceExpression(and, and.getLeft());
        }
    }


    public void visitPredicate(Predicate predicate) {
        ++predicates;
        super.visitPredicate(predicate);
        --predicates;
    }

    private boolean canOptimize(List list) {
        for (int j = 0; j < list.size(); j++) {
            Optimizable optimizable = (Optimizable) list.get(j);
            int axis = optimizable.getOptimizeAxis();
            if (!(axis == Constants.CHILD_AXIS || axis == Constants.DESCENDANT_AXIS ||
                    axis == Constants.DESCENDANT_SELF_AXIS || axis == Constants.ATTRIBUTE_AXIS)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Try to find an expression object implementing interface Optimizable.
     */
    private class FindOptimizable extends BasicExpressionVisitor {

        List optimizables = new ArrayList();

        public List getOptimizables() {
            return optimizables;
        }

        public void visitPathExpr(PathExpr expression) {
            for (int i = 0; i < expression.getLength(); i++) {
                Expression next = expression.getExpression(i);
                next.accept(this);
            }
        }

        public void visitFtExpression(ExtFulltext fulltext) {
            optimizables.add(fulltext);
        }

        public void visitGeneralComparison(GeneralComparison comparison) {
            optimizables.add(comparison);
        }

        public void visitPredicate(Predicate predicate) {
            predicate.getExpression(0).accept(this);
        }

        public void visitBuiltinFunction(Function function) {
            if (function instanceof Optimizable) {
                optimizables.add(function);
            }
        }
    }
}
