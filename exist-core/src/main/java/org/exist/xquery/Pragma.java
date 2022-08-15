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

import org.exist.dom.QName;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

public abstract class Pragma {

    private QName qname;
    private String contents;
    private Expression expression;
    
    public Pragma(QName qname, String contents) throws XPathException {
        this(null, qname, contents);
    }
    
    public Pragma(final Expression expression, QName qname, String contents) throws XPathException {
        this.expression = expression;
        this.qname = qname;
        this.contents = contents;
    }

    public Expression getExpression() {
        return expression;
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    }

    public Sequence eval(Sequence contextSequence, Item contextItem)
    throws XPathException {
        return null;
    }
    
    public abstract void before(XQueryContext context, Sequence contextSequence) throws XPathException;
    
    public abstract void before(XQueryContext context, final Expression expression, Sequence contextSequence) throws XPathException;
    
    public abstract void after(XQueryContext context) throws XPathException;
    
    public abstract void after(XQueryContext context, final Expression expression) throws XPathException;

    protected String getContents() {
        return contents;
    }

    protected QName getQName() {
        return qname;
    }

    public void resetState(boolean postOptimization) {    
    }

    public String toString() {
        return "(# " + qname + ' ' + contents + "#)";
    }
}