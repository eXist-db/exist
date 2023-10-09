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
package org.exist.xquery.pragmas;

import org.exist.xquery.*;
import org.exist.Namespaces;
import org.exist.dom.QName;

public class ForceIndexUse extends AbstractPragma {
    public static final String FORCE_INDEX_USE_PRAGMA_LOCAL_NAME = "force-index-use";
    public static final QName EXCEPTION_IF_INDEX_NOT_USED_PRAGMA =  new QName(FORCE_INDEX_USE_PRAGMA_LOCAL_NAME, Namespaces.EXIST_NS, "exist");

    private boolean bailout = true;

    public ForceIndexUse(final Expression expression, final QName qname, final String contents) {
        super(expression, qname, contents);
    }

    @Override
    public void after(final XQueryContext context, final Expression expression) throws XPathException {
        expression.accept(new DefaultExpressionVisitor() {
            public void visitGeneralComparison(final GeneralComparison expression) {
                bailout = !expression.hasUsedIndex();
            }

            public void visitBuiltinFunction(final Function expression) {
                if (expression instanceof IndexUseReporter) {
                    bailout = !((IndexUseReporter) expression).hasUsedIndex();
                }
            }
        });

        if (bailout) {
            throw new XPathException(expression, "XQDYxxxx: Can not use index on expression '" + expression + "'");
        }
        	
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
