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
package org.exist.collections;

import java.util.Iterator;
import java.util.Map;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.exist.storage.DBBroker;
import org.w3c.dom.Document;

/**
 * @author wolf
 */
public class STXTransformerTrigger extends FilteringTrigger {

	private Templates template = null;
	private SAXTransformerFactory factory = null;
	
	public void configure(Map parameters)
		throws CollectionConfigurationException {
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
		try {
			template = factory.newTemplates(new StreamSource(stylesheet));
		} catch (TransformerConfigurationException e) {
			throw new CollectionConfigurationException(e.getMessage(), e);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.Trigger#prepare(org.exist.storage.DBBroker, org.exist.collections.Collection, java.lang.String, org.w3c.dom.Document)
	 */
	public void prepare(
		DBBroker broker,
		Collection collection,
		String documentName,
		Document existingDocument)
		throws TriggerException {
		TransformerHandler handler;
		try {
			handler = factory.newTransformerHandler(template);
			SAXResult result = new SAXResult();
			result.setHandler(getOutputHandler());
			result.setLexicalHandler(getLexicalOutputHandler());
			handler.setResult(result);
			setOutputHandler(handler);
			setLexicalOutputHandler(handler);
		} catch (TransformerConfigurationException e) {
			throw new TriggerException(e.getMessage(), e);
		}
	}

}
