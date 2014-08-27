package org.exist.indexing.range;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.exist.dom.NodeListImpl;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Map;

public class RangeIndexConfigXML {

    private static final String CONFIG_ROOT = "range";
    private static final String CREATE_ELEM = "create";
    private static final String FIELD_ELEM = "field";

    public static RangeIndexConfig parse(NodeList configNodes, Map<String, String> namespaces) {
    	RangeIndexConfig conf = new RangeIndexConfig();
    	for(int i = 0; i < configNodes.getLength(); i++) {
            Node node = configNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && CONFIG_ROOT.equals(node.getLocalName())) {
                parseChildren(conf, node.getChildNodes(), namespaces);
            }
        }
    	return conf;
    }

    private static void parseChildren(RangeIndexConfig conf, NodeList configNodes, Map<String, String> namespaces) {
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
                    RangeIndexConfigElement idxConf = conf.paths.get(newConfig.getNodePath().getLastComponent());
                    if (idxConf == null) {
                    	conf.paths.put(newConfig.getNodePath().getLastComponent(), newConfig);
                    } else {
                        idxConf.add(newConfig);
                    }
                } catch (DatabaseConfigurationException e) {
                    RangeIndexConfig.LOG.error("Invalid range index configuration: " + e.getMessage());
                }
            }
        }
        // default analyzer
        conf.analyzer = new KeywordAnalyzer();
    }

    private static NodeList getFields(Element root) {
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
}