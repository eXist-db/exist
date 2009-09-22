/*
 *  STXTransformerTrigger.java - eXist Open Source Native XML Database
 *  Copyright (C) 2003 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 * $Id$
 *
 */
package org.exist.collections.triggers;

import java.net.URISyntaxException;
import java.util.Map;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TemplatesHandler;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.xml.sax.SAXException;

/**
 * STXTransformerTrigger applies an STX stylesheet to the input SAX stream,
 * using <a href="http://joost.sourceforge.net">Joost</a>. The stylesheet location
 * is identified by parameter "src". If the src parameter is just a path, the stylesheet
 * will be loaded from the database, otherwise, it is interpreted as an URI.
 * 
 * @author wolf
 */
public class STXTransformerTrigger extends FilteringTrigger {

	private Templates template = null;
	private SAXTransformerFactory factory = null;
	private TransformerHandler handler = null;
	
	public void configure(DBBroker broker, Collection parent, Map parameters)
		throws CollectionConfigurationException {
		super.configure(broker, parent, parameters);
		String stylesheet = (String)parameters.get("src");
		if(stylesheet == null)
			throw new CollectionConfigurationException("STXTransformerTrigger requires an " +
				"attribute 'src'");
		String origProperty = System.getProperty("javax.xml.transform.TransformerFactory");
		System.setProperty("javax.xml.transform.TransformerFactory", 
			"net.sf.joost.trax.TransformerFactoryImpl");
		factory = (SAXTransformerFactory)TransformerFactory.newInstance();
		// reset property to previous setting
		if(origProperty != null)
			System.setProperty("javax.xml.transform.TransformerFactory", origProperty);

		getLogger().debug("compiling stylesheet " + stylesheet);
    	XmldbURI stylesheetUri=null;
    	try {
    		stylesheetUri = XmldbURI.xmldbUriFor(stylesheet);
    	} catch(URISyntaxException e) {
     	}
    	//TODO: allow full XmldbURIs to be used as well.
        if(stylesheetUri==null || stylesheet.indexOf(':') == Constants.STRING_NOT_FOUND) {
        	stylesheetUri = parent.getURI().resolveCollectionPath(stylesheetUri);
            DocumentImpl doc;
            try {
				doc = (DocumentImpl)broker.getXMLResource(stylesheetUri);
				if(doc == null)
					throw new CollectionConfigurationException("stylesheet " + stylesheetUri + " not found in database");
				Serializer serializer = broker.getSerializer();
				TemplatesHandler thandler = factory.newTemplatesHandler();
				serializer.setSAXHandlers(thandler, null);
				serializer.toSAX(doc);
				template = thandler.getTemplates();
				handler = factory.newTransformerHandler(template);
			} catch (TransformerConfigurationException e) {
				throw new CollectionConfigurationException(e.getMessage(), e);
			} catch (PermissionDeniedException e) {
				throw new CollectionConfigurationException(e.getMessage(), e);
			} catch (SAXException e) {
				throw new CollectionConfigurationException(e.getMessage(), e);
			}
        } else
			try {
				template = factory.newTemplates(new StreamSource(stylesheet));
				handler = factory.newTransformerHandler(template);
			} catch (TransformerConfigurationException e) {
				throw new CollectionConfigurationException(e.getMessage(), e);
			}
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.Trigger#prepare(java.lang.String, org.w3c.dom.Document)
	 */
	public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentName, DocumentImpl existingDocument) throws TriggerException {
			SAXResult result = new SAXResult();
			result.setHandler(getOutputHandler());
			result.setLexicalHandler(getLexicalOutputHandler());
			handler.setResult(result);
			setOutputHandler(handler);
			setLexicalOutputHandler(handler);
	}

	public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document) {
		// TODO Auto-generated method stub
	}
}
