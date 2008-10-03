package org.exist.indexing.lucene;

import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.util.Map;

public class LuceneIndexConfig {

    private static final String QNAME_ATTR = "qname";
    private final static String ANALYZER_ID_ATTR = "analyzer";

    private QName qname;
    private String analyzerId = null;

    public LuceneIndexConfig(Element config, Map namespaces, AnalyzerConfig analyzers) throws DatabaseConfigurationException {
        String name = config.getAttribute(QNAME_ATTR);
        if (name == null || name.length() == 0)
            throw new DatabaseConfigurationException("Configuration error: element " + config.getNodeName() +
                    " must have an attribute " + QNAME_ATTR);

        boolean isAttribute = false;
        if (name.startsWith("@")) {
            isAttribute = true;
            name = name.substring(1);
        }
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
        qname = new QName(localName, namespaceURI, prefix);
        if (isAttribute)
            qname.setNameType(ElementValue.ATTRIBUTE);

        String id = config.getAttribute(ANALYZER_ID_ATTR);
        if (id != null && id.length() > 0) {
            if (analyzers.getAnalyzerById(id) == null)
                throw new DatabaseConfigurationException("No analyzer configured for id " + id);
            analyzerId = id;
        }
    }

    public QName getQName() {
        return qname;
    }

    public String getAnalyzerId() {
        return analyzerId;
    }
}

