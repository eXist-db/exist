package org.exist.indexing.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

/**
 * Configures a field type: analyzers etc. used for indexing
 * a field.
 * 
 * @author wolf
 *
 */
public class FieldType {

	private final static String ID_ATTR = "id";
	private final static String ANALYZER_ID_ATTR = "analyzer";
	private final static String BOOST_ATTRIB = "boost";
	private final static String STORE_ATTRIB = "store";
	
	private String id = null;
	
	private String analyzerId = null;
	
    // save Analyzer for later use in LuceneMatchListener
    private Analyzer analyzer = null;

	private float boost = -1;
    
	private Field.Store store = null;
	
    public FieldType(Element config, AnalyzerConfig analyzers) throws DatabaseConfigurationException {
        
    	if (LuceneConfig.FIELD_TYPE_ELEMENT.equals(config.getLocalName())) {
    		id = config.getAttribute(ID_ATTR);
    		if (id == null || id.length() == 0)
    			throw new DatabaseConfigurationException("fieldType needs an attribute 'id'");
    	}
    	
    	String aId = config.getAttribute(ANALYZER_ID_ATTR);
    	// save Analyzer for later use in LuceneMatchListener
        if (aId != null && aId.length() > 0) {
        	analyzer = analyzers.getAnalyzerById(aId);
            if (analyzer == null)
                throw new DatabaseConfigurationException("No analyzer configured for id " + aId);
            analyzerId = aId;
            
        } else {
        	analyzer = analyzers.getDefaultAnalyzer();
        }
        
        String boostAttr = config.getAttribute(BOOST_ATTRIB);
        if (boostAttr != null && boostAttr.length() > 0) {
            try {
                boost = Float.parseFloat(boostAttr);
            } catch (NumberFormatException e) {
                throw new DatabaseConfigurationException("Invalid value for attribute 'boost'. Expected float, " +
                        "got: " + boostAttr);
            }
        }
        
        String storeAttr = config.getAttribute(STORE_ATTRIB);
        if (storeAttr != null && storeAttr.length() > 0) {
        	store = storeAttr.equalsIgnoreCase("yes") ? Field.Store.YES : Field.Store.NO;
        }
    }
    
    public String getId() {
		return id;
	}

	public String getAnalyzerId() {
		return analyzerId;
	}

	public Analyzer getAnalyzer() {
		return analyzer;
	}

	public float getBoost() {
		return boost;
	}
	
	public Field.Store getStore() {
		return store;
	}
}