package org.exist.collections.triggers;

import org.exist.storage.DBBroker;

/**
 *
 * @author aretter
 */


public class CollectionTriggerProxies extends AbstractTriggerProxies<CollectionTrigger, CollectionTriggerProxy, CollectionTriggersVisitor> {

    @Override
    public CollectionTriggersVisitor instantiateVisitor(DBBroker broker) {
        return new CollectionTriggersVisitor(broker, this);
    }
}