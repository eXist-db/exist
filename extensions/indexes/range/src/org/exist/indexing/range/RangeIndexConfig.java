package org.exist.indexing.range;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.collation.CollationKeyAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.Version;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.Collations;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class RangeIndexConfig {

    private static final String CONFIG_ROOT = "range";
    private static final String CREATE_ELEM = "create";
    private static final String FIELD_ELEM = "field";

    private static final Logger LOG = Logger.getLogger(RangeIndexConfig.class);

    private Map<QName, RangeIndexConfigElement> paths = new TreeMap<QName, RangeIndexConfigElement>();

    private Analyzer analyzer;

    private PathIterator iterator = new PathIterator();

    public RangeIndexConfig(NodeList configNodes, Map<String, String> namespaces) {
        parse(configNodes, namespaces);
    }

    public RangeIndexConfig(RangeIndexConfig other) {
        this.paths = other.paths;
        this.analyzer = other.analyzer;
    }

    public RangeIndexConfigElement find(NodePath path) {
        for (RangeIndexConfigElement rice : paths.values()) {
            if (rice.find(path)) {
                return rice;
            }
        }
        return null;
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
                    NodeList fields = getFields((Element) node);
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
                } catch (DatabaseConfigurationException e) {
                    LOG.error("Invalid range index configuration: " + e.getMessage());
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

    public Iterator<RangeIndexConfigElement> getConfig(NodePath path) {
        iterator.reset(path);
        return iterator;
    }

    private NodeList getFields(Element root) {
        NodeListImpl fields = new NodeListImpl();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && FIELD_ELEM.equals(node.getLocalName())) {
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