package org.exist.collections.triggers;

import java.util.ArrayList;
import java.util.List;
import org.exist.storage.DBBroker;

/**
 *
 * @author aretter
 */


public abstract class AbstractTriggerProxies<T extends Trigger, P extends AbstractTriggerProxy<T>, D extends TriggersVisitor> implements TriggerProxies<P>{

    //extract signatures to interface
    
    private List<P> proxies = new ArrayList<P>();
    
    @Override
    public void add(P proxy) {
        proxies.add(proxy);
    }
    
    protected List<T> instantiateTriggers(DBBroker broker) throws TriggerException {
        
        final List<T> triggers = new ArrayList<T>(proxies.size());
        
        for(P proxy : proxies) {
            triggers.add(proxy.newInstance(broker));
        }
        
        return triggers;
    }
    
    @Override
    public abstract D instantiateVisitor(DBBroker broker);
}
