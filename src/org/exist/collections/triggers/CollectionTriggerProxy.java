package org.exist.collections.triggers;

import java.util.List;
import java.util.Map;
import org.exist.xmldb.XmldbURI;

/**
 *
 * @author aretter
 */
public class CollectionTriggerProxy extends AbstractTriggerProxy<CollectionTrigger> {
    
    public CollectionTriggerProxy(Class<CollectionTrigger> clazz,  XmldbURI collectionConfigurationURI){
        super(clazz, collectionConfigurationURI);
    }
    
    public CollectionTriggerProxy(Class<CollectionTrigger> clazz,  XmldbURI collectionConfigurationURI, Map<String, List<? extends Object>> parameters) {
        super(clazz, collectionConfigurationURI, parameters);
    }
}
