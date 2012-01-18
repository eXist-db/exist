 package org.exist.collections.triggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.exist.collections.Collection;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;

/**
 *
 * @author aretter
 */


public abstract class AbstractTriggerProxy<T extends Trigger> implements TriggerProxy<T> {

    private final Class<T> clazz;
    private Map<String, List<? extends Object>> parameters;
    
    /**
     * The database Collection URI of where the configuration for this Trigger came from
     * typically somewhere under /db/system/config/db/
     */
    private final XmldbURI collectionConfigurationURI;

    public AbstractTriggerProxy(Class<T> clazz, XmldbURI collectionConfigurationURI) {
        this.clazz = clazz;
        this.collectionConfigurationURI = collectionConfigurationURI;
    }
    
    public AbstractTriggerProxy(Class<T> clazz, XmldbURI collectionConfigurationURI, Map<String, List<? extends Object>> parameters) {
        this.clazz = clazz;
        this.collectionConfigurationURI = collectionConfigurationURI;
        this.parameters = parameters;
    }

    protected Class<T> getClazz() {
        return clazz;
    }
    
    protected XmldbURI getCollectionConfigurationURI() {
        return collectionConfigurationURI;
    }
    
    @Override
    public void setParameters(Map<String, List<? extends Object>> parameters) {
        this.parameters = parameters;
    }
    
    protected Map<String, List<? extends Object>> getParameters() {
        return parameters;
    }
    
    protected T newInstance(DBBroker broker) throws TriggerException {
        try {
            T trigger = getClazz().newInstance();

            XmldbURI collectionForTrigger = getCollectionConfigurationURI();
            if(collectionForTrigger.startsWith(XmldbURI.CONFIG_COLLECTION_URI)) {
                collectionForTrigger = collectionForTrigger.trimFromBeginning(XmldbURI.CONFIG_COLLECTION_URI);
            }

            Collection collection = broker.getCollection(collectionForTrigger);
            trigger.configure(broker, collection, getParameters());

            return trigger;
        } catch (InstantiationException ie) {
            throw new TriggerException("Unable to instantiate Trigger '"  + getClazz().getName() + "': " + ie.getMessage(), ie);
        } catch (IllegalAccessException iae) {
            throw new TriggerException("Unable to instantiate Trigger '"  + getClazz().getName() + "': " + iae.getMessage(), iae);
        }
    }
    
    public static List<TriggerProxy> newInstance(Class c, XmldbURI collectionConfigurationURI, Map<String, List<? extends Object>> parameters) throws TriggerException {
        
        final List<TriggerProxy> proxies = new ArrayList<TriggerProxy>();
        
        if(DocumentTrigger.class.isAssignableFrom(c)) {
            proxies.add(new DocumentTriggerProxy((Class<DocumentTrigger>)c, collectionConfigurationURI, parameters));
        }
        
        
        if(CollectionTrigger.class.isAssignableFrom(c)) {
            proxies.add(new CollectionTriggerProxy((Class<CollectionTrigger>)c, collectionConfigurationURI, parameters));
        } 
        
        
        if(proxies.isEmpty()) {
            throw new TriggerException("Unknown Trigger class type: " + c.getName());
        }
        
        return proxies;
    }
}