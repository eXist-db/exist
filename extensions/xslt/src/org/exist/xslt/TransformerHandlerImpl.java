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

import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.TransformerHandler;

import org.exist.dom.memtree.SAXAdapter;
import org.exist.xquery.XQueryContext;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class TransformerHandlerImpl extends SAXAdapter implements TransformerHandler {
	//XXX: handle SAX events
	private Transformer transformer;
	
	private String SystemId = null;
    private Result result;
	
	protected TransformerHandlerImpl(XSLContext context, Transformer transformer) {
		super((XQueryContext)context);
		this.transformer = transformer;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.sax.TransformerHandler#getSystemId()
	 */
	public String getSystemId() {
		return SystemId;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.sax.TransformerHandler#getTransformer()
	 */
	public Transformer getTransformer() {
		return transformer;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.sax.TransformerHandler#setResult(javax.xml.transform.Result)
	 */
	public void setResult(Result result) throws IllegalArgumentException {
		this.result = result;
	}

	/* (non-Javadoc)
	 * @see javax.xml.transform.sax.TransformerHandler#setSystemId(java.lang.String)
	 */
	public void setSystemId(String systemID) {
		this.SystemId = systemID;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.DTDHandler#notationDecl(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void notationDecl(String name, String publicId, String systemId) throws SAXException {
        throw new RuntimeException("notationDecl(String name, String publicId, String systemId) not implemented on class " + getClass().getName());
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.DTDHandler#unparsedEntityDecl(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	public void unparsedEntityDecl(String name, String publicId, String systemId, String notationName) throws SAXException {
        throw new RuntimeException("unparsedEntityDecl(String name, String publicId, String systemId, String notationName) not implemented on class " + getClass().getName());
	}

}
