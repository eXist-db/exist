package org.exist.indexing.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Map;
import java.util.TreeMap;

public class LuceneIndexConfig {

    private final static String N_INLINE = "inline";
    private final static String N_IGNORE = "ignore";

    private final static String ANALYZER_ID_ATTR = "analyzer";
    private static final String QNAME_ATTR = "qname";
    private static final String MATCH_ATTR = "match";
    private final static String BOOST_ATTRIB = "boost";

    private final static String IGNORE_ELEMENT = "ignore";
    private final static String INLINE_ELEMENT = "inline";

    private String analyzerId = null;
    // save Analyzer for later use in LuceneMatchListener
    private Analyzer analyzer = null;

    private QName qname = null;

    private NodePath path = null;

    private float boost = -1;

    private Map<QName, String> specialNodes = null;

    public LuceneIndexConfig(Element config, Map<String, String> namespaces, AnalyzerConfig analyzers) throws DatabaseConfigurationException {
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
    	// save Analyzer for later use in LuceneMatchListener
        if (id != null && id.length() > 0) {
        	analyzer = analyzers.getAnalyzerById(id);
            if (analyzer == null)
                throw new DatabaseConfigurationException("No analyzer configured for id " + id);
            analyzerId = id;
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
        parse(config, namespaces);
    }

    private void parse(Element root, Map<String, String> namespaces) throws DatabaseConfigurationException {
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (IGNORE_ELEMENT.equals(child.getLocalName())) {
                    String qnameAttr = ((Element) child).getAttribute(QNAME_ATTR);
                    if (qnameAttr == null || qnameAttr.length() == 0)
                        throw new DatabaseConfigurationException("Lucene configuration element 'ignore' needs an attribute 'qname'");
                    if (specialNodes == null)
                        specialNodes = new TreeMap<QName, String>();
                    specialNodes.put(parseQName(qnameAttr, namespaces), N_IGNORE);
                } else if (INLINE_ELEMENT.equals(child.getLocalName())) {
                    String qnameAttr = ((Element) child).getAttribute(QNAME_ATTR);
                    if (qnameAttr == null || qnameAttr.length() == 0)
                        throw new DatabaseConfigurationException("Lucene configuration element 'inline' needs an attribute 'qname'");
                    if (specialNodes == null)
                        specialNodes = new TreeMap<QName, String>();
                    specialNodes.put(parseQName(qnameAttr, namespaces), N_INLINE);
                }
            }
            child = child.getNextSibling();
        }
    }

    // return saved Analyzer for use in LuceneMatchListener
    public Analyzer getAnalyzer() {
        return analyzer;
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

    public boolean isIgnoredNode(QName qname) {
        return specialNodes != null && specialNodes.get(qname) == N_IGNORE;
    }

    public boolean isInlineNode(QName qname) {
        return specialNodes != null && specialNodes.get(qname) == N_INLINE;
    }

    protected static QName parseQName(Element config, Map<String, String> namespaces) throws DatabaseConfigurationException {
        String name = config.getAttribute(QNAME_ATTR);
        if (name == null || name.length() == 0)
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

