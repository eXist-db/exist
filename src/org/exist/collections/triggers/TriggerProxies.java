package org.exist.collections.triggers;

import org.exist.storage.DBBroker;

/**
 *
 * @author aretter
 */
public interface TriggerProxies<P extends TriggerProxy> {
    
    public void add(P proxy);
    
    public abstract TriggersVisitor instantiateVisitor(DBBroker broker);
}
