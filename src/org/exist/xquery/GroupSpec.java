/* 
 *  eXist Open Source Native XML Database 
 *  Copyright (C) 2001-06 The eXist Project 
 *  http://exist-db.org 
 *  http://exist.sourceforge.net 
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
package org.exist.xquery; 
 
import org.exist.util.Collations;
import org.exist.xquery.util.ExpressionDumper;
 
/** 
 * A XQuery grouping specifier as specified in an "group by" clause (based on 
 * {@link org.exist.xquery.OrderSpec}). 
 *  
 * Used by {@link org.exist.xquery.BindingExpression}.  
 *
 * @author boris
 * @author Wolfgang
 */ 
 
public class GroupSpec { 
 
    @SuppressWarnings("unused")
	private final XQueryContext context; 
    private Expression expression;
    private String keyVarName = null;
    private String collation = Collations.CODEPOINT;
     
    public GroupSpec(XQueryContext context, Expression groupExpr, String keyVarName) {
        if (groupExpr == null) {
            // Spec: "If the GroupingSpec does not contain an ExprSingle, an implicit
            // expression is created, consisting of a variable reference with the
            // same name as the grouping variable."
            groupExpr = new VariableReference(context, keyVarName);
        }
        this.expression = groupExpr;
        this.context = context; 
        this.keyVarName = keyVarName; 
    } 

    public void setCollation(String collation) {
        this.collation = collation;
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException { 
        expression.analyze(contextInfo); 
    }

    public Expression getGroupExpression() { 
        return expression; 
    } 
     
    public String getKeyVarName(){ 
        return this.keyVarName; 
    } 
     
    public String toString() { 
        final StringBuilder buf = new StringBuilder();
        buf.append(ExpressionDumper.dump(expression)); 
        return buf.toString(); 
    } 
     
    public void resetState(boolean postOptimization) {
        expression.resetState(postOptimization);
    }

    public void replace(Expression oldExpr, Expression newExpr) {
        if (expression == oldExpr) {
            expression = newExpr;
        }
    }
} 
