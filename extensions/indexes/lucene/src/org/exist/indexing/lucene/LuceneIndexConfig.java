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

    private static final String QNAME_ATTR = "qname";
    private static final String MATCH_ATTR = "match";

    private final static String IGNORE_ELEMENT = "ignore";
    private final static String INLINE_ELEMENT = "inline";
	private static final String FIELD_ATTR = "field";
	private static final String TYPE_ATTR = "type";

    private String name = null;

    private NodePath path = null;

    private boolean isQNameIndex = false;

    private Map<QName, String> specialNodes = null;

    private LuceneIndexConfig nextConfig = null;
    
    private FieldType type = null;
    
    public LuceneIndexConfig(Element config, Map<String, String> namespaces, AnalyzerConfig analyzers,
    			Map<String, FieldType> fieldTypes) throws DatabaseConfigurationException {
        if (config.hasAttribute(QNAME_ATTR)) {
            QName qname = parseQName(config, namespaces);
            path = new NodePath(qname);
            isQNameIndex = true;
        } else {
            String matchPath = config.getAttribute(MATCH_ATTR);
            try {
				path = new NodePath(namespaces, matchPath);
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
        return type.getAnalyzer();
    }

    public String getAnalyzerId() {
        return type.getAnalyzerId();
    }

    public QName getQName() {
        return path.getLastComponent();
    }

    public NodePath getNodePath() {
        return path;
    }

    public float getBoost() {
        return type.getBoost();
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
    	if (isQNameIndex)
    		return other.getLastComponent().equalsSimple(path.getLastComponent());
        return path.match(other);
    }

	@Override
	public String toString() {
		return path.toString();
	}
}

