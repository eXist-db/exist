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
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.range.*;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.pragmas.*;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
            List<Expression> args = null;
            SequenceConstructor arg0 = null;
            SequenceConstructor arg1 = null;

            List<Predicate> notOptimizable = new ArrayList<Predicate>(preds.size());
            List<RangeIndexConfig> configs = getConfigurations(contextSequence);
            // walk through the predicates attached to the current location step
            // check if expression can be optimized
            for (final Predicate pred : preds) {
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
                    // find a range index configuration matching the full path to the predicate expression
                    RangeIndexConfigElement rice = findConfiguration(path, true, configs);
                    // found index configuration with sub-fields
                    if (rice != null && rice.isComplex() && rice.getNodePath().match(contextPath) && findConfiguration(path, false, configs) == null) {
                        // check for a matching sub-path and retrieve field information
                        RangeIndexConfigField field = ((ComplexRangeIndexConfigElement) rice).getField(path);
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
                // create range:field-equals function
                FieldLookup func = new FieldLookup(context, FieldLookup.signatures[0]);
                func.setFallback(locationStep);
                func.setLocation(locationStep.getLine(), locationStep.getColumn());
                func.setArguments(args);

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
     * Scan all index configurations to find one matching path.
     */
    private RangeIndexConfigElement findConfiguration(NodePath path, boolean complex, List<RangeIndexConfig> configs) {
        for (RangeIndexConfig config : configs) {
            final RangeIndexConfigElement rice = config.find(path);
            if (rice != null && ((complex && rice.isComplex()) ||
                    (!complex && !rice.isComplex()))) {
                return rice;
            }
        }
        return null;
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
