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
        protected final boolean attribute;
        protected final String name;
        protected final XMLString content;
        protected final int wsTreatment;

        public Field(XMLString content, int wsTreatment) {
            this.content = content;
            this.attribute = false;
            this.name = null;
            this.wsTreatment = wsTreatment;
        }

        public Field(String name, boolean isAttribute, int wsTreatment) {
            this.name = name;
            this.attribute = isAttribute;
            this.wsTreatment = wsTreatment;
            this.content = new XMLString();
        }

        public String getContent() {
            if (wsTreatment != XMLString.SUPPRESS_NONE) {
                return content.normalize(wsTreatment).toString();
            }
            return content.toString();
        }

        public String getName() {
            return name;
        }

        public boolean isNamed() {
            return name != null;
        }

        public boolean isAttribute() {
            return attribute;
        }
    }
}
