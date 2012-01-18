package org.exist.collections.triggers;

import org.exist.storage.DBBroker;

/**
 *
 * @author aretter
 */


public class DocumentTriggerProxies extends AbstractTriggerProxies<DocumentTrigger, DocumentTriggerProxy, DocumentTriggersVisitor> {

    @Override
    public DocumentTriggersVisitor instantiateVisitor(DBBroker broker) {
        return new DocumentTriggersVisitor(broker, this);
    }
}