package org.exist.indexing.lucene;

import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.util.Map;

public class LuceneIndexConfig {

    private final static String ANALYZER_ID_ATTR = "analyzer";
    private static final String QNAME_ATTR = "qname";
    private static final String MATCH_ATTR = "match";
    private final static String BOOST_ATTRIB = "boost";

    private String analyzerId = null;

    private QName qname = null;

    private NodePath path = null;

    private float boost = -1;

    public LuceneIndexConfig(Element config, Map namespaces, AnalyzerConfig analyzers) throws DatabaseConfigurationException {
        if (config.hasAttribute(QNAME_ATTR)) {
            qname = parseQName(config, namespaces);
        } else {
            String matchPath = config.getAttribute(MATCH_ATTR);
            path = new NodePath(namespaces, matchPath);
            if (path.length() == 0)
                throw new DatabaseConfigurationException("Lucene module: Invalid match path in collection config: " +
                    matchPath);
            qname = path.getComponent(path.length() - 1);
        }
        String id = config.getAttribute(ANALYZER_ID_ATTR);
        if (id != null && id.length() > 0) {
            if (analyzers.getAnalyzerById(id) == null)
                throw new DatabaseConfigurationException("No analyzer configured for id " + id);
            analyzerId = id;
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
    }

    public String getAnalyzerId() {
        return analyzerId;
    }

    public QName getQName() {
        return qname;
    }

    public NodePath getNodePath() {
        return path;
    }

    public float getBoost() {
        return boost;
    }
    
    protected static QName parseQName(Element config, Map namespaces) throws DatabaseConfigurationException {
        String name = config.getAttribute(QNAME_ATTR);
        if (name == null || name.length() == 0)
            throw new DatabaseConfigurationException("Lucene index configuration error: element " + config.getNodeName() +
                    " must have an attribute " + QNAME_ATTR);

        return parseQName(name, namespaces);
    }

    protected static QName parseQName(String name, Map namespaces) throws DatabaseConfigurationException {
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
                namespaceURI = (String) namespaces.get(prefix);
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

    public boolean match(NodePath other) {
        return path.match(other);
    }
}

