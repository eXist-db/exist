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
package org.exist.xslt;

import org.exist.interpreter.ContextAtExist;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.DBBroker;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;
import javax.xml.transform.Transformer;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XSLContext extends XQueryContext implements ContextAtExist {

	private XSLStylesheet xslStylesheet;

    public XSLContext(DBBroker broker) {
    	super(broker, AccessContext.XSLT);

    	init();
    }

	private void init() {
    	setWatchDog(new XQueryWatchDog(this));
    	
    	//UNDERSTAND: what to do?
    	/*
    	try {
			importModule(XSLTModule.NAMESPACE_URI,
					XSLTModule.PREFIX,
					"java:org.exist.xslt.functions.XSLTModule");
		} catch (XPathException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
	}
	
	public void setXSLStylesheet(XSLStylesheet xsl) {
		this.xslStylesheet = xsl;
	}

	public XSLStylesheet getXSLStylesheet() {
		return xslStylesheet;
	}

	public Transformer getTransformer() {
		return xslStylesheet.getTransformer();
	}
}
