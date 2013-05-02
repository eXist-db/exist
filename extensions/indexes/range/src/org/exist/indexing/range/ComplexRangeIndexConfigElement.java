package org.exist.indexing.range;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComplexRangeIndexConfigElement extends RangeIndexConfigElement {

    public final static String FIELD_ELEMENT = "field";

    private static final Logger LOG = Logger.getLogger(ComplexRangeIndexConfigElement.class);

    private Map<String, RangeIndexConfigField> fields = new HashMap<String, RangeIndexConfigField>();

    public ComplexRangeIndexConfigElement(Element node, NodeList children, Map<String, String> namespaces)
            throws DatabaseConfigurationException {
        super();
        String match = node.getAttribute("match");
        if (match != null) {
            try {
                path = new NodePath(namespaces, match, false);
                if (path.length() == 0)
                    throw new DatabaseConfigurationException("Range index module: Invalid match path in collection config: " + match);
            } catch (IllegalArgumentException e) {
                throw new DatabaseConfigurationException("Range index module: invalid qname in configuration: " + e.getMessage());
            }
        }

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (FIELD_ELEMENT.equals(child.getLocalName())) {
                    RangeIndexConfigField field = new RangeIndexConfigField(path, (Element)child, namespaces);
                    fields.put(field.getName(), field);
                } else {
                    LOG.warn("Invalid element encountered for range index configuration: " + child.getLocalName());
                }
            }
        }
    }

    @Override
    public boolean isComplex() {
        return true;
    }

    @Override
    public boolean match(NodePath other) {
        if (path.match(other))
            return true;
        return false;
    }

    @Override
    public boolean find(NodePath other) {
        return (getField(other) != null);
    }

    @Override
    public TextCollector getCollector() {
        return new ComplexTextCollector(this);
    }

    public RangeIndexConfigField getField(NodePath path) {
        for (RangeIndexConfigField field: fields.values()) {
            if (field.match(path))
                return field;
        }
        return null;
    }

    @Override
    public int getType(String fieldName) {
        RangeIndexConfigField field = fields.get(fieldName);
        if (field != null) {
            return field.getType();
        }
        return Type.STRING;
    }
}
