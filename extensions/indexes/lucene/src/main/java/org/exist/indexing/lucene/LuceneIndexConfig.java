/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2015 The eXist-db Project
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
 *
 */
package org.exist.indexing.lucene;

import java.util.*;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.facet.FacetsConfig;
import org.exist.dom.QName;
import org.exist.dom.persistent.AttrImpl;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class LuceneIndexConfig {

    private final static String N_INLINE = "inline";
    private final static String N_IGNORE = "ignore";

    private final static String IGNORE_ELEMENT = "ignore";
    private final static String INLINE_ELEMENT = "inline";
    private final static String MATCH_ATTR_ELEMENT = "match-attribute";
    private final static String HAS_ATTR_ELEMENT = "has-attribute";
    private final static String MATCH_SIBLING_ATTR_ELEMENT = "match-sibling-attribute";
    private final static String HAS_SIBLING_ATTR_ELEMENT = "has-sibling-attribute";
    private final static String FACET_ELEMENT = "facet";

    public static final String QNAME_ATTR = "qname";
    public static final String MATCH_ATTR = "match";

    public static final String FIELD_ATTR = "field";
    public static final String TYPE_ATTR = "type";

    private String name = null;

    private NodePathPattern path = null;

    private boolean isQNameIndex = false;

    private Map<QName, String> specialNodes = null;

    private List<LuceneFacetConfig> facets = new ArrayList<>();

    private LuceneIndexConfig nextConfig = null;

    private FieldType type = null;

    // This is for the @attr match boosting
    // and the intention is to do a proper predicate check instead in the future. /ljo
    private MultiMap matchAttrs;
    protected final static Logger LOG = LogManager.getLogger(LuceneIndexConfig.class);


    public LuceneIndexConfig(Element config, Map<String, String> namespaces, AnalyzerConfig analyzers,
                             Map<String, FieldType> fieldTypes, FacetsConfig facetsConfig) throws DatabaseConfigurationException {
        if (config.hasAttribute(QNAME_ATTR)) {
            QName qname = parseQName(config, namespaces);
            path = new NodePathPattern(qname);
            isQNameIndex = true;
        } else {
            String matchPath = config.getAttribute(MATCH_ATTR);

            try {
				path = new NodePathPattern(namespaces, matchPath);
				if (path.length() == 0)
				    throw new DatabaseConfigurationException("Lucene module: Invalid match path in collection config: " +
				        matchPath);
			} catch (IllegalArgumentException e) {
				throw new DatabaseConfigurationException("Lucene module: invalid qname in configuration: " + e.getMessage());
			}
        }

        String name = config.getAttribute(FIELD_ATTR);
        if (name != null && name.length() > 0)
        	setName(name);

        String fieldType = config.getAttribute(TYPE_ATTR);
        if (fieldType != null && fieldType.length() > 0)
        	type = fieldTypes.get(fieldType);
        if (type == null)
        	type = new FieldType(config, analyzers);

        parse(config, namespaces, facetsConfig);
    }

    private void parse(Element root, Map<String, String> namespaces, FacetsConfig facetsConfig) throws DatabaseConfigurationException {
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                final String localName = child.getLocalName();
                if (null != localName) {
		    Element configElement = (Element) child;
                    switch (localName) {
                        case FACET_ELEMENT: {
                            facets.add(new LuceneFacetConfig(configElement, facetsConfig, namespaces));
                            break;
                        }
                        case IGNORE_ELEMENT: {
			    String qnameAttr = configElement.getAttribute(QNAME_ATTR);
                            if (StringUtils.isEmpty(qnameAttr)) {
                                throw new DatabaseConfigurationException("Lucene configuration element 'ignore' needs an attribute 'qname'");
                            }
                            if (specialNodes == null) {
                                specialNodes = new TreeMap<>();
                            }
                            specialNodes.put(parseQName(qnameAttr, namespaces), N_IGNORE);
                            break;
                        }
                        case INLINE_ELEMENT: {
			    String qnameAttr = configElement.getAttribute(QNAME_ATTR);
                            if (StringUtils.isEmpty(qnameAttr)) {
                                throw new DatabaseConfigurationException("Lucene configuration element 'inline' needs an attribute 'qname'");
                            }
                            if (specialNodes == null) {
                                specialNodes = new TreeMap<>();
                            }
                            specialNodes.put(parseQName(qnameAttr, namespaces), N_INLINE);
                            break;
                        }
                        case MATCH_SIBLING_ATTR_ELEMENT:
                        case HAS_SIBLING_ATTR_ELEMENT:
                        case HAS_ATTR_ELEMENT:
                        case MATCH_ATTR_ELEMENT: {
                            final boolean doMatch = localName.equals(MATCH_ATTR_ELEMENT) || localName.equals(MATCH_SIBLING_ATTR_ELEMENT);
                            final boolean onSibling = localName.equals(HAS_SIBLING_ATTR_ELEMENT) || localName.equals(MATCH_SIBLING_ATTR_ELEMENT);

                            if (onSibling && !isAttributeNode()) {
                                throw new DatabaseConfigurationException(
                                        "Lucene module: " + localName + " can only be used on attribute");
                            } else if (!onSibling && isAttributeNode()) {
                                throw new DatabaseConfigurationException(
                                        "Lucene module: " + localName + " can not be used on attribute");
                            }

                            final String qname = configElement.getAttribute("qname");
                            if (StringUtils.isEmpty(qname)) {
                                throw new DatabaseConfigurationException("Lucene configuration element '" + localName + " needs an attribute 'qname'");
                            }

                            float boost;
                            final String boostStr = configElement.getAttribute("boost");
                            try {
                                boost = Float.parseFloat(boostStr);
                            } catch (NumberFormatException e) {
                                throw new DatabaseConfigurationException(
                                        "Invalid value for attribute 'boost'. "
                                        + "Expected float, got: " + boostStr);
                            }

                            String value = null;
                            if (doMatch) {
                                value = configElement.getAttribute("value");
                                if (StringUtils.isEmpty(value)) {
                                    throw new DatabaseConfigurationException("Lucene configuration element '" + localName + " needs an attribute 'value'");
                                }
                            }

                            if (matchAttrs == null)
                                matchAttrs = new MultiValueMap();

                            matchAttrs.put(qname, new MatchAttrData(qname, value, boost, onSibling));
                            break;
                        }
                    }
                }
            }
            child = child.getNextSibling();
        }
    }

    // return saved Analyzer for use in LuceneMatchListener
    public Analyzer getAnalyzer() {
        return type.getAnalyzer();
    }

    public String getAnalyzerId() {
        return type.getAnalyzerId();
    }

    public QName getQName() {
        return path.getLastComponent();
    }

    public NodePathPattern getNodePathPattern() {
        return path;
    }

    public float getBoost() {
        return type.getBoost();
    }

    /**
     * Get boost by matching the config with given attributes
     * (e.g. sibling or child atributes)
     * if no match, the value from getBoost() is returned
     */
    public float getAttrBoost(Collection<AttrImpl> attributes) {
        float boost = 0;
        boolean hasBoost = false;

        for (Attr attr : attributes) {
            Collection<MatchAttrData> matchAttrData
                    = (Collection<MatchAttrData>) matchAttrs.get(attr.getName());

            if (matchAttrData == null) {
                continue;
            }
            for (MatchAttrData matchAttrDatum : matchAttrData) {
                // if matchAttr value is null we don't care about the value
                if (matchAttrDatum.value == null
                        || matchAttrDatum.value.equals(attr.getValue())) {
                    hasBoost = true;
                    boost += matchAttrDatum.boost;
                    // we matched the attribute already, but since we allow
		    // further boost on the attribute, e g
		    // both from "has-attribute" and "match-attribute"
		    // there is no break here
                }
            }
        }

        if (hasBoost) {
            return boost;
	} else {
            return getBoost();
	}
    }

    public void setName(String name) {
		this.name = name;
    }

    public String getName() {
	return name;
    }

    public void add(LuceneIndexConfig config) {
	if (nextConfig == null)
	    nextConfig = config;
	else
	    nextConfig.add(config);
    }

    public LuceneIndexConfig getNext() {
	return nextConfig;
    }

    private boolean isAttributeNode() {
        return path.getLastComponent().getNameType() == ElementValue.ATTRIBUTE;
    }

    /**
     * @return true if this index can be queried by name
     */
    public boolean isNamed() {
	return name != null;
    }

    public boolean isIgnoredNode(QName qname) {
        return specialNodes != null && specialNodes.get(qname) == N_IGNORE;
    }

    public boolean isInlineNode(QName qname) {
        return specialNodes != null && specialNodes.get(qname) == N_INLINE;
    }

    public List<LuceneFacetConfig> getFacets() {
        return facets;
    }

    public static QName parseQName(Element config, Map<String, String> namespaces) throws DatabaseConfigurationException {
        String name = config.getAttribute(QNAME_ATTR);
        if (StringUtils.isEmpty(name))
            throw new DatabaseConfigurationException("Lucene index configuration error: element " + config.getNodeName() +
                    " must have an attribute " + QNAME_ATTR);

        return parseQName(name, namespaces);
    }

    protected static QName parseQName(String name, Map<String, String> namespaces) throws DatabaseConfigurationException {
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

            final QName qname;
            if (isAttribute) {
                qname = new QName(localName, namespaceURI, prefix, ElementValue.ATTRIBUTE);
            } else {
                qname = new QName(localName, namespaceURI, prefix);
            }
            return qname;
        } catch (QName.IllegalQNameException e) {
            throw new DatabaseConfigurationException("Lucene index configuration error: " + e.getMessage(), e);
        }
    }

    public boolean match(NodePath other) {
        if (isQNameIndex) {
            final QName qn1 = path.getLastComponent();
            final QName qn2 = other.getLastComponent();
            return qn1.getNameType() == qn2.getNameType() && qn2.equals(qn1);
        }
        return path.match(other);
    }

    @Override
    public String toString() {
	return path.toString();
    }

    boolean shouldReindexOnAttributeChange() {
        return matchAttrs != null;
    }

    private static class MatchAttrData {

        final String qname;
        final String value;
        final float boost;
        final boolean onSibling;

        MatchAttrData(String qname, String value, float boost, boolean onSibling) {
            this.qname = qname;
            this.value = value;
            this.boost = boost;
            this.onSibling = onSibling;
        }
    }
}
