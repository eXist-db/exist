package org.exist.cocoon;

import java.util.ArrayList;
import java.util.TreeMap;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * XSPHelper.java enclosing_type
 * 
 * @author wolf
 *
 */
public class XSPHelper {
    
    private TreeMap collections = new TreeMap();
    private TreeMap documents = new TreeMap();
    private ResourceSet result;
    
    public XSPHelper(ResourceSet result) throws XMLDBException {
        this.result = result;
        if(result == null)
            return;
        ArrayList hitsByDoc;
        XMLResource resource;
        Collection currentCollection;
        for(int i = 0; i < result.getSize(); i++) {
            resource = (XMLResource)result.getResource( ( long ) i );
            currentCollection = resource.getParentCollection();
            if((documents = (TreeMap)collections.get(currentCollection.getName())) == null) {
                documents = new TreeMap();
                collections.put(currentCollection.getName(), documents);
            }
            if((hitsByDoc = (ArrayList)documents.get(resource.getDocumentId())) == null) {
                hitsByDoc = new ArrayList();
                documents.put(resource.getDocumentId(), hitsByDoc);
            }
            hitsByDoc.add(resource);
        }
    }
    
    public int getHits() throws XMLDBException {
        return result == null ? 0 : (int)result.getSize();
    }
    
    public ResourceSet getResult() {
        return result;
    }        
}
