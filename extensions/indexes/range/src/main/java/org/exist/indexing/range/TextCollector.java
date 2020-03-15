/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
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
