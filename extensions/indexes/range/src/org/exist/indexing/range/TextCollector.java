package org.exist.indexing.range;

import org.exist.dom.AttrImpl;
import org.exist.dom.CharacterDataImpl;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.XMLString;
import org.exist.xquery.value.Type;

import java.util.List;

public interface TextCollector {

    public void startElement(QName qname, NodePath path);

    public void endElement(QName qname, NodePath path);

    public void characters(CharacterDataImpl text, NodePath path);

    public void attribute(AttrImpl attribute, NodePath path);

    public int length();

    public List<Field> getFields();

    public static class Field {
        final boolean attribute;
        final String name;
        final XMLString content;

        public Field(XMLString content) {
            this.content = content;
            this.attribute = false;
            this.name = null;
        }

        public Field(String name, boolean isAttribute) {
            this.name = name;
            this.attribute = isAttribute;
            this.content = new XMLString();
        }

        public boolean isNamed() {
            return name != null;
        }

        public boolean isAttribute() {
            return attribute;
        }
    }
}
