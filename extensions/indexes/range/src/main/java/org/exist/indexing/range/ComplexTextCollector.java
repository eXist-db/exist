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

import java.util.*;

public class ComplexTextCollector implements TextCollector {

    private NodePath parentPath;
    private ComplexRangeIndexConfigElement config;
    private List<Field> fields = new LinkedList<>();
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
            fields.addFirst(field);
        }
    }

    @Override
    public void characters(AbstractCharacterData text, NodePath path) {
        if (currentField != null) {
            Field field = fields.getLast();
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
