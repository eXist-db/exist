package org.exist.indexing.range;

import org.exist.dom.AttrImpl;
import org.exist.dom.CharacterDataImpl;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.XMLString;

import java.util.ArrayList;
import java.util.List;

public class SimpleTextCollector implements TextCollector {

    private boolean includeNested = true;
    private RangeIndexConfigElement config = null;
    private XMLString buf = new XMLString();
    private int wsTreatment = XMLString.SUPPRESS_NONE;

    public SimpleTextCollector(RangeIndexConfigElement config, boolean includeNested, int wsTreatment) {
        this.config = config;
        this.includeNested = includeNested;
        this.wsTreatment = wsTreatment;
    }

    public SimpleTextCollector(String content) {
        buf.append(content);
    }

    @Override
    public void startElement(QName qname, NodePath path) {
    }

    @Override
    public void endElement(QName qname, NodePath path) {
    }

    @Override
    public void characters(CharacterDataImpl text, NodePath path) {
        if (includeNested || config.match(path)) {
            buf.append(text.getXMLString());
        }
    }

    @Override
    public void attribute(AttrImpl attribute, NodePath path) {
    }

    @Override
    public int length() {
        return buf.length();
    }

    @Override
    public List<Field> getFields() {
        List<Field> fields = new ArrayList<Field>(1);
        fields.add(new Field(buf, wsTreatment));
        return fields;
    }
}
