package org.exist.indexing.range;

import org.exist.dom.AttrImpl;
import org.exist.dom.CharacterDataImpl;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.XMLString;

import java.util.*;

public class ComplexTextCollector implements TextCollector {

    private ComplexRangeIndexConfigElement config;
    private List<Field> fields = new LinkedList<Field>();
    private RangeIndexConfigField currentField = null;
    private int length = 0;

    public ComplexTextCollector(ComplexRangeIndexConfigElement configuration) {
        config = configuration;
    }

    @Override
    public void startElement(QName qname, NodePath path) {
        RangeIndexConfigField fieldConf = config.getField(path);
        if (fieldConf != null) {
            currentField = fieldConf;
            Field field = new Field(currentField.getName(), false);
            fields.add(field);
        }

    }

    @Override
    public void endElement(QName qname, NodePath path) {
        if (currentField != null && currentField.match(path)) {
            currentField = null;
        }
    }

    @Override
    public void attribute(AttrImpl attribute, NodePath path) {
        RangeIndexConfigField fieldConf = config.getField(path);
        if (fieldConf != null) {
            Field field = new Field(fieldConf.getName(), true);
            field.content.append(attribute.getValue());
            fields.add(0, field);
        }
    }

    @Override
    public void characters(CharacterDataImpl text, NodePath path) {
        if (currentField != null) {
            Field field = fields.get(fields.size() - 1);
            if (!field.isAttribute()) {
                field.content.append(text.getXMLString());
                length += text.getXMLString().length();
            }
        }
    }

    @Override
    public int length() {
        return length;
    }

    public List<Field> getFields() {
        return fields;
    }
}
