/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.xslt.constructors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xslt.XSLContext;
import org.exist.xslt.expression.XSLPathExpr;
import org.exist.xslt.pattern.Pattern;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class AttributeConstructor extends org.exist.xquery.AttributeConstructor {

	public AttributeConstructor(XQueryContext context, String name) {
		super(context, name);
	}
	
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        newDocumentContext = (contextInfo.getFlags() & IN_NODE_CONSTRUCTOR) == 0;
        
        List<Object> newContents = new ArrayList<Object>(5);
        
        for (Iterator<Object> i = contents.iterator(); i.hasNext();) {
        	Object obj = i.next();
        	if (obj instanceof String) {
				String value = (String) obj;
				if (value.startsWith("{") && value.startsWith("}")) {
					XSLPathExpr expr = new XSLPathExpr((XSLContext) getContext());
	    			Pattern.parse(contextInfo.getContext(), 
	    					value.substring(1, value.length() - 1), 
	    					expr);
	    			newContents.add(expr);
	    			continue;
				}
			}
        	newContents.add(obj);
        }
        
        contents.clear();
        contents.add(newContents);
    }


}
