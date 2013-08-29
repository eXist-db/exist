package org.exist.indexing.range;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.collation.CollationKeyAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;
import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndexConfig;
import org.exist.storage.NodePath;
import org.exist.util.Collations;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.XMLString;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.Map;

public class RangeIndexConfigElement {

    protected NodePath path = null;
    private int type = Type.STRING;
    private RangeIndexConfigElement nextConfig = null;
    protected boolean isQNameIndex = false;
    protected Analyzer analyzer = null;
    protected boolean includeNested = false;
    protected boolean caseSensitive = true;
    protected int wsTreatment = XMLString.SUPPRESS_NONE;

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
            path = new NodePath(NodePath.SKIP);
            path.addComponent(qname);
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
                analyzer = new CollationKeyAnalyzer(RangeIndex.LUCENE_VERSION_IN_USE, Collations.getCollationFromURI(null, collation));
            } catch (XPathException e) {
                throw new DatabaseConfigurationException(e.getMessage(), e);
            }
        }
        String nested = node.getAttribute("nested");
        includeNested = (nested == null || nested.equalsIgnoreCase("yes"));

        // normalize whitespace if whitespace="normalize"
        String whitespace = node.getAttribute("whitespace");
        if (whitespace != null) {
            if ("trim".equalsIgnoreCase(whitespace)) {
                wsTreatment = XMLString.SUPPRESS_BOTH;
            } else if ("normalize".equalsIgnoreCase(whitespace)) {
                wsTreatment = XMLString.NORMALIZE;
            }
        }

        String caseStr = node.getAttribute("case");
        if (caseStr != null && caseStr.length() > 0) {
            caseSensitive = caseStr.equalsIgnoreCase("yes");
        }
    }

    public Field convertToField(String fieldName, String content) throws IOException {
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
                    return new TextField(fieldName, content, Field.Store.NO);
            }
        } catch (NumberFormatException e) {
            // wrong type: ignore
        }
        return null;
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

    public TextCollector getCollector(NodePath path) {
        return new SimpleTextCollector(this, includeNested, wsTreatment, caseSensitive);
    }

    public Analyzer getAnalyzer() {
        return analyzer;
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
