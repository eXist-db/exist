/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xslt.expression;

import org.exist.dom.QName;
import org.exist.interpreter.ContextAtExist;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.util.XMLChar;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Atomize;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xslt.ErrorCodes;
import org.exist.xslt.XSLContext;
import org.exist.xslt.pattern.Pattern;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;

/**
 * <!-- Category: instruction -->
 * <xsl:attribute
 *   name = { qname }
 *   namespace? = { uri-reference }
 *   select? = expression
 *   separator? = { string }
 *   type? = qname
 *   validation? = "strict" | "lax" | "preserve" | "strip">
 *   <!-- Content: sequence-constructor -->
 * </xsl:attribute>
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Attribute extends SimpleConstructor {

    private String name = null;
    private String namespace = null;
    private String select = null;
    private String separator = null;
    private String type = null;
    private String validation = null;

    private String value = null;

    private XSLPathExpr qnameExpr = null;
    private Expression valueExpr = null;

    public Attribute(XSLContext context) {
        super(context);
    }

    public Attribute(XSLContext context, String name) {
        super(context);
        this.name = name;
    }

    public Attribute(XSLContext context, String name, String value) {
        super(context);
        this.name = name;
        this.value = value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setToDefaults() {
        name = null;
        namespace = null;
        select = null;
        separator = null;
        type = null;
        validation = null;
    }

    public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
        String attr_name = attr.getLocalName();
        if (attr_name.equals(NAME)) {
            name = attr.getValue();
        } else if (attr_name.equals(NAMESPACE)) {
            namespace = attr.getValue();
        } else if (attr_name.equals(SELECT)) {
            select = attr.getValue();
        } else if (attr_name.equals(SEPARATOR)) {
            separator = attr.getValue();
        } else if (attr_name.equals(TYPE)) {
            type = attr.getValue();
        } else if (attr_name.equals(VALIDATION)) {
            validation = attr.getValue();
        }
    }

    public void validate() throws XPathException {
        for (int pos = 0; pos < this.getLength(); pos++) {
            Expression expr = this.getExpression(pos);
            if (expr instanceof ValueOf) {
                ValueOf valueOf = (ValueOf) expr;
                valueOf.validate();
                valueOf.sequenceItSelf = true;
            } else if (expr instanceof ApplyTemplates) {
                ApplyTemplates applyTemplates = (ApplyTemplates) expr;
                applyTemplates.validate();
            } else if (expr instanceof If) {
                ((If) expr).validate();
            } else if (expr instanceof Text) {
                Text text = (Text) expr;
                text.validate();
                text.sequenceItSelf = true;
            } else {
                compileError("Unsupported sub-element "+expr);
            }
        }
    }

    public void setContentExpr(Expression expr) {
        this.valueExpr = new Atomize(context, expr);
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        qnameExpr = Pattern.parse(contextInfo.getContext(), name);
        if (qnameExpr != null)
            qnameExpr.analyze(contextInfo);
        valueExpr = Pattern.parse(contextInfo.getContext(), value);
        if (valueExpr != null)
            valueExpr.analyze(contextInfo);
        else if (value == null){
            contextInfo.addFlag(NON_STREAMABLE);
            super.analyze(contextInfo);
        }
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT ITEM", contextItem.toSequence());
        }
        if (newDocumentContext)
            context.pushDocumentContext();
        NodeImpl node;
        try {
            MemTreeBuilder builder = context.getDocumentBuilder();
            builder.setReplaceAttributeFlag(true);
            context.proceed(this, builder);
            QName qn = null;
            String name = null;
            if (qnameExpr != null) {
                Sequence nameSeq = qnameExpr.eval(contextSequence, contextItem);
                if (!nameSeq.hasOne())
                    throw new XPathException(this, "The name expression should evaluate to a single value");
                Item qnItem = nameSeq.itemAt(0);
                if (qnItem.getType() == Type.QNAME)
                    qn = ((QNameValue) qnItem).getQName();
                else
                    name = nameSeq.getStringValue();
            } else {
                name = this.name;
            }
            if (qn == null) {
                //Not in the specs but... makes sense
                if(!XMLChar.isValidName(name))
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                        "'" + name + "' is not a valid attribute name");
                try {
                    qn = QName.parse(context, name, null);
                } catch (IllegalArgumentException e) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                        "'" + name + "' is not a valid attribute name");
                }
            }
            String value = this.value;
            Sequence valueSeq = null;
            if (valueExpr != null) {
	            valueSeq = valueExpr.eval(contextSequence, contextItem);
            } else {
                context.pushDocumentContext();
                valueSeq = super.eval(contextSequence, contextItem);
                context.popDocumentContext();
            }
            if (!valueSeq.isEmpty()) {
                StringBuilder buf = new StringBuilder();
                for(SequenceIterator i = valueSeq.iterate(); i.hasNext(); ) {
                    Item next = i.nextItem();
                    buf.append(next.getStringValue());
                    if(i.hasNext())
                        buf.append(' ');
                }
                value = buf.toString();
            }
            if (value == null)
                value = "";
            node = null;
            try {
                int nodeNo = builder.addAttribute(qn, value);
                node = builder.getDocument().getAttribute(nodeNo);
            } catch (DOMException e) {
                throw new XPathException(this, ErrorCodes.XQDY0025, e.getMessage());
            } 
        } finally {
            if (newDocumentContext)
                context.popDocumentContext();
        }
        if (context.getProfiler().isEnabled())
            context.getProfiler().end(this, "", node);
        return node;
    }

    public boolean allowMixedNodesInReturn() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("<xsl:attribute");

        if (name != null) {
            dumper.display(" name = ");
            dumper.display(name);
        }
        if (namespace != null) {
            dumper.display(" namespace = ");
            dumper.display(namespace);
        }
        if (select != null) {
            dumper.display(" select = ");
            dumper.display(select);
        }
        if (separator != null) {
            dumper.display(" separator = ");
            dumper.display(separator);
        }
        if (type != null) {
            dumper.display(" type = ");
            dumper.display(type);
        }
        if (validation != null) {
            dumper.display(" validation = ");
            dumper.display(validation);
        }
        dumper.display(">");
        super.dump(dumper);
        dumper.display("</xsl:attribute>");
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        //TODO : consider other prefix ! ("xs" is also quite common) -pb
        result.append("<xsl:attribute");
        if (name != null)
            result.append(" name = "+name.toString());
        if (namespace != null)
            result.append(" namespace = "+namespace.toString());
        if (select != null)
            result.append(" select = "+select.toString());
        if (separator != null)
            result.append(" separator = "+separator.toString());
        if (type != null)
            result.append(" type = "+type.toString());
        if (validation != null)
            result.append(" validation = "+validation.toString());
        result.append("> ");
        result.append(super.toString());
        result.append("</xsl:attribute> ");
        return result.toString();
    }
}
