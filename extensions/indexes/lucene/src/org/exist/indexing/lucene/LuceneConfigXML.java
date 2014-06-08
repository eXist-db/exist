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

import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.document.Field;
import org.exist.dom.QName;
import org.exist.indexing.lucene.analyzers.NoDiacriticsStandardAnalyzer;
import org.exist.storage.ElementValue;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LuceneConfigXML {

    private final static String CONFIG_ROOT = "lucene";
    private final static String INDEX_ELEMENT = "text";
    private final static String ANALYZER_ELEMENT = "analyzer";
    private final static String PARSER_ELEMENT = "parser";
    private final static String FIELD_TYPE_ELEMENT = "fieldType";
    private static final String INLINE_ELEMENT = "inline";
    private static final String IGNORE_ELEMENT = "ignore";
    private final static String BOOST_ATTRIB = "boost";
    private static final String DIACRITICS = "diacritics";
    private final static String NUMERIC_TYPE_ATTRIB = "numeric-type";

    /**
     * Parse a configuration entry. The main configuration entries for this index
     * are the &lt;text&gt; elements. They may be enclosed by a &lt;lucene&gt; element.
     *
     * @param configNodes
     * @param namespaces
     * @throws org.exist.util.DatabaseConfigurationException
     */
    protected static LuceneConfig parseConfig(NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        LuceneConfig conf = new LuceneConfig();
        
        parseConfig(conf, configNodes, namespaces);
        
        return conf;
    }

    private static LuceneConfig parseConfig(LuceneConfig conf, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
    	Node node;
        for(int i = 0; i < configNodes.getLength(); i++) {
            node = configNodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                try {
					if (CONFIG_ROOT.equals(node.getLocalName())) {
					    Element elem = (Element) node;
					    if (elem.hasAttribute(BOOST_ATTRIB)) {
					        String value = elem.getAttribute(BOOST_ATTRIB);
					        try {
					        	conf.boost = Float.parseFloat(value);
					        } catch (NumberFormatException e) {
					            throw new DatabaseConfigurationException("Invalid value for 'boost' attribute in " +
					                "lucene index config: float expected, got " + value);
					        }
					    }
                        if (elem.hasAttribute(DIACRITICS)) {
                            String value = elem.getAttribute(DIACRITICS);
                            if (value.equalsIgnoreCase("no")) {
                            	conf.analyzers.setDefaultAnalyzer(new NoDiacriticsStandardAnalyzer(LuceneIndex.LUCENE_VERSION_IN_USE));
                            }
                        }
					    parseConfig(conf, node.getChildNodes(), namespaces);
                        
					} else if (ANALYZER_ELEMENT.equals(node.getLocalName())) {
						conf.analyzers.addAnalyzer((Element) node);
						
					} else if (PARSER_ELEMENT.equals(node.getLocalName())) {
					    conf.queryParser = ((Element)node).getAttribute("class");
                        
					} else if (FIELD_TYPE_ELEMENT.equals(node.getLocalName())) {
						FieldType type = parseFieldType((Element) node, conf.analyzers);

						conf.addFieldType(type);
                        
					} else if (INDEX_ELEMENT.equals(node.getLocalName())) {
						// found an index definition
					    Element elem = (Element) node;
						
					    LuceneConfigText config = parseLuceneConfigText(conf, elem, namespaces, conf.analyzers, conf.fieldTypes);
						
						conf.add(config);
                        
					} else if (INLINE_ELEMENT.equals(node.getLocalName())) {
					    Element elem = (Element) node;
					    QName qname = parseQName(elem, namespaces);
					    
					    conf.addInlineNode(qname);
                        
					} else if (IGNORE_ELEMENT.equals(node.getLocalName())) {
					    Element elem = (Element) node;
					    QName qname = parseQName(elem, namespaces);
					    
					    conf.addIgnoreNode(qname);
					    
					}
                    
                } catch (DatabaseConfigurationException e) {
					LuceneConfig.LOG.warn("Invalid lucene configuration element: " + e.getMessage());
				}
            }
        }
        
        return conf;
    }
    
	private final static String ID_ATTR = "id";
	private final static String ANALYZER_ID_ATTR = "analyzer";
	//private final static String BOOST_ATTRIB = "boost";
	private final static String STORE_ATTRIB = "store";
	private final static String TOKENIZED_ATTR = "tokenized";
        private final static String SYMBOLIZED_ATTR = "symbolized";
	
    private static FieldType parseFieldType(Element config, AnalyzerConfig analyzers) throws DatabaseConfigurationException {
    	FieldType type = new FieldType();
    	
    	if (FIELD_TYPE_ELEMENT.equals(config.getLocalName())) {
    		type.id = config.getAttribute(ID_ATTR);
    		if (type.id == null || type.id.length() == 0)
    			throw new DatabaseConfigurationException("fieldType needs an attribute 'id'");
    	}
    	
    	String aId = config.getAttribute(ANALYZER_ID_ATTR);
    	// save Analyzer for later use in LuceneMatchListener
        if (aId != null && aId.length() > 0) {
        	type.analyzer = analyzers.getAnalyzerById(aId);
            if (type.analyzer == null)
                throw new DatabaseConfigurationException("No analyzer configured for id " + aId);
            type.analyzerId = aId;
            
        } else {
        	type.analyzer = analyzers.getDefaultAnalyzer();
        }
        
        String boostAttr = config.getAttribute(BOOST_ATTRIB);
        if (boostAttr != null && boostAttr.length() > 0) {
            try {
            	type.setBoost( Float.parseFloat(boostAttr) );
            } catch (NumberFormatException e) {
                throw new DatabaseConfigurationException("Invalid value for attribute 'boost'. Expected float, " +
                        "got: " + boostAttr);
            }
        }
        
        String numericTypeAttr = config.getAttribute(NUMERIC_TYPE_ATTRIB);
        if (numericTypeAttr != null && numericTypeAttr.length() > 0) {
            try {
            	type.setNumericType( numericTypeAttr );
            } catch (IllegalArgumentException e) {
                throw new DatabaseConfigurationException("Invalid value for attribute 'numeric-type'. Expected ', " +
                        "got: " + boostAttr);
            }
        }

        String storeAttr = config.getAttribute(STORE_ATTRIB);
        if (storeAttr != null && storeAttr.length() > 0) {
        	type.isStore = storeAttr.equalsIgnoreCase("yes");
        	type.store = type.isStore ? Field.Store.YES : Field.Store.NO;
        }

        String tokenizedAttr = config.getAttribute(TOKENIZED_ATTR);
        if (tokenizedAttr != null && tokenizedAttr.length() > 0) {
        	type.isTokenized = tokenizedAttr.equalsIgnoreCase("yes");
        }
        
        String symbolizedAttr = config.getAttribute(SYMBOLIZED_ATTR);
        if (symbolizedAttr != null && symbolizedAttr.length() > 0) {
                type.isSymbolized = symbolizedAttr.equalsIgnoreCase("yes");
        }

        return type;
    }
    

    private final static String QNAME_ATTR = "qname";
    private final static String MATCH_ATTR = "match";

    //private final static String IGNORE_ELEMENT = "ignore";
    //private final static String INLINE_ELEMENT = "inline";
	private final static String FIELD_ATTR = "field";
	private final static String TYPE_ATTR = "type";

	private final static String PATTERN_ATTR = "attribute";

    private static LuceneConfigText parseLuceneConfigText(
    		LuceneConfig config,
    		Element node, 
    		Map<String, String> namespaces, 
    		AnalyzerConfig analyzers,
    		Map<String, FieldType> fieldTypes
    		) throws DatabaseConfigurationException {
    	
    	LuceneConfigText conf = new LuceneConfigText(config);
    	
        if (node.hasAttribute(QNAME_ATTR)) {
            QName qname = parseQName(node, namespaces);
            
            conf.setQName(qname);
            
        } else {
            String matchPath = node.getAttribute(MATCH_ATTR);

            conf.setPath(namespaces, matchPath);
        }

        if (node.hasAttribute(PATTERN_ATTR)) {
        	String pattern = node.getAttribute(PATTERN_ATTR);
        	
        	conf.setAttrPattern(namespaces, pattern);
        }

        String name = node.getAttribute(FIELD_ATTR);
        if (name != null && name.length() > 0)
        	conf.setName(name);
        
        String fieldType = node.getAttribute(TYPE_ATTR);
        if (fieldType != null && fieldType.length() > 0)
        	conf.type = fieldTypes.get(fieldType);        
        
        if (conf.type == null)
        	conf.type = parseFieldType(node, analyzers);

        parse(conf, node, namespaces);
        
        return conf;
    }

    private static void parse(LuceneConfigText conf, Element root, Map<String, String> namespaces) throws DatabaseConfigurationException {
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (IGNORE_ELEMENT.equals(child.getLocalName())) {
                    String qnameAttr = ((Element) child).getAttribute(QNAME_ATTR);
                    if (qnameAttr == null || qnameAttr.length() == 0)
                        throw new DatabaseConfigurationException("Lucene configuration element 'ignore' needs an attribute 'qname'");
                    
                    conf.addIgnoreNode(parseQName(qnameAttr, namespaces));
                    
                } else if (INLINE_ELEMENT.equals(child.getLocalName())) {
                    String qnameAttr = ((Element) child).getAttribute(QNAME_ATTR);
                    if (qnameAttr == null || qnameAttr.length() == 0)
                        throw new DatabaseConfigurationException("Lucene configuration element 'inline' needs an attribute 'qname'");
                    if (conf.specialNodes == null)
                    	conf.specialNodes = new TreeMap<QName, String>();
                    conf.specialNodes.put(parseQName(qnameAttr, namespaces), LuceneConfigText.N_INLINE);
                }
            }
            child = child.getNextSibling();
        }
    }
    
    public static QName parseQName(Element config, Map<String, String> namespaces) throws DatabaseConfigurationException {
        String name = config.getAttribute(QNAME_ATTR);
        if (name == null || name.length() == 0)
            throw new DatabaseConfigurationException("Lucene index configuration error: element " + config.getNodeName() +
                    " must have an attribute " + QNAME_ATTR);

        return parseQName(name, namespaces);
    }

    public static QName parseQName(String name, Map<String, String> namespaces) throws DatabaseConfigurationException {
        boolean isAttribute = false;
        if (name.startsWith("@")) {
            isAttribute = true;
            name = name.substring(1);
        }
        try {
            String prefix = QName.extractPrefix(name);
            String localName = QName.extractLocalName(name);
            String namespaceURI = "";
            if (prefix != null) {
                namespaceURI = namespaces.get(prefix);
                if(namespaceURI == null) {
                    throw new DatabaseConfigurationException("No namespace defined for prefix: " + prefix +
                            " in index definition");
                }
            }
            QName qname = new QName(localName, namespaceURI, prefix);
            if (isAttribute)
                qname.setNameType(ElementValue.ATTRIBUTE);
            return qname;
        } catch (IllegalArgumentException e) {
            throw new DatabaseConfigurationException("Lucene index configuration error: " + e.getMessage(), e);
        }
    }
}