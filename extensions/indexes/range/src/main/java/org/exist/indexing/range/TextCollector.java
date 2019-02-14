package org.exist.indexing.range;

import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.AbstractCharacterData;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.XMLString;

import java.util.List;

public interface TextCollector {

    public void startElement(QName qname, NodePath path);

    public void endElement(QName qname, NodePath path);

    public void characters(AbstractCharacterData text, NodePath path);

    public void attribute(AttrImpl attribute, NodePath path);

    public int length();

    public List<Field> getFields();

    public boolean hasFields();

    public static class Field {
        protected final boolean attribute;
        protected final String name;
        protected final int wsTreatment;
        protected final boolean caseSensitive;
        protected XMLString content;

        public Field(XMLString content, int wsTreatment, boolean caseSensitive) {
            this.content = content;
            this.attribute = false;
            this.name = null;
            this.wsTreatment = wsTreatment;
            this.caseSensitive = caseSensitive;
        }

        public Field(String name, boolean isAttribute, int wsTreatment, boolean caseSensitive) {
            this.name = name;
            this.attribute = isAttribute;
            this.wsTreatment = wsTreatment;
            this.content = new XMLString();
            this.caseSensitive = caseSensitive;
        }

        public String getContent() {
            if (!caseSensitive) {
                content = content.transformToLower();
            }
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
