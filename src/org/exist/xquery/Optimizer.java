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

import org.exist.xquery.functions.ExtFulltext;
import org.exist.xquery.util.ExpressionDumper;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

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
 */
public class Optimizer extends BasicExpressionVisitor {

    private static final Logger LOG = Logger.getLogger(Optimizer.class);

    private XQueryContext context;

    public Optimizer(XQueryContext context) {
        this.context = context;
    }

    public void visitLocationStep(LocationStep locationStep) {
        boolean optimize = false;
        if (locationStep.hasPredicates()) {
            List preds = locationStep.getPredicates();
            for (Iterator i = preds.iterator(); i.hasNext(); ) {
                Predicate pred = (Predicate) i.next();
                FindOptimizable find = new FindOptimizable();
                pred.accept(find);
                optimize = true;
                break;
            }
        }
        if (optimize) {
            Expression parent = locationStep.getParent();
            if (!(parent instanceof PathExpr)) {
                LOG.warn("Parent expression of step is not a PathExpr: " + parent);
                return;
            }
            if (LOG.isTraceEnabled())
                LOG.trace("Rewriting expression: " + ExpressionDumper.dump(locationStep));
            PathExpr path = (PathExpr) parent;
            ExtensionExpression extension = new ExtensionExpression(context);
            try {
                extension.addPragma(new Optimize(context, Optimize.OPTIMIZE_PRAGMA, null));
            } catch (XPathException e) {
                LOG.warn("Failed to optimize expression: " + locationStep + ": " + e.getMessage(), e);
                return;
            }
            extension.setExpression(locationStep);
            path.replaceExpression(locationStep, extension);
        }
    }

    public void visitPathExpr(PathExpr expression) {
        for (int i = 0; i < expression.getLength(); i++) {
            Expression next = expression.getExpression(i);
            next.accept(this);
        }
    }

    public void visitForExpression(ForExpr forExpr) {
        forExpr.getInputSequence().accept(this);
        Expression where = forExpr.getWhereExpression();
        if (where != null)
            where.accept(this);
        forExpr.getReturnExpression().accept(this);
    }

    public void visitLetExpression(LetExpr letExpr) {
        letExpr.getInputSequence().accept(this);
        Expression where = letExpr.getWhereExpression();
        if (where != null)
            where.accept(this);
        letExpr.getReturnExpression().accept(this);
    }

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
            predicate.accept(this);
        }

        public void visitFunction(Function function) {
            if (function instanceof Optimizable) {
                optimizables.add(function);
            }
        }
    }
}
