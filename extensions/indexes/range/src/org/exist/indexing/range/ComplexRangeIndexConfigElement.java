/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.indexing.range;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.exist.dom.QName;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

public class ComplexRangeIndexConfigElement extends RangeIndexConfigElement {

    public static final Comparator<ComplexRangeIndexConfigElement> NUM_CONDITIONS_COMPARATOR =
            Comparator.comparingInt(ComplexRangeIndexConfigElement::getNumberOfConditions).reversed();


    public final static String FIELD_ELEMENT = "field";
    public final static String CONDITION_ELEMENT = "condition";

    private static final Logger LOG = LogManager.getLogger(ComplexRangeIndexConfigElement.class);

    private Map<String, RangeIndexConfigField> fields = new HashMap<String, RangeIndexConfigField>();


    protected ArrayList<RangeIndexConfigCondition> conditions = new ArrayList<RangeIndexConfigCondition>();
    public ArrayList<RangeIndexConfigCondition> getConditions() {
        return conditions;
    }
    public int getNumberOfConditions() { return conditions.size(); }


    public ComplexRangeIndexConfigElement(final Element node, final NodeList children, final Map<String, String> namespaces)
            throws DatabaseConfigurationException {
        super(node, namespaces);

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (FIELD_ELEMENT.equals(child.getLocalName())) {
                    RangeIndexConfigField field = new RangeIndexConfigField(path, (Element) child, namespaces);
                    fields.put(field.getName(), field);
                } else if (CONDITION_ELEMENT.equals(child.getLocalName())){
                    conditions.add(new RangeIndexConfigCondition((Element) child, path));
                } else if (FILTER_ELEMENT.equals(child.getLocalName())) {
                    analyzer.addFilter((Element) child);
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
    public boolean isCaseSensitive(String fieldName) {
        for (RangeIndexConfigField field: fields.values()) {
            if(fieldName != null && fieldName.equals(field.getName())) {
                return field.isCaseSensitive();
            }
        }
        return caseSensitive;
    }

    @Override
    public boolean match(NodePath other) {
        if (isQNameIndex) {
            final QName qn1 = path.getLastComponent();
            final QName qn2 = other.getLastComponent();
            return qn1.getNameType() == qn2.getNameType() && qn2.equals(qn1);
        }
        return path.match(other);
    }

    @Override
    public boolean find(NodePath other) {
        return (getField(other) != null);
    }

    @Override
    public TextCollector getCollector(NodePath path) {
        return new ComplexTextCollector(this, path);
    }

    @Override
    public Analyzer getAnalyzer(String fieldName) {
        if (fields.containsKey(fieldName)) {
            return analyzer;
        }
        return null;
    }

    public RangeIndexConfigField getField(NodePath path) {
        for (RangeIndexConfigField field: fields.values()) {
            if (field.match(path))
                return field;
        }
        return null;
    }

    public RangeIndexConfigField getField(NodePath parentPath, NodePath path) {
        for (RangeIndexConfigField field: fields.values()) {
            if (field.match(parentPath, path))
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
        return Type.ITEM;
    }

    @Override
    public org.exist.indexing.range.conversion.TypeConverter getTypeConverter(String fieldName) {
        RangeIndexConfigField field = fields.get(fieldName);
        if (field != null) {
            return field.getTypeConverter();
        }
        return null;
    }

    public boolean matchConditions(Node node) {
        for (RangeIndexConfigCondition condition : conditions) {
            if (!condition.matches(node))
                return false;
        }

        return true;
    }

    public boolean findCondition(QName lhe, String rhe, RangeIndex.Operator operator) {
        for (RangeIndexConfigCondition condition : conditions) {
            if (condition.find(lhe, rhe, operator))
                return true;
        }

        return false;
    }

}
