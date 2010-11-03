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
 *  $Id$
 */
package org.exist.collections.triggers;

import org.exist.collections.Collection;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * Interface for triggers that can be registered with collection-related events.
 * 
 * @author wolf
 */
public interface CollectionTrigger extends Trigger {

    /**
     * This method is called once before the database will actually create, remove or rename a collection. You may 
     * take any action here, using the supplied broker instance.
     * 
     * @param event the type of event that triggered this call (see the constants defined in this interface).
     * @param broker the database instance used to process the current action.
     * @param collection the {@link Collection} which fired this event.
     * @param newName optional: if event is a {@link Trigger#RENAME_COLLECTION_EVENT},
     *  this parameter contains the new name of the collection. It is null otherwise.
     * @throws TriggerException throwing a TriggerException will abort the current action.
     */
    @Deprecated
    public void prepare(
        int event,
        DBBroker broker,
        Txn transaction,
        Collection collection,
        Collection newCollection)
        throws TriggerException;
    
    /**
     * This method is called after the operation has completed.
     *   
     **/
    @Deprecated
    public void finish(
        int event,
        DBBroker broker,
        Txn transaction,
        Collection collection,
        Collection newCollection);
    
    public void beforeCreateCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException;
    public void afterCreateCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException;

    public void beforeCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException;
    public void afterCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException;

    public void beforeMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException;
    public void afterMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException;

    public void beforeDeleteCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException;
    public void afterDeleteCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException;
}
