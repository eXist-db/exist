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
package org.exist.xquery;

import org.exist.Namespaces;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.XMLNames;
import org.exist.xquery.util.*;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.*;

import javax.xml.XMLConstants;


/**
 * XQuery 3.0 computed namespace constructor.
 *
 * @author wolf
 */
public class NamespaceConstructor extends NodeConstructor {

    private Expression qnameExpr;
    private Expression content = null;

    public NamespaceConstructor(final XQueryContext context) {
        super(context);
    }

    public void setContentExpr(final PathExpr path) {
        path.setUseStaticContext(true);
        final Expression expr = new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, path,
                new Error(Error.FUNC_PARAM_CARDINALITY));
        this.content = expr;
    }

    public void setNameExpr(Expression expr) {
        expr = new Atomize(context, expr);
        expr = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, expr,
                new Error(Error.FUNC_PARAM_CARDINALITY));
        this.qnameExpr = expr;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.setParent(this);
        newContextInfo.addFlag(IN_NODE_CONSTRUCTOR);
        qnameExpr.analyze(newContextInfo);
        if (content != null) {
            content.analyze(newContextInfo);
        }
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        final MemTreeBuilder builder = context.getDocumentBuilder();
        context.proceed(this, builder);

        final Sequence prefixSeq = qnameExpr.eval(contextSequence, contextItem);
        if (!(prefixSeq.isEmpty() || Type.subTypeOf(prefixSeq.getItemType(), Type.STRING) || prefixSeq.getItemType() == Type.UNTYPED_ATOMIC)) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "Prefix needs to be xs:string or xs:untypedAtomic");
        }
        String prefix = XMLConstants.DEFAULT_NS_PREFIX;
        if (!prefixSeq.isEmpty()) {
            prefix = prefixSeq.getStringValue();
            if (!(prefix.length() == 0 || XMLNames.isNCName(prefix))) {
                throw new XPathException(this, ErrorCodes.XQDY0074, "Prefix cannot be cast to xs:NCName");
            }
        }

        final Sequence uriSeq = content.eval(contextSequence, contextItem);
        final String value = uriSeq.getStringValue();

        final String inscopeNsUri = context.getInScopeNamespace(prefix);
        if (inscopeNsUri != null && !inscopeNsUri.equals(value)) {
            throw new XPathException(this, ErrorCodes.XQDY0102, "Cannot override already defined ns");
        }

        if (prefix.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
            throw new XPathException(this, ErrorCodes.XQDY0101, "Cannot bind xmlns prefix");
        } else if (prefix.equals(XMLConstants.XML_NS_PREFIX) && !value.equals(Namespaces.XML_NS)) {
            throw new XPathException(this, ErrorCodes.XQDY0101, "Cannot bind xml prefix to another namespace");
        } else if (value.equals(Namespaces.XML_NS) && !"xml".equals(prefix)) {
            throw new XPathException(this, ErrorCodes.XQDY0101, "Cannot bind prefix to XML namespace");
        } else if (value.equals(Namespaces.XMLNS_NS)) {
            throw new XPathException(this, ErrorCodes.XQDY0101, "Cannot bind prefix to xmlns namespace");
        } else if (value.length() == 0) {
            throw new XPathException(this, ErrorCodes.XQDY0101, "Cannot bind prefix to empty or zero-length namespace");
        }

        //context.declareInScopeNamespace(prefix, value);
        final int nodeNr = builder.namespaceNode(prefix, value);
        final Sequence result = builder.getDocument().getNamespaceNode(nodeNr);

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("namespace ");
        //TODO : remove curly braces if Qname
        dumper.display("{");
        qnameExpr.dump(dumper);
        dumper.display("} ");
        dumper.display("{");
        dumper.startIndent();
        if (content != null) {
            content.dump(dumper);
        }
        dumper.endIndent().nl();
        dumper.display("} ");
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append("namespace ");
        //TODO : remove curly braces if Qname
        result.append("{");
        result.append(qnameExpr.toString());
        result.append("} ");
        result.append("{");
        if (content != null) {
            result.append(content.toString());
        }
        result.append("} ");
        return result.toString();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        qnameExpr.resetState(postOptimization);
        if (content != null) {
            content.resetState(postOptimization);
        }
    }
}
