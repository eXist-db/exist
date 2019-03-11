package org.exist.indexing.range;

import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.AbstractCharacterData;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.XMLString;

import java.util.List;

public interface TextCollector {

    void startElement(QName qname, NodePath path);

    void endElement(QName qname, NodePath path);

    void characters(AbstractCharacterData text, NodePath path);

    void attribute(AttrImpl attribute, NodePath path);

    int length();

    List<Field> getFields();

    boolean hasFields();

    class Field {
        private final boolean attribute;
        private final String name;
        private final int wsTreatment;
        private final boolean caseSensitive;
        private XMLString content;

        public Field(final XMLString content, final int wsTreatment, final boolean caseSensitive) {
            this.content = content;
            this.attribute = false;
            this.name = null;
            this.wsTreatment = wsTreatment;
            this.caseSensitive = caseSensitive;
        }

        public Field(final String name, final boolean isAttribute, final int wsTreatment, final boolean caseSensitive) {
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
                final XMLString normalized = content.normalize(wsTreatment);
                try {
                    return normalized.toString();
                } finally {
                    if (normalized != content) {
                        normalized.reset();
                    }
                }
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

        public void append(final String value) {
            content.append(value);
        }
        public void append(final XMLString value) {
            content.append(value);
        }
    }
}
