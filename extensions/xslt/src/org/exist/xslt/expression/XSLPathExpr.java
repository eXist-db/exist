/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2009 The eXist Project
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

import org.exist.xquery.AnyNodeTest;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.LocationStep;
import org.exist.xquery.PathExpr;
import org.exist.xquery.TextConstructor;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.StringValue;
import org.exist.xslt.XSLContext;
import org.exist.xslt.XSLExceptions;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class XSLPathExpr extends PathExpr implements XSLExpression {
	
	public XSLPathExpr(XQueryContext context) {
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
	public void compileError(String error) throws XPathException {
		throw new XPathException(error);
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
		for (int pos = 0; pos < path.getLength(); pos++) { // getLength
			Expression expr = path.getExpression(pos);     // getExpression
			if ((pos == 0) && (expr instanceof LocationStep)) {
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
		if (path.getLength() != 0) {    // getLength
			Expression expr = path.getExpression(path.getLength()-1); // 2x
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
		text = StringValue.trimWhitespace(text);
		TextConstructor constructer = new TextConstructor(getContext(), text);
		add(constructer);
	}
}
