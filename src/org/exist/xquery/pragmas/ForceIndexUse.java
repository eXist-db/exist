/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 */
package org.exist.xquery.pragmas;

import org.exist.xquery.*;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.xquery.value.Sequence;

public class ForceIndexUse extends Pragma {
	
	Expression expression;
	boolean bailout = true;

	public static final QName EXCEPTION_IF_INDEX_NOT_USED_PRAGMA = 
		 new QName("force-index-use", Namespaces.EXIST_NS, "exist");

    public ForceIndexUse(QName qname, String contents) throws XPathException {
    	super(qname, contents);
    }
    
    public void before(XQueryContext context, Expression expression, Sequence contextSequence) throws XPathException {
    }
    
    public void after(XQueryContext context, Expression expression) throws XPathException {
    	expression.accept(new DefaultExpressionVisitor() {
        	public void visitGeneralComparison(GeneralComparison expression) {
        		bailout = !expression.hasUsedIndex();
        	}
        	public void visitBuiltinFunction(Function expression) {
                if (expression instanceof IndexUseReporter)
                	{bailout = !((IndexUseReporter)expression).hasUsedIndex();}
            }
    	});
    	
    	if (bailout)
    		{throw new XPathException(expression, "XQDYxxxx: Can not use index on expression '" + expression + "'");}
        	
    	/*
    	if (expression instanceof PathExpr) {
    		PathExpr pe = (PathExpr)expression;
    		for (Iterator i = pe.steps.iterator(); i.hasNext();) {
                Expression expr = (Expression) i.next();
                if (expr instanceof GeneralComparison) {
                	if (!((GeneralComparison)expr).hasUsedIndex())
                		throw new XPathException(expression, "XQDYxxxx: Can not use index");
                }
                if (expr instanceof FunMatches) {
                	if (!((FunMatches)expr).hasUsedIndex())
                		throw new XPathException(expression, "XQDYxxxx: Can not use index");                	
                } 
            }
    	}
    	*/
    }

}
