/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011-2012 The eXist Project
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

import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 *
 * @author aretter
 */
public class CollectionTriggersVisitor extends AbstractTriggersVisitor<CollectionTrigger, CollectionTriggerProxies> implements CollectionTrigger {

    protected Logger LOG = Logger.getLogger(getClass());
    
    public CollectionTriggersVisitor(DBBroker broker, CollectionTriggerProxies proxies) {
        super(broker, proxies);
    }

    @Override
    public void configure(DBBroker broker, Collection parent, Map<String, List<? extends Object>> parameters) throws TriggerException {
        //ignore triggers are already configured by this stage!
    }

    @Override
    public void prepare(int event, DBBroker broker, Txn txn, Collection collection, Collection newCollection) throws TriggerException {
        for(final CollectionTrigger trigger : getTriggers()) {
            trigger.prepare(event, broker, txn, collection, newCollection);
        }
    }

    @Override
    public void finish(int event, DBBroker broker, Txn txn, Collection collection, Collection newCollection) {
        try {
            for(final CollectionTrigger trigger : getTriggers()) {
                trigger.finish(event, broker, txn, collection, newCollection);
            }
        } catch (final TriggerException te) {
            LOG.error(te.getMessage(), te);
        }
    }

    @Override
    public void beforeCreateCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        for(final CollectionTrigger trigger : getTriggers()) {
            trigger.beforeCreateCollection(broker, txn, uri);
        }
    }

    @Override
    public void afterCreateCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
        for(final CollectionTrigger trigger : getTriggers()) {
            trigger.afterCreateCollection(broker, txn, collection);
        }
    }

    @Override
    public void beforeCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
        for(final CollectionTrigger trigger : getTriggers()) {
            trigger.beforeCopyCollection(broker, txn, collection, newUri);
        }
    }

    @Override
    public void afterCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException {
        for(final CollectionTrigger trigger : getTriggers()) {
            trigger.afterCopyCollection(broker, txn, collection, oldUri);
        }
    }

    @Override
    public void beforeMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
        for(final CollectionTrigger trigger : getTriggers()) {
            trigger.beforeMoveCollection(broker, txn, collection, newUri);
        }
    }

    @Override
    public void afterMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException {
        for(final CollectionTrigger trigger : getTriggers()) {
            trigger.afterMoveCollection(broker, txn, collection, oldUri);
        }
    }

    @Override
    public void beforeDeleteCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
        for(final CollectionTrigger trigger : getTriggers()) {
            trigger.beforeDeleteCollection(broker, txn, collection);
        }
    }

    @Override
    public void afterDeleteCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        for(final CollectionTrigger trigger : getTriggers()) {
            trigger.afterDeleteCollection(broker, txn, uri);
        }
    }
}