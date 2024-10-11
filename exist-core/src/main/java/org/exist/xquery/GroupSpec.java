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

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.SequenceType;

import javax.annotation.Nullable;

/** 
 * A XQuery grouping specifier as specified in a "group by" clause (based on
 * {@link org.exist.xquery.OrderSpec}). 
 *  
 * Used by {@link org.exist.xquery.GroupByClause}.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author boris
 * @author Wolfgang
 */ 
 
public class GroupSpec { 

	private final XQueryContext context; 
    private Expression expression;
    private final QName keyVarName;
    @Nullable private SequenceType keyVarType;
    private Collator collator;
     
    public GroupSpec(final XQueryContext context, @Nullable final Expression groupExpr, final QName keyVarName, @Nullable final SequenceType keyVarType) {
        if (groupExpr != null) {
            this.expression = groupExpr;
        } else {
            // Spec: "If the GroupingSpec does not contain an ExprSingle, an implicit
            // expression is created, consisting of a variable reference with the
            // same name as the grouping variable."
            this.expression = new VariableReference(context, keyVarName);
        }

        this.context = context; 
        this.keyVarName = keyVarName;
        this.keyVarType = keyVarType;
        this.collator = context.getDefaultCollator();
    } 

    public void setCollator(final String collation) throws XPathException {
        this.collator = context.getCollator(collation);
    }

    public Collator getCollator() {
        return this.collator;
    }

    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        expression.analyze(contextInfo);
    }

    public Expression getGroupExpression() { 
        return expression; 
    } 
     
    public QName getKeyVarName(){
        return this.keyVarName; 
    }

    public @Nullable SequenceType getKeyVarType() {
        return keyVarType;
    }

    @Override
    public String toString() {
        return "$" + keyVarName + " := " + ExpressionDumper.dump(expression);
    } 
     
    public void resetState(final boolean postOptimization) {
        expression.resetState(postOptimization);
    }

    public void replace(final Expression oldExpr, final Expression newExpr) {
        if (expression == oldExpr) {
            expression = newExpr;
        }
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof GroupSpec && ((GroupSpec)obj).keyVarName.equals(keyVarName);
    }

    @Override
    public int hashCode() {
        return keyVarName.hashCode();
    }
}
