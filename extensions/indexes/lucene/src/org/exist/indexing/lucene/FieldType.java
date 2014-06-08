/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType.NumericType;

/**
 * Configures a field type: analyzers etc. used for indexing
 * a field.
 * 
 * @author wolf
 *
 */
public class FieldType {

	protected String id = null;
	
	protected String analyzerId = null;
	
    // save Analyzer for later use in LuceneMatchListener
	protected Analyzer analyzer = null;

	private float boost = -1;
    
	protected Field.Store store = null;
	
	protected boolean isStore = false;
	protected boolean isTokenized = false;
    protected boolean isSymbolized = false;
	
	protected NumericType numericType = null;
	
    public FieldType() {
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

	public void setBoost(float boost) {
		this.boost = boost;
	}

	public float getBoost() {
		return boost;
	}
	
	public Field.Store getStore() {
		return store;
	}

	public void tokenized(boolean isTokenized) {
		this.isTokenized = isTokenized;
	}

	public boolean isTokenized() {
		return isTokenized;
	}
	
    public boolean isSymbolized() {
            return isSymbolized;
    }

    public void setNumericType(String str) {
		numericType = NumericType.valueOf(str);
//		throw new IllegalArgumentException("Unknown numeric-type '"+numericTypeAttr+"'.");
	}
	
	org.apache.lucene.document.FieldType ft = null;
	
	public org.apache.lucene.document.FieldType getFieldType() {
		if (ft == null) {
			org.apache.lucene.document.FieldType _ft = new org.apache.lucene.document.FieldType();
		
			_ft.setStored(isStore);
			_ft.setTokenized(isTokenized);
			_ft.setStoreTermVectors(true);
			_ft.setIndexed(true);
			
			if (numericType != null)
				_ft.setNumericType(numericType);
			
			ft = _ft;
		}
		
		return ft;
	}
}