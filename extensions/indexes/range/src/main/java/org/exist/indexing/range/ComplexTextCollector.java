package org.exist.indexing.range;

import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.AbstractCharacterData;
import org.exist.dom.QName;
import org.exist.storage.NodePath;

import java.util.*;

public class ComplexTextCollector implements TextCollector {

    private NodePath parentPath;
    private ComplexRangeIndexConfigElement config;
    private List<Field> fields = new LinkedList<Field>();
    private RangeIndexConfigField currentField = null;
    private int length = 0;

    public ComplexTextCollector(ComplexRangeIndexConfigElement configuration, NodePath parentPath) {
        this.config = configuration;
        this.parentPath = new NodePath(parentPath, false);
    }

    @Override
    public void startElement(QName qname, NodePath path) {
        RangeIndexConfigField fieldConf = config.getField(parentPath, path);
        if (fieldConf != null) {
            currentField = fieldConf;
            Field field = new Field(currentField.getName(), false, fieldConf.whitespaceTreatment(), fieldConf.isCaseSensitive());
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
        RangeIndexConfigField fieldConf = config.getField(parentPath, path);
        if (fieldConf != null) {
            Field field = new Field(fieldConf.getName(), true, fieldConf.whitespaceTreatment(), fieldConf.isCaseSensitive());
            field.append(attribute.getValue());
            fields.add(0, field);
        }
    }

    @Override
    public void characters(AbstractCharacterData text, NodePath path) {
        if (currentField != null) {
            Field field = fields.get(fields.size() - 1);
            if (!field.isAttribute() && (currentField.includeNested() || currentField.match(path))) {
                field.append(text.getXMLString());
                length += text.getXMLString().length();
            }
        }
    }

    @Override
    public boolean hasFields() {
        return true;
    }

    @Override
    public int length() {
        return length;
    }

    public List<Field> getFields() {
        return fields;
    }

    public ComplexRangeIndexConfigElement getConfig() {
        return config;
    }
}
