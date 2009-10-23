/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  $Id:$
 */
package org.exist.debugger.dbgp;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.mina.core.session.IoSession;
import org.exist.debugger.DebuggerImpl;
import org.exist.debugger.Response;
import org.exist.memtree.SAXAdapter;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * 
 */
public class ResponseImpl implements Response {

	private IoSession session;
	
	private Document parsedResponse = null;
	
	public ResponseImpl(IoSession session, InputStream inputStream) {
		this.session = session;
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		InputSource src = new InputSource(inputStream);
		SAXParser parser;
		try {
			parser = factory.newSAXParser();
			XMLReader reader = parser.getXMLReader();
			SAXAdapter adapter = new SAXAdapter();
			reader.setContentHandler(adapter);
			reader.parse(src);
			parsedResponse = adapter.getDocument();
		} catch (ParserConfigurationException e) {
		} catch (SAXException e) {
		} catch (IOException e) {
		}
	}
	
	protected boolean isValid() {
		return (parsedResponse != null);
	}
	
	protected DebuggerImpl getDebugger() {
		return (DebuggerImpl) session.getAttribute("debugger");
	}

	public IoSession getSession() {
		return session;
	}

	public String getTransactionID() {
		if (parsedResponse.getFirstChild().getNodeName().equals("init"))
			return "init";
		
		return getAttribute("transaction_id");
	}

	public String getAttribute(String attr) {
		return parsedResponse.getFirstChild().getAttributes().getNamedItem(attr).getNodeValue();
	}

	public String getText() {
		return null;
	}
}
