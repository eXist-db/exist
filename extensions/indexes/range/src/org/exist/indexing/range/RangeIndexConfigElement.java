package org.exist.indexing.range;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.collation.CollationKeyAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.Version;
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndexConfig;
import org.exist.storage.NodePath;
import org.exist.util.Collations;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

public class RangeIndexConfigElement {

    public static final org.apache.lucene.document.FieldType TYPE_CONTENT = new org.apache.lucene.document.FieldType();
    static {
        TYPE_CONTENT.setIndexed(true);
        TYPE_CONTENT.setStored(false);
        TYPE_CONTENT.setOmitNorms(true);
        TYPE_CONTENT.setStoreTermVectors(false);
        TYPE_CONTENT.setTokenized(true);
    }

    protected NodePath path = null;
    private int type = Type.STRING;
    private RangeIndexConfigElement nextConfig = null;
    private boolean isQNameIndex = false;
    protected Analyzer analyzer = null;

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
        String collation = node.getAttribute("collation");
        if (collation != null && collation.length() > 0) {
            try {
                analyzer = new CollationKeyAnalyzer(Version.LUCENE_43, Collations.getCollationFromURI(null, collation));
            } catch (XPathException e) {
                throw new DatabaseConfigurationException(e.getMessage(), e);
            }
        }
    }

    public Field convertToField(String fieldName, String content) throws IOException {
        int fieldType = getType(fieldName);
        Analyzer analyzer = getAnalyzer(fieldName);
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
                    if (analyzer != null) {
                        TokenStream stream = analyzer.tokenStream(fieldName, new StringReader(content));
                        return new TextField(fieldName, stream);
                    }
                    return new TextField(fieldName, content, Field.Store.NO);
            }
        } catch (NumberFormatException e) {
            // wrong type: ignore
        }
        return null;
    }

    public static Query toQuery(String field, AtomicValue content, RangeIndex.Operator operator,
                                DocumentSet docs, RangeIndexWorker worker) throws XPathException {
        final int type = content.getType();
        BytesRef bytes;
        if (Type.subTypeOf(type, Type.STRING)) {
            BytesRef key = worker.analyzeContent(field, content, docs);
            switch (operator) {
                case EQ:
                    return new TermQuery(new Term(field, key));
                case STARTS_WITH:
                    return new PrefixQuery(new Term(field, key));
                case ENDS_WITH:
                    bytes = new BytesRef("*");
                    bytes.append(key);
                    return new WildcardQuery(new Term(field, bytes));
                case CONTAINS:
                    bytes = new BytesRef("*");
                    bytes.append(key);
                    bytes.append(new BytesRef("*"));
                    return new WildcardQuery(new Term(field, bytes));
            }
        }
        if (operator == RangeIndex.Operator.EQ) {
            return new TermQuery(new Term(field, convertToBytes(content)));
        }
        final boolean includeUpper = operator == RangeIndex.Operator.LE;
        final boolean includeLower = operator == RangeIndex.Operator.GE;
        switch (type) {
            case Type.INTEGER:
            case Type.LONG:
            case Type.UNSIGNED_LONG:
                if (operator == RangeIndex.Operator.LT || operator == RangeIndex.Operator.LE) {
                    return NumericRangeQuery.newLongRange(field, null, ((NumericValue)content).getLong(), includeLower, includeUpper);
                } else {
                    return NumericRangeQuery.newLongRange(field, ((NumericValue)content).getLong(), null, includeLower, includeUpper);
                }
            case Type.INT:
            case Type.UNSIGNED_INT:
            case Type.SHORT:
            case Type.UNSIGNED_SHORT:
                if (operator == RangeIndex.Operator.LT || operator == RangeIndex.Operator.LE) {
                    return NumericRangeQuery.newIntRange(field, null, ((NumericValue) content).getInt(), includeLower, includeUpper);
                } else {
                    return NumericRangeQuery.newIntRange(field, ((NumericValue) content).getInt(), null, includeLower, includeUpper);
                }
            case Type.DECIMAL:
            case Type.DOUBLE:
                if (operator == RangeIndex.Operator.LT || operator == RangeIndex.Operator.LE) {
                    return NumericRangeQuery.newDoubleRange(field, null, ((NumericValue) content).getDouble(), includeLower, includeUpper);
                } else {
                    return NumericRangeQuery.newDoubleRange(field, ((NumericValue) content).getDouble(), null, includeLower, includeUpper);
                }
            case Type.FLOAT:
                if (operator == RangeIndex.Operator.LT || operator == RangeIndex.Operator.LE) {
                    return NumericRangeQuery.newFloatRange(field, null, (float) ((NumericValue) content).getDouble(), includeLower, includeUpper);
                } else {
                    return NumericRangeQuery.newFloatRange(field, (float) ((NumericValue) content).getDouble(), null, includeLower, includeUpper);
                }
            default:
                if (operator == RangeIndex.Operator.LT || operator == RangeIndex.Operator.LE) {
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

    public Analyzer getAnalyzer(String field) {
        return analyzer;
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
        return other.match(path);
    }

    public boolean find(NodePath other) {
        return match(other);
    }
}
