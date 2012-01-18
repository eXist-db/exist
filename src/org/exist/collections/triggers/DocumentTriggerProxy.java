package org.exist.collections.triggers;

import java.util.List;
import java.util.Map;
import org.exist.xmldb.XmldbURI;

/**
 *
 * @author aretter
 */
public class DocumentTriggerProxy extends AbstractTriggerProxy<DocumentTrigger> {

    public DocumentTriggerProxy(Class<DocumentTrigger> clazz, XmldbURI collectionConfigurationURI) {
        super(clazz, collectionConfigurationURI);
    }
    
    public DocumentTriggerProxy(Class<DocumentTrigger> clazz, XmldbURI collectionConfigurationURI, Map<String, List<? extends Object>> parameters) {
        super(clazz, collectionConfigurationURI, parameters);
    }
}
