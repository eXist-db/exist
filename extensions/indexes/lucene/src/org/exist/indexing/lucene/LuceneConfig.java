package org.exist.indexing.lucene;

import org.exist.util.DatabaseConfigurationException;
import org.exist.dom.QName;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.apache.lucene.analysis.Analyzer;

import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.List;

public class LuceneConfig {

    private final static String CONFIG_ROOT = "lucene";
    private final static String INDEX_ELEMENT = "text";
    private final static String ANALYZER_ELEMENT = "analyzer";

    private Map qnames = new TreeMap();

    private AnalyzerConfig analyzers = new AnalyzerConfig();

    public LuceneConfig(NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
        parseConfig(configNodes, namespaces);
    }

    public boolean matches(QName qname) {
        return qnames.get(qname) != null;
    }

    public Analyzer getAnalyzer(QName qname) {
        LuceneIndexConfig config = (LuceneIndexConfig) qnames.get(qname);
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
                    LuceneIndexConfig config = new LuceneIndexConfig((Element) node, namespaces, analyzers);
                    qnames.put(config.getQName(), config);
                }
            }
        }
    }
}
