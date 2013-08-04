package org.exist.indexing.range;

import org.apache.lucene.document.*;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndexConfig;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;

import java.util.Map;

public class RangeIndexConfigElement {

    protected NodePath path = null;
    private int type = Type.STRING;
    private RangeIndexConfigElement nextConfig = null;
    private boolean isQNameIndex = false;

    public RangeIndexConfigElement() {
    }

    public RangeIndexConfigElement(Element node, Map<String, String> namespaces) throws DatabaseConfigurationException {
        String match = node.getAttribute("match");
        if (match != null && match.length() > 0) {
            try {
                path = new NodePath(namespaces, match);
                if (path.length() == 0)
                    throw new DatabaseConfigurationException("Range index module: Invalid match path in collection config: " + match);
            } catch (IllegalArgumentException e) {
                throw new DatabaseConfigurationException("Range index module: invalid qname in configuration: " + e.getMessage());
            }
        } else if (node.hasAttribute("qname")) {
            QName qname = LuceneIndexConfig.parseQName(node, namespaces);
            path = new NodePath(qname);
            isQNameIndex = true;
        }
        String typeStr = node.getAttribute("type");
        if (typeStr != null && typeStr.length() > 0) {
            try {
                this.type = Type.getType(typeStr);
            } catch (XPathException e) {
                throw new DatabaseConfigurationException("Invalid type declared for range index on " + match + ": " + typeStr);
            }
        }
    }

    public Field convertToField(String fieldName, String content) {
        int fieldType = getType(fieldName);
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
                default:
                    // default: treat as text string
                    return new TextField(fieldName, content, Field.Store.NO);
            }
        } catch (NumberFormatException e) {
            // wrong type: ignore
        }
        return null;
    }

    public static Query toQuery(String field, AtomicValue content, int operator) throws XPathException {
        if (operator == Constants.EQ) {
            return new TermQuery(new Term(field, convertToBytes(content)));
        }
        final int type = content.getType();
        final boolean includeUpper = operator == Constants.LTEQ;
        final boolean includeLower = operator == Constants.GTEQ;
        switch (type) {
            case Type.INTEGER:
            case Type.LONG:
            case Type.UNSIGNED_LONG:
                if (operator == Constants.LT || operator == Constants.LTEQ) {
                    return NumericRangeQuery.newLongRange(field, null, ((NumericValue)content).getLong(), includeLower, includeUpper);
                } else {
                    return NumericRangeQuery.newLongRange(field, ((NumericValue)content).getLong(), null, includeLower, includeUpper);
                }
            case Type.INT:
            case Type.UNSIGNED_INT:
            case Type.SHORT:
            case Type.UNSIGNED_SHORT:
                if (operator == Constants.LT || operator == Constants.LTEQ) {
                    return NumericRangeQuery.newIntRange(field, null, ((NumericValue) content).getInt(), includeLower, includeUpper);
                } else {
                    return NumericRangeQuery.newIntRange(field, ((NumericValue) content).getInt(), null, includeLower, includeUpper);
                }
            case Type.DECIMAL:
            case Type.DOUBLE:
                if (operator == Constants.LT || operator == Constants.LTEQ) {
                    return NumericRangeQuery.newDoubleRange(field, null, ((NumericValue) content).getDouble(), includeLower, includeUpper);
                } else {
                    return NumericRangeQuery.newDoubleRange(field, ((NumericValue) content).getDouble(), null, includeLower, includeUpper);
                }
            case Type.FLOAT:
                if (operator == Constants.LT || operator == Constants.LTEQ) {
                    return NumericRangeQuery.newFloatRange(field, null, (float) ((NumericValue) content).getDouble(), includeLower, includeUpper);
                } else {
                    return NumericRangeQuery.newFloatRange(field, (float) ((NumericValue) content).getDouble(), null, includeLower, includeUpper);
                }
            default:
                if (operator == Constants.LT || operator == Constants.LTEQ) {
                    return new TermRangeQuery(field, null, convertToBytes(content), includeLower, includeUpper);
                } else {
                    return new TermRangeQuery(field, convertToBytes(content), null, includeLower, includeUpper);
                }
        }
    }

    public static BytesRef convertToBytes(AtomicValue content) throws XPathException {
        BytesRef bytes;
        switch(content.getType()) {
            case Type.INTEGER:
            case Type.LONG:
            case Type.UNSIGNED_LONG:
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(((IntegerValue)content).getLong(), 0, bytes);
                return bytes;
            case Type.SHORT:
            case Type.UNSIGNED_SHORT:
            case Type.INT:
            case Type.UNSIGNED_INT:
                bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
                NumericUtils.intToPrefixCoded(((IntegerValue)content).getInt(), 0, bytes);
                return bytes;
            case Type.DECIMAL:
                long dv = NumericUtils.doubleToSortableLong(((DecimalValue)content).getDouble());
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(dv, 0, bytes);
                return bytes;
            case Type.DOUBLE:
                long lv = NumericUtils.doubleToSortableLong(((DoubleValue)content).getDouble());
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(lv, 0, bytes);
                return bytes;
            case Type.FLOAT:
                int iv = NumericUtils.floatToSortableInt(((FloatValue)content).getValue());
                bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
                NumericUtils.longToPrefixCoded(iv, 0, bytes);
                return bytes;
            default:
                return new BytesRef(content.getStringValue());
        }
    }

    public static Term convertToTerm(String fieldName, AtomicValue content) throws XPathException {
        BytesRef bytes;
        switch(content.getType()) {
            case Type.INTEGER:
            case Type.LONG:
            case Type.UNSIGNED_LONG:
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(((IntegerValue)content).getLong(), 0, bytes);
                return new Term(fieldName, bytes);
            case Type.SHORT:
            case Type.UNSIGNED_SHORT:
            case Type.INT:
            case Type.UNSIGNED_INT:
                bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
                NumericUtils.intToPrefixCoded(((IntegerValue)content).getInt(), 0, bytes);
                return new Term(fieldName, bytes);
            case Type.DECIMAL:
                long dv = NumericUtils.doubleToSortableLong(((DecimalValue)content).getDouble());
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(dv, 0, bytes);
                return new Term(fieldName, bytes);
            case Type.DOUBLE:
                long lv = NumericUtils.doubleToSortableLong(((DoubleValue)content).getDouble());
                bytes = new BytesRef(NumericUtils.BUF_SIZE_LONG);
                NumericUtils.longToPrefixCoded(lv, 0, bytes);
                return new Term(fieldName, bytes);
            case Type.FLOAT:
                int iv = NumericUtils.floatToSortableInt(((FloatValue)content).getValue());
                bytes = new BytesRef(NumericUtils.BUF_SIZE_INT);
                NumericUtils.longToPrefixCoded(iv, 0, bytes);
                return new Term(fieldName, bytes);
            default:
                return new Term(fieldName, content.getStringValue());
        }
    }

    public TextCollector getCollector() {
        return new SimpleTextCollector();
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
        if (isQNameIndex)
            return other.getLastComponent().equalsSimple(path.getLastComponent());
        return path.match(other);
    }

    public boolean find(NodePath other) {
        return match(other);
    }
}
