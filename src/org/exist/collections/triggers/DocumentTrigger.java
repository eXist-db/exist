/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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
 *  $Id$
 */
package org.exist.collections.triggers;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

import org.xml.sax.ContentHandler;
import org.xml.sax.ext.LexicalHandler;

/**
 * Interface for triggers that react to document-related events.
 * 
 * Document triggers may have two roles:
 * 
 * <ol>
 *  <li>before the document is stored, updated or removed, the trigger's {@link #prepare(int, DBBroker,Txn, XmldbURI, DocumentImpl) prepare} 
 *  method is called. The trigger code may take any action desired, for example, to ensure referential
 *  integrity on the database, issue XUpdate commands on other documents in the database...</li>
 *  <li>the trigger also functions as a filter: the trigger interface extends SAX {@link org.xml.sax.ContentHandler content handler} and
 *  {@link org.xml.sax.ext.LexicalHandler lexical handler}. It will thus receive any SAX events generated by the SAX parser. The default
 *  implementation just forwards the SAX events to the indexer, i.e. the output content handler. However,
 *  a trigger may also alter the received SAX events before it forwards them to the indexer, for example,
 *  by applying a stylesheet.</li>
 * </ol>
 * 
 * The DocumentTrigger interface is also called for binary resources. However, in this case, the trigger can not function as
 * a filter and the SAX-related methods are useless. Only {@link #prepare(int, DBBroker, Txn, XmldbURI, DocumentImpl)} and
 * {@link #finish(int, DBBroker, Txn, XmldbURI, DocumentImpl)} will be called. To determine if the document is a binary resource,
 * call {@link org.exist.dom.DocumentImpl#getResourceType()}.
 * 
 * The general contract for a trigger is as follows:
 * 
 * <ol>
 *  <li>configuration phase: whenever the collection loads its configuration file, the trigger's 
 *  {@link #configure(DBBroker, Collection, Map) configure} method
 *  will be called once.</li>
 *  <li>pre-parse phase: before parsing the source document, the collection will call the trigger's
 *  {@link #prepare(int, DBBroker, Txn, XmldbURI, DocumentImpl) prepare} 
 *  method once for each document to be stored, removed or updated. The trigger may
 *  throw a TriggerException if the current action should be aborted.</li>
 *  <li>validation phase: during the validation phase, the document is parsed once by the SAX parser. During this
 *  phase, the trigger may decide to throw a SAXException to report a problem. Validation will fail and the action
 *  is aborted.</li>
 *  <li>storage phase: the document is again parsed by the SAX parser. The trigger will still receive all SAX events,
 *  but it is not allowed to throw an exception. Throwing an exception during the storage phase will result in an
 *  invalid document in the database. Use {@link #isValidating() isValidating} in your code to check that you're
 *  in validation phase.</li>
 *  <li>finalization: the method {@link #finish(int, DBBroker, Txn, XmldbURI, DocumentImpl)} is called. At this point, the document
 *  has already been stored and is ready to be used or - for {@link #REMOVE_DOCUMENT_EVENT} - has been removed.
 *  </li>
 * </ol>
 * 
 * @author wolf
 */
public interface DocumentTrigger extends Trigger, ContentHandler, LexicalHandler {

    /**
     * This method is called once before the database will actually parse the input data. You may take any action
     * here, using the supplied broker instance.
     * 
     * @param broker
     * @param txn
     * @param uri
     * @throws TriggerException
     */
    public void beforeCreateDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException;
    
    /**
     * This method is called after the operation completed. At this point, the document has already
     * been stored.
     * 
     * @param broker
     * @param txn
     * @param document
     * @throws TriggerException
     */
    public void afterCreateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException;

    public void beforeUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException;
    public void afterUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException;

	public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException;
	public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException;

    public void beforeCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException;
    public void afterCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException;

    public void beforeMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException;
    public void afterMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException;

    public void beforeDeleteDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException;
    public void afterDeleteDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException;

    /**
     * Returns true if the SAX parser is currently in validation phase. During validation phase, the trigger
     * may safely throw a SAXException. However, if is {@link #isValidating() isValidating} returns false, no exceptions should be
     * thrown.
     * 
     * @return true if the parser is in validation phase.
     */
    public boolean isValidating();

    /**
     * Called by the database to report that it is entering validation phase.
     * 
     * @param validating
     */
    public void setValidating(boolean validating);
    
    /**
     * Called by the database to set the output content handler for this trigger.
     * 
     * @param handler
     */
    public void setOutputHandler(ContentHandler handler);

    /**
     * Called by the database to set the lexical output content handler for this trigger.
     * 
     * @param handler
     */
    public void setLexicalOutputHandler(LexicalHandler handler);

    /**
     * Returns the output handler to which SAX events should be forwarded.
     * 
     * @return The ContentHandler instance for the output.
     */
    public ContentHandler getOutputHandler();

    /**
     * Returns the input content handler. Usually, this method should just return
     * the trigger object itself, i.e. <b>this</b>. However, the trigger may choose to provide
     * a different content handler.
     * 
     * @return the ContentHandler to be called by the database.
     */
    public ContentHandler getInputHandler();

    /**
     * Called by the database to set the lexical output handler for this trigger.
     * 
     * @return The LexicalHandler instance for the output.
     */
    public LexicalHandler getLexicalOutputHandler();

    /**
     * Returns the lexical input handler for this trigger. See {@link #getInputHandler() getInputHandler}.
     * 
     * @return The LexicalHandler instance for the input.
     */
    public LexicalHandler getLexicalInputHandler();
}
