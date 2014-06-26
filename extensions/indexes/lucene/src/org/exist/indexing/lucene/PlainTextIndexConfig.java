package org.exist.indexing.lucene;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.document.Field;
import org.exist.memtree.NodeImpl;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Private class for representing the add/doc Solr xml fragment 
 */
public class PlainTextIndexConfig {
    
    private PlainTextDoc solrDoc = new PlainTextDoc();
    
    private ArrayList<PlainTextField> solrFields = new ArrayList<>();
    
    /**
     * Helper method for retrieving boost value, defaults to 1.0f
     */
    private float getFloatValue(String value) {
        
        float retVal = 1.0f;

        if (value != null && value.length() > 0) {
            try {
                retVal = Float.parseFloat(value);
                
            } catch (NumberFormatException e) {
                // TODO throw exception?
                LuceneIndexWorker.LOG.error(e);
            }
        }
        
        return retVal;
    }
    
    /**
     * Retrieve Solr configuration info from NodeValue structure
     */
    boolean parse(NodeValue descriptor) {
        
        if (descriptor.getImplementationType() == NodeValue.IN_MEMORY_NODE)
        	((NodeImpl)descriptor).expand();
        
        /* Check <doc> and retrieve boost value */
        Node doc = descriptor.getNode();
        // Get QName and text from descriptor
        if (!doc.getLocalName().contentEquals("doc")) {
            // throw exception
            LuceneIndexWorker.LOG.error("Expected <doc> got <" + descriptor.getNode().getLocalName() + ">");
            return false;
        }
        
        NamedNodeMap nnm = doc.getAttributes();
        Node attributeNode = nnm.getNamedItem("boost");
        if (attributeNode != null) {
            solrDoc.setBoost(getFloatValue(attributeNode.getNodeValue()));
        }
        
        
        /* Check <fields> and retrieve name, boost and value */
        
        List<PlainTextField> fields = getFields();

        NodeList nodeList = descriptor.getNode().getChildNodes();
        int length = nodeList.getLength();

        for (int i = 0; i < length; i++) {
            Node child = nodeList.item(i);

            String childname = child.getLocalName();
            if (!"field".equals(childname)) {
                LuceneIndexWorker.LOG.error("Expected <field> got <" + childname + ">");

            } else {
                // field element found
                PlainTextField field = new PlainTextField();
                fields.add(field);

                // Find mandatory attribute
                nnm = child.getAttributes();
                attributeNode = nnm.getNamedItem("name");

                if (attributeNode == null) {
                    // attribute not found
                    LuceneIndexWorker.LOG.error("No name attribute");

                } else {
                    // Get vlaue of name attribute
                    String name = attributeNode.getNodeValue();
                    field.setName(name);

                    // Get value of optional boost attribute
                    attributeNode = nnm.getNamedItem("boost");
                    if (attributeNode != null) {
                        String boost = attributeNode.getNodeValue();
                        field.setBoost(getFloatValue(boost));
                    }
                    
                    // Get value of optional store attribute
                    attributeNode = nnm.getNamedItem("store");
                    if (attributeNode != null) {
                    	String val = attributeNode.getNodeValue();
                        boolean store = val != null && val.equalsIgnoreCase("yes");
                        field.setStore(store);
                    }

                    // Collect data
                    CharSequence content = child.getNodeValue();
                    field.setContent(content);
                }
            }
        }
        return true;
    }
    

    ArrayList<PlainTextField> getFields() {
        return solrFields;
    }

    private void setFields(ArrayList<PlainTextField> solrField) {
        this.solrFields = solrField;
    }
    
    PlainTextDoc getDoc(){
        return solrDoc;
    }
    
    private void setDoc(PlainTextDoc doc){
        this.solrDoc = doc;
    }
    
    /**
     * Private class representing the <field> element (name, boost, content)
     */
    public static class PlainTextField {

        private Field.Store store = Field.Store.NO;
        private String name;
        private float boost = 1.0f;
        private CharSequence data;

        void setStore(boolean setStore){
           store = setStore ? Field.Store.YES : Field.Store.NO;
        }

        void setName(String name) {
            this.name=name;
        }

        void setBoost(float value) {
            boost = value;
        }

        void setContent(CharSequence value) {
            data = value;
        }

        public float getBoost() {
            return boost;
        }

        public CharSequence getData() {
            return data;
        }

        public String getName() {
            return name;
        }
        
        public Field.Store getStore(){
            return store;
        }
    }
    
    /**
     * Private class representing the <doc> element (boost).
     */
    public static class PlainTextDoc {
        private float boost = 1.0f;

        void setBoost(float value) {
            boost = value;
        }

        public float getBoost() {
            return boost;
        }
    }
}