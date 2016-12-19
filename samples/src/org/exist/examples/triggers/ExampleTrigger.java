/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.examples.triggers;

import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.xml.sax.InputSource;

/**
 * This trigger maintains a file "contents.xml", containing a list of all
 * documents added to a collection.
 * It uses XUpdate to update "contents.xml" whenever a document is added or
 * removed.
 * Usage
 * Put the collection.xconf document in the selected collection under 
 * /db/system/config/db/, eg /db/system/config/db/test and make sure the 
 * examples.jar is built and available. Put some files into the collection
 * /db/test and see that the contents.xml document gets updated with the
 * files you add or remove.
 * Read the triggers documentation if in need of more information.
 * 
 * @author wolf
 */
public class ExampleTrigger extends FilteringTrigger implements DocumentTrigger {

    protected Logger LOG = LogManager.getLogger(getClass());
    
	private DocumentImpl doc;
	
	/* (non-Javadoc)
	 * @see org.exist.collections.Trigger#configure(org.exist.storage.DBBroker, org.exist.collections.Collection, java.util.Map)
	 */
    @Override
    public void configure(DBBroker broker, Collection parent, Map parameters)
            throws TriggerException {
            super.configure(broker, parent, parameters);
            // the name of the contents file can be set through parameters
            XmldbURI contentsFile = null;
            String contentsName = (String)parameters.get("contents");
            if(contentsName == null) {
                    contentsFile = XmldbURI.create("contents.xml");
            } else {
                    try{
                            contentsFile = XmldbURI.xmldbUriFor(contentsName);
                    } catch(URISyntaxException e) {
                            throw new TriggerException(e);
                    }
            }
            // try to retrieve the contents file
            try {
                this.doc = parent.getDocument(broker, contentsFile);
                if(this.doc == null)
                // doesn't exist yet: create it

                LOG.debug("creating new file for collection contents");
                // IMPORTANT: temporarily disable triggers on the collection.
                // We would end up in infinite recursion if we don't do that
                parent.setTriggersEnabled(false);
                IndexInfo info = parent.validateXMLResource(null, broker, contentsFile, "<?xml version=\"1.0\"?><contents></contents>");
                //TODO : unlock the collection here ?
                parent.store(null, broker, info, "<?xml version=\"1.0\"?><contents></contents>");
                this.doc = info.getDocument();
            } catch (Exception e) {
                throw new TriggerException(e.getMessage(), e);
            } finally {
                parent.setTriggersEnabled(true);
            }
    }

	private void addRecord(DBBroker broker, String xupdate) throws TriggerException {
		LOG.debug(xupdate);
		// create a document set containing "contents.xml"
		DefaultDocumentSet docs = new DefaultDocumentSet();
		docs.add(doc);
		try {
			// IMPORTANT: temporarily disable triggers on the collection.
			// We would end up in infinite recursion if we don't do that
			getCollection().setTriggersEnabled(false);
			// create the XUpdate processor
			XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
			// process the XUpdate
			Modification modifications[] = processor.parse(new InputSource(new StringReader(xupdate)));
			for(int i = 0; i < modifications.length; i++)
				modifications[i].process(null);
			broker.flush();
		} catch (Exception e) {
			e.printStackTrace();
			throw new TriggerException(e.getMessage(), e);
		} finally {
			// IMPORTANT: re-enable trigger processing for the collection.
			getCollection().setTriggersEnabled(true);
		}
	}
	@Override
	public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		String xupdate = "<?xml version=\"1.0\"?>" +
		"<xu:modifications version=\"1.0\" xmlns:xu=\"" + XUpdateProcessor.XUPDATE_NS + "\">" +
		"<xu:append select='/contents'><xu:element name='file'>" +
		uri.toString() +
		"</xu:element></xu:append></xu:modifications>";

		addRecord(broker, xupdate);
	}

	@Override
	public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		String xupdate = "<?xml version=\"1.0\"?>" +
		"<xu:modifications version=\"1.0\" xmlns:xu=\""+ XUpdateProcessor.XUPDATE_NS + "\">" +
		"<xu:remove select=\"//file[text()='" + document.getURI().toString() + "']\"></xu:remove>" +
		"</xu:modifications>";
		
		addRecord(broker, xupdate);
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
