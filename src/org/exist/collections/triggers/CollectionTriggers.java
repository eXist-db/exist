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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class CollectionTriggers implements CollectionTrigger {
    
    private final List<CollectionTrigger> triggers;

    public CollectionTriggers(DBBroker broker, Txn transaction) throws TriggerException {
        this(broker, transaction, null, null);
    }

    public CollectionTriggers(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
        this(broker, transaction, collection, collection.getConfiguration(broker));
    }

    public CollectionTriggers(DBBroker broker, Txn transaction, Collection collection, CollectionConfiguration config) throws TriggerException {
        
        List<TriggerProxy<? extends CollectionTrigger>> colTriggers = null;
        if (config != null) {
            colTriggers = config.collectionTriggers();
        }
        
        java.util.Collection<TriggerProxy<? extends CollectionTrigger>> masterTriggers = broker.getDatabase().getCollectionTriggers();
        
        triggers = new ArrayList<CollectionTrigger>( masterTriggers.size() + (colTriggers == null ? 0 : colTriggers.size()) );
        
        for (TriggerProxy<? extends CollectionTrigger> colTrigger : masterTriggers) {
            
            CollectionTrigger instance = colTrigger.newInstance(broker, transaction, collection);
            
            register(instance);
        }
        
        if (colTriggers != null) {
            for (TriggerProxy<? extends CollectionTrigger> colTrigger : colTriggers) {
                
                CollectionTrigger instance = colTrigger.newInstance(broker, transaction, collection);
                
                register(instance);
            }
        }
    }
    
    private void register(CollectionTrigger trigger) {
        triggers.add(trigger);
    }
    
    @Override
    public void configure(DBBroker broker, Txn transaction, Collection col, Map<String, List<? extends Object>> parameters) throws TriggerException {
    }

    @Override
    public void beforeCreateCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        for (CollectionTrigger trigger : triggers) {
            trigger.beforeCreateCollection(broker, txn, uri);
        }
    }

    @Override
    public void afterCreateCollection(DBBroker broker, Txn txn, Collection collection) {
        for (CollectionTrigger trigger : triggers) {
            try {
                trigger.afterCreateCollection(broker, txn, collection);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
        for (CollectionTrigger trigger : triggers) {
            trigger.beforeCopyCollection(broker, txn, collection, newUri);
        }
    }

    @Override
    public void afterCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) {
        for (CollectionTrigger trigger : triggers) {
            try {
                trigger.afterCopyCollection(broker, txn, collection, oldUri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
        for (CollectionTrigger trigger : triggers) {
            trigger.beforeMoveCollection(broker, txn, collection, newUri);
        }
    }

    @Override
    public void afterMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) {
        for (CollectionTrigger trigger : triggers) {
            try {
                trigger.afterMoveCollection(broker, txn, collection, oldUri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeDeleteCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
        for (CollectionTrigger trigger : triggers) {
            trigger.beforeDeleteCollection(broker, txn, collection);
        }
    }

    @Override
    public void afterDeleteCollection(DBBroker broker, Txn txn, XmldbURI uri) {
        for (CollectionTrigger trigger : triggers) {
            try {
                trigger.afterDeleteCollection(broker, txn, uri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }
}
