package org.exist.indexing.lucene;

import java.util.*;

import org.apache.lucene.analysis.Analyzer;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class LuceneConfig {

    private final static String CONFIG_ROOT = "lucene";
    private final static String INDEX_ELEMENT = "text";
    private final static String ANALYZER_ELEMENT = "analyzer";
    private static final String INLINE_ELEMENT = "inline";
    private static final String IGNORE_ELEMENT = "ignore";
    private final static String BOOST_ATTRIB = "boost";

    private Map<QName, LuceneIndexConfig> qnames = new TreeMap<QName, LuceneIndexConfig>();
    private LuceneIndexConfig paths[] = null;

    private Set<QName> inlineNodes = null;
    private Set<QName> ignoreNodes = null;

    private float boost = -1;

    private AnalyzerConfig analyzers = new AnalyzerConfig();

    public LuceneConfig(NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        parseConfig(configNodes, namespaces);
    }

    public boolean matches(NodePath path) {
        QName qn = path.getComponent(path.length() - 1);
        if (qnames.containsKey(qn))
            return true;
        if (paths != null) {
            for (int i = 0; i < paths.length; i++) {
                if (paths[i].match(path))
                    return true;
            }
        }
        return false;
    }

    public LuceneIndexConfig getConfig(NodePath path) {
        QName qn = path.getComponent(path.length() - 1);
        LuceneIndexConfig config = qnames.get(qn);
        if (config == null && paths != null) {
            for (int i = 0; i < paths.length; i++) {
                if (paths[i].match(path)) {
                    config = paths[i];
                    break;
                }
            }
        }
        return config;
    }

    public Analyzer getAnalyzer(QName qname) {
        LuceneIndexConfig config = qnames.get(qname);
        if (config == null && paths != null) {
            for (int i = 0; i < paths.length; i++) {
                LuceneIndexConfig path = paths[i];
                if (path.getQName().compareTo(qname) == 0) {
                    config = path;
                    break;
                }
            }
        }
        if (config != null) {
            String id = config.getAnalyzerId();
            if (id != null)
                return analyzers.getAnalyzerById(config.getAnalyzerId());
        }
        return analyzers.getDefaultAnalyzer();
    }

    public Analyzer getAnalyzer(NodePath nodePath) {
        if (nodePath.length() == 0)
            throw new RuntimeException();
        LuceneIndexConfig config = getConfig(nodePath);
        if (config != null) {
            String id = config.getAnalyzerId();
            if (id != null)
                return analyzers.getAnalyzerById(config.getAnalyzerId());
        }
        return analyzers.getDefaultAnalyzer();
    }

    public boolean isInlineNode(QName qname) {
        return inlineNodes != null && inlineNodes.contains(qname);
    }

    public boolean isIgnoredNode(QName qname) {
        return ignoreNodes != null && ignoreNodes.contains(qname);
    }
    
    public void getDefinedIndexes(List<QName> indexes) {
        for (QName qn : qnames.keySet()) {
            indexes.add(qn);
        }
    }

    public float getBoost() {
        return boost;
    }
    
    /**
     * Parse a configuration entry. The main configuration entries for this index
     * are the &lt;text&gt; elements. They may be enclosed by a &lt;lucene&gt; element.
     *
     * @param configNodes
     * @param namespaces
     * @throws org.exist.util.DatabaseConfigurationException
     */
    protected void parseConfig(NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        Node node;
        for(int i = 0; i < configNodes.getLength(); i++) {
            node = configNodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                if (CONFIG_ROOT.equals(node.getLocalName())) {
                    Element elem = (Element) node;
                    if (elem.hasAttribute(BOOST_ATTRIB)) {
                        String value = elem.getAttribute(BOOST_ATTRIB);
                        try {
                            boost = Float.parseFloat(value);
                        } catch (NumberFormatException e) {
                            throw new DatabaseConfigurationException("Invalid value for 'boost' attribute in " +
                                    "lucene index config: float expected, got " + value);
                        }
                    }
                    parseConfig(node.getChildNodes(), namespaces);
                } else if (ANALYZER_ELEMENT.equals(node.getLocalName())) {
                    analyzers.addAnalyzer((Element) node);
                } else if (INDEX_ELEMENT.equals(node.getLocalName())) {
                    Element elem = (Element) node;
                    LuceneIndexConfig config = new LuceneIndexConfig(elem, namespaces, analyzers);
                    if (config.getNodePath() == null)
                        qnames.put(config.getQName(), config);
                    else {
                        if (paths == null) {
                            paths = new LuceneIndexConfig[1];
                            paths[0] = config;
                        } else {
                            LuceneIndexConfig np[] = new LuceneIndexConfig[paths.length + 1];
                            System.arraycopy(paths, 0, np, 0, paths.length);
                            np[paths.length] = config;
                            paths = np;
                        }
                    }
                } else if (INLINE_ELEMENT.equals(node.getLocalName())) {
                    Element elem = (Element) node;
                    QName qname = LuceneIndexConfig.parseQName(elem, namespaces);
                    if (inlineNodes == null)
                        inlineNodes = new TreeSet<QName>();
                    inlineNodes.add(qname);
                } else if (IGNORE_ELEMENT.equals(node.getLocalName())) {
                    Element elem = (Element) node;
                    QName qname = LuceneIndexConfig.parseQName(elem, namespaces);
                    if (ignoreNodes == null)
                        ignoreNodes = new TreeSet<QName>();
                    ignoreNodes.add(qname);
                }
            }
        }
    }
}
