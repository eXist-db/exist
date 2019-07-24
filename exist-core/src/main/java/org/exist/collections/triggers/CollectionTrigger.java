/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
     * @param broker eXist-db broker
     * @param txn the transaction
     * @param uri of the collection the trigger listens on
     * @throws TriggerException if an error in the trigger function is thrown
     */
    public void beforeCreateCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException;
    
    /**
     * This method is called after the operation has completed.
     *
     * @param broker eXist-db broker
     * @param txn the transaction
     * @param collection the trigger listens on
     * @throws TriggerException if an error in the trigger function is thrown
     */
    public void afterCreateCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException;

    public void beforeCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException;
    public void afterCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException;

    public void beforeMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException;
    public void afterMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException;

    public void beforeDeleteCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException;
    public void afterDeleteCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException;
}
