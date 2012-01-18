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
    public void prepare(int event, DBBroker broker, Txn transaction, Collection collection, Collection newCollection) throws TriggerException {
        for(CollectionTrigger trigger : getTriggers()) {
            trigger.prepare(event, broker, transaction, collection, newCollection);
        }
    }

    @Override
    public void finish(int event, DBBroker broker, Txn transaction, Collection collection, Collection newCollection) {
        try {
            for(CollectionTrigger trigger : getTriggers()) {
                trigger.finish(event, broker, transaction, collection, newCollection);
            }
        } catch (TriggerException te) {
            LOG.error(te.getMessage(), te);
        }
    }

    @Override
    public void beforeCreateCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
        for(CollectionTrigger trigger : getTriggers()) {
            trigger.beforeCreateCollection(broker, transaction, uri);
        }
    }

    @Override
    public void afterCreateCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
        for(CollectionTrigger trigger : getTriggers()) {
            trigger.afterCreateCollection(broker, transaction, collection);
        }
    }

    @Override
    public void beforeCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
        for(CollectionTrigger trigger : getTriggers()) {
            trigger.beforeCopyCollection(broker, transaction, collection, newUri);
        }
    }

    @Override
    public void afterCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI oldUri) throws TriggerException {
        for(CollectionTrigger trigger : getTriggers()) {
            trigger.afterCopyCollection(broker, transaction, collection, oldUri);
        }
    }

    @Override
    public void beforeMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
        for(CollectionTrigger trigger : getTriggers()) {
            trigger.beforeMoveCollection(broker, transaction, collection, newUri);
        }
    }

    @Override
    public void afterMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI oldUri) throws TriggerException {
        for(CollectionTrigger trigger : getTriggers()) {
            trigger.afterMoveCollection(broker, transaction, collection, oldUri);
        }
    }

    @Override
    public void beforeDeleteCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
        for(CollectionTrigger trigger : getTriggers()) {
            trigger.beforeDeleteCollection(broker, transaction, collection);
        }
    }

    @Override
    public void afterDeleteCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
        for(CollectionTrigger trigger : getTriggers()) {
            trigger.afterDeleteCollection(broker, transaction, uri);
        }
    }
}