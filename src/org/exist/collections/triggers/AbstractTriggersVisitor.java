package org.exist.collections.triggers;

import java.util.List;
import org.exist.storage.DBBroker;

/**
 *
 * @author aretter
 */


public abstract class AbstractTriggersVisitor<T extends Trigger, P extends AbstractTriggerProxies> implements TriggersVisitor {
    private final DBBroker broker;
    private final P proxies;
    private List<T> triggers;
    
    public AbstractTriggersVisitor(DBBroker broker, P proxies) {
        this.broker = broker;
        this.proxies = proxies;
    }
    
    /**
     * lazy instantiated
     */
    protected List<T> getTriggers() throws TriggerException {
        if(triggers == null) {
            triggers = proxies.instantiateTriggers(broker);
        }
        return triggers;
    }
}
