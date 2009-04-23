package org.exist.indexing.lucene;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

    private Map qnames = new TreeMap();
    private LuceneIndexConfig paths[] = null;

    private AnalyzerConfig analyzers = new AnalyzerConfig();

    public LuceneConfig(NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
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
        LuceneIndexConfig config = (LuceneIndexConfig) qnames.get(qn);
        if (config == null && paths != null) {
            for (int i = 0; i < paths.length; i++) {
                if (paths[i].match(path))
                    return paths[i];
            }
        }
        return null;
    }

    public Analyzer getAnalyzer(QName qname) {
        LuceneIndexConfig config = (LuceneIndexConfig) qnames.get(qname);
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

    public void getDefinedIndexes(List indexes) {
        for (Iterator ci = qnames.keySet().iterator(); ci.hasNext();) {
            QName qn = (QName) ci.next();
            indexes.add(qn);
        }
    }

    /**
     * Parse a configuration entry. The main configuration entries for this index
     * are the &lt;text&gt; elements. They may be enclosed by a &lt;lucene&gt; element.
     *
     * @param configNodes
     * @param namespaces
     * @throws org.exist.util.DatabaseConfigurationException
     */
    protected void parseConfig(NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
        Node node;
        for(int i = 0; i < configNodes.getLength(); i++) {
            node = configNodes.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                if (CONFIG_ROOT.equals(node.getLocalName()))
                    parseConfig(node.getChildNodes(), namespaces);
                else if (ANALYZER_ELEMENT.equals(node.getLocalName())) {
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
                }
            }
        }
    }

}
