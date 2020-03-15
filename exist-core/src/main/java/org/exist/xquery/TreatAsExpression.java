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

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;

public class TreatAsExpression extends AbstractExpression {

    private Expression expression;
    private SequenceType type;
    
    public TreatAsExpression(XQueryContext context, Expression expr, SequenceType type) {
        super(context);
        this.expression = expr;
        this.type = type;
    }

    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        return expression.eval(contextSequence, contextItem);
    }

    public int returnsType() {
        return type.getPrimaryType();
    }

    @Override
    public Cardinality getCardinality() {
        return type.getCardinality();
    }
    
    public int getDependencies() {
        return expression.getDependencies();
    }
    
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        expression.analyze(contextInfo);
        
        expression = new DynamicCardinalityCheck(context, type.getCardinality(), expression,
            new Error("XPDY0050", type.toString()));
        expression = new DynamicTypeCheck(context, type.getPrimaryType(), expression);
    }

    public void dump(ExpressionDumper dumper) {
        expression.dump(dumper);
        dumper.display(" treat as ");
        dumper.display(type.toString());
    }

    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        expression.resetState(postOptimization);
    }
    
    public void setContextDocSet(DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        expression.setContextDocSet(contextSet);
    }
}