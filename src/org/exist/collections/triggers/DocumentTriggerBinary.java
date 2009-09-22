/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  $Id: DocumentTrigger.java 7184 2008-01-10 19:54:10Z dizzzz $
 */
package org.exist.collections.triggers;

import org.exist.dom.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

public interface DocumentTriggerBinary extends DocumentTrigger {

    /**
     * This method is called once before the database will actually parse the input data. You may take any action
     * here, using the supplied broker instance.
     * 
     * @param event the type of event that triggered this call (see the constants defined in this interface). The ContentHandler instance for the output.
     * @param broker the database instance used to process the current action.
     * @param transaction the current transaction context
     * @param srcDocumentPath the full absolute path of the document currently processed.
     * @param dstDocumentPath the full absolute path of the target document.
     * @param existingDocument optional: if event is a {@link #UPDATE_DOCUMENT_EVENT},
     *  existingDocument will contain the Document object for the old document, which will be overwritten. Otherwise, the parameter
     *  is null.
     * @throws TriggerException throwing a TriggerException will abort the current action.
     */
    public void prepare(
            int event,
            DBBroker broker,
            Txn transaction,
            XmldbURI srcDocumentPath,
            XmldbURI dstDocumentPath,
            DocumentImpl existingDocument)
            throws TriggerException;

    /**
     * This method is called after the operation completed. At this point, the document has already
     * been stored.
     * 
     * @param event the type of event that triggered this call (see the constants defined in this interface).
     * @param broker the database instance used to process the current action.
     * @param transaction the current transaction context
     * @param srcDocumentPath the full absolute path of the document currently processed.
     * @param dstDocumentPath the full absolute path of the target document.
     * @param document the stored document or null if the document is removed
     **/
    public void finish(
            int event,
            DBBroker broker,
            Txn transaction,
            XmldbURI srcDocumentPath,
            XmldbURI dstDocumentPath,
            DocumentImpl document);
        
}
