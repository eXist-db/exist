/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.util;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * Factory to create new XMLReader objects on demand. The factory is used
 * by {@link org.exist.util.XMLReaderPool}.
 * 
 * @author wolf
 */
public class XMLReaderObjectFactory extends BasePoolableObjectFactory {

	private final static int VALIDATION_ENABLED = 0;
	private final static int VALIDATION_AUTO = 1;
	private final static int VALIDATION_DISABLED = 2;
	
	public static String PROPERTY_VALIDATION = "indexer.validation";
	
	private BrokerPool pool;
	
	/**
	 * 
	 */
	public XMLReaderObjectFactory(BrokerPool pool) {
		super();
		this.pool = pool;
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.pool.BasePoolableObjectFactory#makeObject()
	 */
	public Object makeObject() throws Exception {
		Configuration config = pool.getConfiguration();
		// get validation settings
		int validation = VALIDATION_AUTO;
		String option = (String) config.getProperty(PROPERTY_VALIDATION);
		if (option != null) {
			if (option.equals("true") || option.equals("yes"))
				validation = VALIDATION_ENABLED;
			else if (option.equals("auto"))
				validation = VALIDATION_AUTO;
			else
				validation = VALIDATION_DISABLED;
		}
		// create a SAX parser
		SAXParserFactory saxFactory = SAXParserFactory.newInstance();
		if (validation == VALIDATION_AUTO || validation == VALIDATION_ENABLED)
			saxFactory.setValidating(true);
		else
			saxFactory.setValidating(false);
		saxFactory.setNamespaceAware(true);
		try {
			saxFactory.setFeature("http://xml.org/sax/features/namespace-prefixes", true);
			try {
                                // TODO check does this work?
                                // http://xerces.apache.org/xerces2-j/features.html
				saxFactory.setFeature("http://xml.org/sax/features/validation",
						validation == VALIDATION_AUTO
                                                || validation == VALIDATION_ENABLED);
     
				saxFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
						validation == VALIDATION_AUTO
                                                || validation == VALIDATION_ENABLED);
                                
				saxFactory.setFeature("http://apache.org/xml/features/validation/dynamic",
						validation == VALIDATION_AUTO);
				saxFactory.setFeature("http://apache.org/xml/features/validation/schema",
						validation == VALIDATION_AUTO
						|| validation == VALIDATION_ENABLED);
                                
			} catch (SAXNotRecognizedException e1) {
				// ignore: feature only recognized by xerces
			} catch (SAXNotSupportedException e1) {
				// ignore: feature only recognized by xerces
			}
			SAXParser sax = saxFactory.newSAXParser();
			XMLReader parser = sax.getXMLReader();
			return parser;
		} catch (ParserConfigurationException e) {
			throw new EXistException(e);
		}
	}

}
