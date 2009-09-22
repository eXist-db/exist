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
 *  $Id$
 */
package org.exist.collections.triggers;

import org.exist.collections.Collection;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;

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
    public void prepare(
        int event,
        DBBroker broker,
        Txn transaction,
        Collection collection,
        String newName)
        throws TriggerException;
    
    /**
     * This method is called after the operation has completed.
     *   
     **/
    public void finish(
        int event,
        DBBroker broker,
        Txn transaction,
        Collection collection,
        String newName);
}
