/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field;
import org.exist.indexing.lucene.analyzers.MetaAnalyzer;
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
    private final MetaAnalyzer analyzer;

	private float boost = -1;
    
	private Field.Store store = null;
	
    public FieldType(Element config, AnalyzerConfig analyzers) throws DatabaseConfigurationException {
        
    	if (LuceneConfig.FIELD_TYPE_ELEMENT.equals(config.getLocalName())) {
    		id = config.getAttribute(ID_ATTR);
            if (id.isEmpty()) {
				throw new DatabaseConfigurationException("fieldType needs an attribute 'id'");
			}
    	}

		final String aId = config.getAttribute(ANALYZER_ID_ATTR);
    	// save Analyzer for later use in LuceneMatchListener
        if (aId.isEmpty()) {
			analyzer = new MetaAnalyzer(analyzers.getDefaultAnalyzer());
        } else {
			final Analyzer configuredAnalyzer = analyzers.getAnalyzerById(aId);
			if (configuredAnalyzer == null) {
				throw new DatabaseConfigurationException("No analyzer configured for id " + aId);
			}
			analyzerId = aId;
			analyzer = new MetaAnalyzer(configuredAnalyzer);
        }
        
        final String boostAttr = config.getAttribute(BOOST_ATTRIB);
        if (!boostAttr.isEmpty()) {
            try {
                boost = Float.parseFloat(boostAttr);
            } catch (NumberFormatException e) {
                throw new DatabaseConfigurationException("Invalid value for attribute 'boost'. Expected float, " +
                        "got: " + boostAttr);
            }
        }

		final String storeAttr = config.getAttribute(STORE_ATTRIB);
        if (!storeAttr.isEmpty()) {
        	store = "yes".equalsIgnoreCase(storeAttr) ? Field.Store.YES : Field.Store.NO;
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

	public void addAnalzer(String fieldName, Analyzer analyzer) {
		this.analyzer.addAnalyzer(fieldName, analyzer);
	}

	public float getBoost() {
		return boost;
	}
	
	public Field.Store getStore() {
		return store;
	}
}
