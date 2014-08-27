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
import org.exist.dom.AttrImpl;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class LuceneConfigText {

    protected final static String N_INLINE = "inline";
    protected final static String N_IGNORE = "ignore";
    
    private LuceneConfig config;

    protected String name = null;

    protected NodePath path = null;

    protected boolean isQNameIndex = false;
    protected boolean isAttrPatternIndex = false;

    protected Map<QName, String> specialNodes = null;

    protected LuceneConfigText nextConfig = null;
    
    protected FieldType type = null;
    
    protected QName attrName = null;
    protected Pattern attrValuePattern = null;
    
    public LuceneConfigText(LuceneConfig config) {
    	this.config = config;
    }
    
    public FieldType getFieldType() {
    	if (type == null) {
	     	type = new FieldType();
	    	type.analyzer = config.analyzers.getDefaultAnalyzer();
    	}
    	
    	return type;
	}


    // return saved Analyzer for use in LuceneMatchListener
    public Analyzer getAnalyzer() {
        return getFieldType().getAnalyzer();
    }

    public String getAnalyzerId() {
        return getFieldType().getAnalyzerId();
    }

    public QName getQName() {
        return path.getLastComponent();
    }

    public NodePath getNodePath() {
        return path;
    }

    public float getBoost() {
        return getFieldType().getBoost();
    }
    
    public void setQName(QName qname) {
        path = new NodePath(qname);
        isQNameIndex = true;
	}
    
    public void setPath(Map<String, String> namespaces, String matchPath) throws DatabaseConfigurationException {
        try {
        	path = new NodePath(namespaces, matchPath);
			if (path.length() == 0)
			    throw new DatabaseConfigurationException("Lucene module: Invalid match path in collection config: " + matchPath);
		} catch (IllegalArgumentException e) {
			throw new DatabaseConfigurationException("Lucene module: invalid qname in configuration: " + e.getMessage());
		}
	}
    
    public void setAttrPattern(Map<String, String> namespaces, String pattern) throws DatabaseConfigurationException {
    	int pos = pattern.indexOf("=");
    	if (pos > 0) {
    		attrName = LuceneConfigXML.parseQName(pattern.substring(0, pos), namespaces);
    		try {
    			attrValuePattern = Pattern.compile(pattern.substring(pos+1));
    		} catch (PatternSyntaxException e) {
    			throw new DatabaseConfigurationException(pattern, e);
    		}
    		isAttrPatternIndex = true;
    	} else {
			throw new DatabaseConfigurationException("Valid pattern 'attribute-name=pattern', but get '"+pattern+"'");
    	}
	}

    public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
	
	public void add(LuceneConfigText config) {
		if (nextConfig == null)
			nextConfig = config;
		else
			nextConfig.add(config);
	}
	
	public LuceneConfigText getNext() {
		return nextConfig;
	}
	
	/**
	 * @return true if this index can be queried by name
	 */
	public boolean isNamed() {
		return name != null;
	}
	
	public void addIgnoreNode(QName qname) {
        if (specialNodes == null)
        	specialNodes = new TreeMap<>();
        
        specialNodes.put(qname, LuceneConfigText.N_IGNORE);
	}

	public boolean isIgnoredNode(QName qname) {
        return specialNodes != null && specialNodes.get(qname) == N_IGNORE;
    }

	public void addInlineNode(QName qname) {
        if (specialNodes == null)
        	specialNodes = new TreeMap<>();
        
        specialNodes.put(qname, LuceneConfigText.N_INLINE);
	}
	
    public boolean isInlineNode(QName qname) {
        return specialNodes != null && specialNodes.get(qname) == N_INLINE;
    }
    
    public boolean isAttrPattern() {
    	return isAttrPatternIndex;
    }

    public boolean match(NodePath other) {
        if (isQNameIndex) {
            final QName qn1 = path.getLastComponent();
            final QName qn2 = other.getLastComponent();
            return qn1.getNameType() == qn2.getNameType() && qn2.equalsSimple(qn1);
        }
        return path.match(other);
    }

    public boolean match(NodePath other, AttrImpl attrib) {
		if (isAttrPatternIndex) {
			if (attrib != null && attrValuePattern != null) { //log error?
				if ((isQNameIndex && other.getLastComponent().equalsSimple(path.getLastComponent())) || path.match(other)) {
					
					if (attrib.getQName().equalsSimple(attrName)) {
						
						Matcher m = attrValuePattern.matcher(attrib.getValue());
						return m.matches();
					}
					
				}
			}
		} else {
	    	if (isQNameIndex) {
	    		return other.getLastComponent().equalsSimple(path.getLastComponent());
	    	}
	        return path.match(other);
    	}
		return false;
    }


    @Override
	public String toString() {
		return path.toString();
	}
}

