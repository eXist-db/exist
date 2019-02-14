package org.exist.indexing.range;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

public class RangeIndexConfig {

    static final String CONFIG_ROOT = "range";
    static final String CREATE_ELEM = "create";
    private static final String FIELD_ELEM = "field";
    private final static String CONDITION_ELEM = "condition";

    private static final Logger LOG = LogManager.getLogger(RangeIndexConfig.class);

    private Map<QName, RangeIndexConfigElement> paths = new TreeMap<>();

    private Analyzer analyzer;

    private PathIterator iterator = new PathIterator();

    public RangeIndexConfig(NodeList configNodes, Map<String, String> namespaces) {
        parse(configNodes, namespaces);
    }

    public RangeIndexConfig(RangeIndexConfig other) {
        this.paths = other.paths;
        this.analyzer = other.analyzer;
    }

    /* find one simple configuration for path */
    public RangeIndexConfigElement find(NodePath path) {
        for (RangeIndexConfigElement rice : paths.values()) {
            do {
                if (rice.find(path) && !rice.isComplex()) {
                    return rice;
                }
                rice = rice.getNext();
            } while (rice != null);
        }
        return null;
    }

    /* find all complex configurations for path (that might have different conditions) */
    public List<ComplexRangeIndexConfigElement> findAll(NodePath path) {
        ArrayList<ComplexRangeIndexConfigElement> rices = new ArrayList<ComplexRangeIndexConfigElement>();

        for (RangeIndexConfigElement rice : paths.values()) {
            do {
                if (rice.find(path) && rice.isComplex()) {
                    rices.add((ComplexRangeIndexConfigElement)rice);
                }

                rice = rice.getNext();
            } while (rice != null);
        }
        return rices;
    }

    private void parse(NodeList configNodes, Map<String, String> namespaces) {
        for(int i = 0; i < configNodes.getLength(); i++) {
            Node node = configNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && CONFIG_ROOT.equals(node.getLocalName())) {
                parseChildren(node.getChildNodes(), namespaces);
            }
        }
    }

    private void parseChildren(NodeList configNodes, Map<String, String> namespaces) {
        Node node;
        for(int i = 0; i < configNodes.getLength(); i++) {
            node = configNodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE && CREATE_ELEM.equals(node.getLocalName())) {
                try {
                    NodeList fields = getFieldsAndConditions((Element) node);
                    RangeIndexConfigElement newConfig;
                    if (fields.getLength() > 0) {
                        newConfig = new ComplexRangeIndexConfigElement((Element) node, fields, namespaces);
                    } else {
                        newConfig = new RangeIndexConfigElement((Element) node, namespaces);
                    }
                    RangeIndexConfigElement idxConf = paths.get(newConfig.getNodePath().getLastComponent());
                    if (idxConf == null) {
                        paths.put(newConfig.getNodePath().getLastComponent(), newConfig);
                    } else {
                        idxConf.add(newConfig);
                    }
                } catch (final DatabaseConfigurationException e) {
                    String uri = null;
                    final Document doc = node.getOwnerDocument();
                    if(doc != null) {
                        uri = doc.getDocumentURI();
                    }

                    if(uri != null) {
                        LOG.error("Invalid range index configuration (" + uri + "): " + e.getMessage());
                    } else {
                        LOG.error("Invalid range index configuration: " + e.getMessage());
                    }
                }
            }
        }
        // default analyzer
        analyzer = new KeywordAnalyzer();
    }

    public Analyzer getDefaultAnalyzer() {
        return analyzer;
    }

    public Analyzer getAnalyzer(QName qname, String fieldName) {
        Analyzer analyzer = null;
        if (qname != null) {
            RangeIndexConfigElement idxConf = paths.get(qname);
            if (idxConf != null) {
                analyzer = idxConf.getAnalyzer(null);
            }
        } else {
            for (RangeIndexConfigElement idxConf: paths.values()) {
                if (idxConf.isComplex()) {
                    analyzer = idxConf.getAnalyzer(fieldName);
                    if (analyzer != null) {
                        break;
                    }
                }
            }
        }
        return analyzer;
    }

    public boolean isCaseSensitive(QName qname, String fieldName) {
        boolean caseSensitive = true;
        if (qname != null) {
            RangeIndexConfigElement idxConf = paths.get(qname);
            if (idxConf != null) {
                caseSensitive = idxConf.isCaseSensitive(fieldName);
            }
        } else {
            for (RangeIndexConfigElement idxConf: paths.values()) {
                if (idxConf.isComplex()) {
                    caseSensitive = idxConf.isCaseSensitive(fieldName);
                    if (!caseSensitive) {
                        break;
                    }
                }
            }
        }
        return caseSensitive;
    }

    public Iterator<RangeIndexConfigElement> getConfig(NodePath path) {
        iterator.reset(path);
        return iterator;
    }

    private NodeList getFieldsAndConditions(Element root) {
        NodeListImpl fields = new NodeListImpl();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && (FIELD_ELEM.equals(node.getLocalName()) || CONDITION_ELEM.equals(node.getLocalName()))) {
                fields.add(node);
            }
        }
        return fields;
    }

    public boolean matches(NodePath path) {
        RangeIndexConfigElement idxConf = paths.get(path.getLastComponent());
        while (idxConf != null) {
            if (idxConf.match(path))
                return true;
            idxConf = idxConf.getNext();
        }
        return false;
    }

    public int getType(String field) {
        for (RangeIndexConfigElement conf : paths.values()) {
            if (conf.isComplex()) {
                int type = conf.getType(field);
                if (type != Type.ITEM) {
                    return type;
                }
            }
        }
        return Type.ITEM;
    }

    private class PathIterator implements Iterator<RangeIndexConfigElement> {

        private RangeIndexConfigElement nextConfig;
        private NodePath path;
        private boolean atLast = false;

        protected void reset(NodePath path) {
            this.atLast = false;
            this.path = path;
            nextConfig = paths.get(path.getLastComponent());
            if (nextConfig == null) {
                atLast = true;
            }
        }

        @Override
        public boolean hasNext() {
            return (nextConfig != null);
        }

        @Override
        public RangeIndexConfigElement next() {
            if (nextConfig == null)
                return null;

            RangeIndexConfigElement currentConfig = nextConfig;
            nextConfig = nextConfig.getNext();
            if (nextConfig == null && !atLast) {
                atLast = true;
            }
            return currentConfig;
        }

        @Override
        public void remove() {
            //Nothing to do
        }

    }
}