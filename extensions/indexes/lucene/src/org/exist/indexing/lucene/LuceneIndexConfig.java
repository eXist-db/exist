package org.exist.indexing.lucene;

import org.exist.dom.QName;
import org.exist.storage.ElementValue;
import org.exist.util.DatabaseConfigurationException;

import java.util.Map;

public class LuceneIndexConfig {

    private QName qname;

    public LuceneIndexConfig(Map namespaces, String name) throws DatabaseConfigurationException {
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
    }

    public QName getQName() {
        return qname;
    }
}
