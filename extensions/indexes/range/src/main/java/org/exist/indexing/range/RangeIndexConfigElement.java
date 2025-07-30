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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.NumericUtils;
import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndexConfig;
import org.exist.indexing.range.conversion.TypeConverter;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLString;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static org.exist.indexing.lucene.LuceneIndexConfig.MATCH_ATTR;
import static org.exist.indexing.lucene.LuceneIndexConfig.QNAME_ATTR;
import static org.exist.indexing.lucene.LuceneIndexConfig.TYPE_ATTR;

public class RangeIndexConfigElement {

    protected final static String FILTER_ELEMENT = "filter";

    protected NodePath path = null;
    private int type = Type.STRING;
    private RangeIndexConfigElement nextConfig = null;
    protected boolean isQNameIndex = false;
    protected RangeIndexAnalyzer analyzer = new RangeIndexAnalyzer();
    protected boolean includeNested = false;
    protected boolean caseSensitive = true;
    protected boolean usesCollation = false;
    protected int wsTreatment = XMLString.SUPPRESS_NONE;
    private TypeConverter typeConverter = null;

    public RangeIndexConfigElement(Element node, Map<String, String> namespaces) throws DatabaseConfigurationException {
        String match = node.getAttribute(MATCH_ATTR);
        if (!match.isEmpty()) {
            try {
                path = new NodePath(namespaces, match);
                if (path.length() == 0)
                    throw new DatabaseConfigurationException("Range index module: Invalid match path in collection config: " + match);
            } catch (IllegalArgumentException e) {
                throw new DatabaseConfigurationException("Range index module: invalid qname in configuration: " + e.getMessage());
            }
        } else if (node.hasAttribute(QNAME_ATTR)) {
            QName qname = LuceneIndexConfig.parseQName(node, namespaces);
            path = new NodePath(NodePath.SKIP);
            path.addComponent(qname);
            isQNameIndex = true;
        }
        String typeStr = node.getAttribute(TYPE_ATTR);
        if (!typeStr.isEmpty()) {
            try {
                this.type = Type.getType(typeStr);
            } catch (XPathException e) {
                throw new DatabaseConfigurationException("Invalid type declared for range index on " + match + ": " + typeStr);
            }
        }

        parseChildren(node);

        String collation = node.getAttribute("collation");
        if (!collation.isEmpty()) {
            analyzer.addCollation(collation);
            usesCollation = true;
        }
        String nested = node.getAttribute("nested");
        includeNested = (nested.isEmpty() || "yes".equalsIgnoreCase(nested));

        // normalize whitespace if whitespace="normalize"
        String whitespace = node.getAttribute("whitespace");
        if (!whitespace.isEmpty()) {
            if ("trim".equalsIgnoreCase(whitespace)) {
                wsTreatment = XMLString.SUPPRESS_BOTH;
            } else if ("normalize".equalsIgnoreCase(whitespace)) {
                wsTreatment = XMLString.NORMALIZE;
            }
        }

        String caseStr = node.getAttribute("case");
        if (!caseStr.isEmpty()) {
            caseSensitive = "yes".equalsIgnoreCase(caseStr);
        }
        final String custom = node.getAttribute("converter");
        if (!custom.isEmpty()) {
            try {
                final Class<?> customClass = Class.forName(custom);
                typeConverter = (TypeConverter) customClass.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                RangeIndex.LOG.warn("Class for custom-type not found: {}", custom);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                RangeIndex.LOG.warn("Failed to initialize custom-type: {}", custom, e);
            }
        }
    }

    private void parseChildren(final Node root) throws DatabaseConfigurationException {
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (FILTER_ELEMENT.equals(child.getLocalName())) {
                    analyzer.addFilter((Element) child);
                }
            }
            child = child.getNextSibling();
        }
    }

    public Field convertToField(String fieldName, String content) throws IOException {
        // check if a converter is defined for this index to handle on-the-fly conversions
        final TypeConverter custom = getTypeConverter(fieldName);
        if (custom != null) {
            return custom.toField(fieldName, content);
        }
        // no converter: handle default types
        final int fieldType = getType(fieldName);
        try {
            switch (fieldType) {
                case Type.INTEGER:
                case Type.LONG:
                case Type.UNSIGNED_LONG:
                    long lvalue = Long.parseLong(content);
                    return new LongField(fieldName, lvalue, LongField.TYPE_NOT_STORED);
                case Type.INT:
                case Type.UNSIGNED_INT:
                case Type.SHORT:
                case Type.UNSIGNED_SHORT:
                    int ivalue = Integer.parseInt(content);
                    return new IntField(fieldName, ivalue, IntField.TYPE_NOT_STORED);
                case Type.DECIMAL:
                case Type.DOUBLE:
                    double dvalue = Double.parseDouble(content);
                    return new DoubleField(fieldName, dvalue, DoubleField.TYPE_NOT_STORED);
                case Type.FLOAT:
                    float fvalue = Float.parseFloat(content);
                    return new FloatField(fieldName, fvalue, FloatField.TYPE_NOT_STORED);
                case Type.DATE:
                    DateValue dv = new DateValue(content);
                    long dl = dateToLong(dv);
                    return new LongField(fieldName, dl, LongField.TYPE_NOT_STORED);
                case Type.TIME:
                    TimeValue tv = new TimeValue(content);
                    long tl = timeToLong(tv);
                    return new LongField(fieldName, tl, LongField.TYPE_NOT_STORED);
                case Type.DATE_TIME:
                    DateTimeValue dtv = new DateTimeValue(content);
                    String dateStr = dateTimeToString(dtv);
                    return new TextField(fieldName, dateStr, Field.Store.NO);
                default:
                    return new TextField(fieldName, content, Field.Store.NO);
            }
        } catch (NumberFormatException | XPathException e) {
            // wrong type: ignore
        }
        return null;
    }

    public static BytesRef convertToBytes(final AtomicValue content) throws XPathException {
        final BytesRefBuilder bytes = new BytesRefBuilder();
        switch(content.getType()) {
            case Type.INTEGER:
            case Type.LONG:
            case Type.UNSIGNED_LONG:
                NumericUtils.longToPrefixCoded(((IntegerValue)content).getLong(), 0, bytes);
                break;

            case Type.SHORT:
            case Type.UNSIGNED_SHORT:
            case Type.INT:
            case Type.UNSIGNED_INT:
                NumericUtils.intToPrefixCoded(((IntegerValue)content).getInt(), 0, bytes);
                break;

            case Type.DECIMAL:
                final long dv = NumericUtils.doubleToSortableLong(((DecimalValue)content).getDouble());
                NumericUtils.longToPrefixCoded(dv, 0, bytes);
                break;

            case Type.DOUBLE:
                final long lv = NumericUtils.doubleToSortableLong(((DoubleValue)content).getDouble());
                NumericUtils.longToPrefixCoded(lv, 0, bytes);
                break;

            case Type.FLOAT:
                final int iv = NumericUtils.floatToSortableInt(((FloatValue)content).getValue());
                NumericUtils.longToPrefixCoded(iv, 0, bytes);
                break;

            case Type.DATE:
                final long dl = dateToLong((DateValue)content);
                NumericUtils.longToPrefixCoded(dl, 0, bytes);
                break;

            case Type.TIME:
                final long tl = timeToLong((TimeValue) content);
                NumericUtils.longToPrefixCoded(tl, 0, bytes);
                break;

            case Type.DATE_TIME:
                final String dt = dateTimeToString((DateTimeValue) content);
                bytes.copyChars(dt);
                break;

            default:
                bytes.copyChars(content.getStringValue());
        }
        return bytes.toBytesRef();
    }

    public static long dateToLong(DateValue date) {
        final XMLGregorianCalendar utccal = date.calendar.normalize();
        return ((long)utccal.getYear() << 16) + ((long)utccal.getMonth() << 8) + ((long)utccal.getDay());
    }

    public static long timeToLong(TimeValue time) {
        return time.getTimeInMillis();
    }

    public static String dateTimeToString(DateTimeValue dtv) {
        final XMLGregorianCalendar utccal = dtv.calendar.normalize();
        final StringBuilder sb = new StringBuilder();
        formatNumber(utccal.getMillisecond(), 3, sb);
        formatNumber(utccal.getSecond(), 2, sb);
        formatNumber(utccal.getMinute(), 2, sb);
        formatNumber(utccal.getHour(), 2, sb);
        formatNumber(utccal.getDay(), 2, sb);
        formatNumber(utccal.getMonth(), 2, sb);
        formatNumber(utccal.getYear(), 4, sb);
        return sb.toString();
    }

    public static void formatNumber(int number, int digits, StringBuilder sb) {
        int count = 0;
        long n = number;
        while (n > 0) {
            final int digit = '0' + (int)n % 10;
            sb.insert(0, (char)digit);
            count++;
            if (count == digits) {
                break;
            }
            n = n / 10;
        }
        if (count < digits) {
            for (int i = count; i < digits; i++) {
                sb.insert(0, '0');
            }
        }
    }

    public TextCollector getCollector(NodePath path) {
        return new SimpleTextCollector(this, includeNested, wsTreatment, caseSensitive);
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public Analyzer getAnalyzer(String field) {
        return analyzer;
    }

    public boolean isCaseSensitive(String fieldName) {
        return caseSensitive;
    }

    public boolean usesCollation() {
        return usesCollation;
    }

    public boolean isComplex() {
        return false;
    }

    public int getType(String fieldName) {
        // no fields: return type
        return type;
    }

    public int getType() {
        return type;
    }

    public TypeConverter getTypeConverter(String fieldName) {
        return typeConverter;
    }

    public NodePath getNodePath() {
        return path;
    }

    public void add(RangeIndexConfigElement config) {
        if (nextConfig == null)
            nextConfig = config;
        else
            nextConfig.add(config);
    }

    public RangeIndexConfigElement getNext() {
        return nextConfig;
    }

    public boolean match(NodePath other) {
        if (isQNameIndex) {
            final QName qn1 = path.getLastComponent();
            final QName qn2 = other.getLastComponent();
            return qn1.getNameType() == qn2.getNameType() && qn2.equals(qn1);
        }
        return other.match(path);
    }

    public boolean find(NodePath other) {
        return match(other);
    }
}
