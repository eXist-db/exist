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
package org.exist.collections.triggers;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
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
public class STXTransformerTrigger extends SAXTrigger implements DocumentTrigger {

    protected Logger LOG = LogManager.getLogger(getClass());
    
    private final SAXTransformerFactory factory = (SAXTransformerFactory)TransformerFactory.newInstance("net.sf.joost.trax.TransformerFactoryImpl", getClass().getClassLoader());
    private TransformerHandler handler = null;

    @Override
    public void configure(DBBroker broker, Txn transaction, Collection parent, Map<String, List<?>> parameters) throws TriggerException {
        super.configure(broker, transaction, parent, parameters);
        final String stylesheet = (String)parameters.get("src").getFirst();
        if(stylesheet == null) {
                throw new TriggerException("STXTransformerTrigger requires an attribute 'src'");
        }

        /*
        String origProperty = System.getProperty("javax.xml.transform.TransformerFactory");
        System.setProperty("javax.xml.transform.TransformerFactory",  "net.sf.joost.trax.TransformerFactoryImpl");
        factory = (SAXTransformerFactory)TransformerFactory.newInstance();
        // reset property to previous setting
        if(origProperty != null) {
                System.setProperty("javax.xml.transform.TransformerFactory", origProperty);
        }
         */

        /*ServiceLoader<TransformerFactory> loader = ServiceLoader.load(TransformerFactory.class);
        for(TransformerFactory transformerFactory : loader) {
            if(transformerFactory.getClass().getName().equals("net.sf.joost.trax.TransformerFactoryImpl")) {
                    factory = transformerFactory.ne
            }
        }*/

        XmldbURI stylesheetUri=null;
        try {
            stylesheetUri = XmldbURI.xmldbUriFor(stylesheet);
        } catch(final URISyntaxException e) {
        }
        //TODO: allow full XmldbURIs to be used as well.
        if(stylesheetUri==null || stylesheet.indexOf(':') == Constants.STRING_NOT_FOUND) {

            stylesheetUri = parent.getURI().resolveCollectionPath(stylesheetUri);
            DocumentImpl doc;
            try {
                doc = (DocumentImpl)broker.getXMLResource(stylesheetUri);
                if(doc == null) {
                    throw new TriggerException("stylesheet " + stylesheetUri + " not found in database");
                }
                if(doc instanceof BinaryDocument) {
                    throw new TriggerException("stylesheet " + stylesheetUri + " must be stored as an xml document and not a binary document!");
                }
                handler = factory.newTransformerHandler(STXTemplatesCache.getInstance().getOrUpdateTemplate(broker, doc));
            } catch (final TransformerConfigurationException | PermissionDeniedException | SAXException | LockException e) {
                    throw new TriggerException(e.getMessage(), e);
            }
        } else {
            try {
                LOG.debug("compiling stylesheet {}", stylesheet);
                final Templates template = factory.newTemplates(new StreamSource(stylesheet));
                handler = factory.newTransformerHandler(template);
            } catch (final TransformerConfigurationException e) {
                throw new TriggerException(e.getMessage(), e);
            }
        }
    }

    private void prepare() {
        //XXX: refactoring required!!!
//        final SAXResult result = new SAXResult();
//        result.setHandler(getOutputHandler());
//        result.setLexicalHandler(getLexicalOutputHandler());
//        handler.setResult(result);
//        setOutputHandler(handler);
//        setLexicalOutputHandler(handler);
    }

	@Override
	public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		prepare();
	}

	@Override
	public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		prepare();
	}

	@Override
	public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		prepare();
	}

	@Override
	public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		prepare();
	}

	@Override
	public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		prepare();
	}

	@Override
	public void afterDeleteDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
	}

	@Override
	public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}


	@Override
	public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}
}
