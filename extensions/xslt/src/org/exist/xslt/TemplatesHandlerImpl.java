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
package org.exist.xslt;

import javax.xml.transform.Templates;
import javax.xml.transform.sax.TemplatesHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.XPathException;
import org.exist.dom.memtree.SAXAdapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class TemplatesHandlerImpl extends SAXAdapter implements TemplatesHandler {

    private final static Logger LOG = LogManager.getLogger(TemplatesHandlerImpl.class);

    private String systemId = null;
    private Templates templates = null;
	
	protected TemplatesHandlerImpl() {
		super();
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.sax.TemplatesHandler#getSystemId()
	 */
	public String getSystemId() {
		return systemId;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.sax.TemplatesHandler#getTemplates()
	 */
	public Templates getTemplates() {
		if (templates == null) {
	        Document doc = getDocument();
	        Element xsl = doc.getDocumentElement();

	        try {
				templates = XSL.compile(xsl);
			} catch (XPathException e) {
				LOG.debug(e);
				e.printStackTrace();
				System.err.println(e.getDetailMessage()); //TODO: remove
			}
		}
		return templates;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.sax.TemplatesHandler#setSystemId(java.lang.String)
	 */
	public void setSystemId(String systemId) {
		this.systemId = systemId;
	}
}
