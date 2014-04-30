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

import org.exist.interpreter.ContextAtExist;
import org.exist.xquery.AnyNodeTest;
import org.exist.xquery.Constants;
import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.LocationStep;
import org.exist.xquery.PathExpr;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xslt.XSLContext;
import org.exist.xslt.XSLExceptions;
import org.w3c.dom.Attr;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XSLPathExpr extends PathExpr implements XSLExpression {

    public XSLPathExpr(XSLContext context) {
        super(context);
        setToDefaults();
    }

    public XSLContext getXSLContext() {
        return (XSLContext) getContext();
    }

    public void validate() throws XPathException {
        for (int pos = 0; pos < this.getLength(); pos++) {
            Expression expr = this.getExpression(pos);
            if (expr instanceof XSLPathExpr) {
                XSLPathExpr xsl = (XSLPathExpr) expr;
                xsl.validate();
            }
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xslt.instruct.Expression#compileError(java.lang.String)
     */
    public void compileError(String code) throws XPathException {
        throw new XPathException(code);
    }

    /* (non-Javadoc)
     * @see org.exist.xslt.instruct.Expression#compileError(java.lang.String, java.lang.String)
     */
    public void compileError(ErrorCode code, String description) throws XPathException {
        throw new XPathException(this, code, description);
    }

    public Boolean getBoolean(String value) throws XPathException {
        if (value.equals(YES))
            return true;
        else if (value.equals(NO))
            return false;
        compileError(XSLExceptions.ERR_XTSE0020);
        return null;
	}

    protected void _check_(Expression path) {
        for (int index = 0; index < path.getSubExpressionCount(); index++) {
            Expression expr = path.getSubExpression(index);
            if ((index == 0) && (expr instanceof LocationStep)) {
                LocationStep location = (LocationStep) expr;
                if (location.getTest().isWildcardTest())
                    ;
                else if (location.getAxis() == Constants.CHILD_AXIS) {
                    location.setAxis(Constants.SELF_AXIS);
                }
            } else {
                _check_(expr);
            }
        }
    }

    protected void _check_childNodes_(Expression path) {
        if (path.getSubExpressionCount() != 0) {
            Expression expr = path.getSubExpression(path.getSubExpressionCount()-1);
            if (expr instanceof LocationStep) {
                LocationStep location = (LocationStep) expr;
                //TODO: rewrite
                if (location.getAxis() == Constants.ATTRIBUTE_AXIS)
                    ;
                else if (!"node()".equals(location.getTest().toString())) {
                    ((PathExpr)path).add(new LocationStep(getContext(), Constants.CHILD_AXIS, new AnyNodeTest()));
                } else {
                    location.setAxis(Constants.CHILD_AXIS);
                }
            }
        }
    }

    protected void _check_(Expression path, boolean childNodes) {
        _check_(path);
        if (childNodes)
            _check_childNodes_(path);
    }


    public void addText(String text) throws XPathException {
        //UNDERSTAND: what is whitespace? text = StringValue.trimWhitespace(text);
        Text constructor = new Text((XSLContext) getContext(), text);
        add(constructor);
    }

    public void add(SimpleConstructor constructor) {
        steps.add(constructor);
    }

    @Override
    public void setToDefaults() {
        // TODO Auto-generated method stub
    }

    @Override
    public void prepareAttribute(ContextAtExist context, Attr attr) throws XPathException {
        // TODO Auto-generated method stub
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
        Sequence result = null;
        if (steps.size() == 0) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            result = new ValueSequence();
            Sequence currentContext;
            if (contextItem != null)
                currentContext = contextItem.toSequence();
            else
                currentContext = contextSequence;
            for (Expression expr : steps) {
                if (currentContext == null) {
                    result.addAll(expr.eval(null, null));
                    continue;
                }
                //Restore a position which may have been modified by inner expressions 
                int p = context.getContextPosition();
                Sequence seq = context.getContextSequence();
                for (SequenceIterator iterInner = currentContext.iterate();
                        iterInner.hasNext(); p++) {
                    context.setContextSequencePosition(p, seq);
                    result.addAll(expr.eval(currentContext, iterInner.nextItem()));
                }
            }
        }
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);
        return result;
    }

    /**
     * @deprecated Use {@link #process(XSLContext,SequenceIterator)} instead
     */
    public void process(SequenceIterator sequenceIterator, XSLContext context) {
        process(context, sequenceIterator);
    }

    public void process(XSLContext context, SequenceIterator sequenceIterator) {
        for (Expression step : steps) {
            ((XSLPathExpr) step).process(context, sequenceIterator);
        }
    }
}