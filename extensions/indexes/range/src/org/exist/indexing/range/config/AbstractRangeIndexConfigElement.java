package org.exist.indexing.range.config;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.collation.CollationKeyAnalyzer;
import org.apache.lucene.document.*;
import org.exist.indexing.range.RangeIndex;
import org.exist.indexing.range.conversion.TypeConversion;
import org.exist.indexing.range.conversion.TypeConverter;
import org.exist.storage.NodePath;
import org.exist.util.Collations;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.TimeValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.Map;

/**
 * Created by aretter on 04/05/2015.
 */
public abstract class AbstractRangeIndexConfigElement implements RangeIndexConfigElement {

    private TypeConverter typeConverter = null;
    protected boolean caseSensitive = true;
    protected Analyzer analyzer = null;
    private RangeIndexConfigElement nextConfig = null;

    public AbstractRangeIndexConfigElement(final Element node, final Map<String, String> namespaces) throws DatabaseConfigurationException {
        String collation = node.getAttribute("collation");
        if (collation != null && collation.length() > 0) {
            try {
                analyzer = new CollationKeyAnalyzer(RangeIndex.LUCENE_VERSION_IN_USE, Collations.getCollationFromURI(null, collation));
            } catch (XPathException e) {
                throw new DatabaseConfigurationException(e.getMessage(), e);
            }
        }
        final String custom = node.getAttribute("converter");
        if (custom != null && custom.length() > 0) {
            try {
                final Class customClass = Class.forName(custom);
                this.typeConverter = (TypeConverter) customClass.newInstance();
            } catch (final ClassNotFoundException e) {
                RangeIndexConfig.LOG.warn("Class for custom-type not found: " + custom);
            } catch (final InstantiationException | IllegalAccessException e) {
                RangeIndexConfig.LOG.warn("Failed to initialize custom-type: " + custom, e);
            }
        }
    }

    @Override
    public boolean isCaseSensitive(final String fieldName) {
        return caseSensitive;
    }

    @Override
    public Analyzer getAnalyzer() {
        return analyzer;
    }

    @Override
    public Analyzer getAnalyzer(final String field) {
        return analyzer;
    }

    @Override
    public void add(final RangeIndexConfigElement config) {
        if (nextConfig == null) {
            nextConfig = config;
        } else {
            nextConfig.add(config);
        }
    }

    @Override
    public RangeIndexConfigElement getNext() {
        return nextConfig;
    }

    @Override
    public boolean find(final NodePath other) {
        return match(other);
    }

    public TypeConverter getTypeConverter(final String fieldName) {
        return typeConverter;
    }

    @Override
    public Field convertToField(final String fieldName, final String content) throws IOException {
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
                    long dl = TypeConversion.dateToLong(dv);
                    return new LongField(fieldName, dl, LongField.TYPE_NOT_STORED);
                case Type.TIME:
                    TimeValue tv = new TimeValue(content);
                    long tl = TypeConversion.timeToLong(tv);
                    return new LongField(fieldName, tl, LongField.TYPE_NOT_STORED);
                case Type.DATE_TIME:
                    DateTimeValue dtv = new DateTimeValue(content);
                    String dateStr = TypeConversion.dateTimeToString(dtv);
                    return new TextField(fieldName, dateStr, Field.Store.NO);
                default:
                    return new TextField(fieldName, content, Field.Store.NO);
            }
        } catch (NumberFormatException e) {
            // wrong type: ignore
        } catch (XPathException e) {
            // wrong type: ignore
        }
        return null;
    }
}
