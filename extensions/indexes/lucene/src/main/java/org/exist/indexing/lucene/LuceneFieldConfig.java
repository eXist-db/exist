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
package org.exist.indexing.lucene;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.util.BytesRef;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for a field definition nested inside a lucene index configuration element.
 * A field must have a name attribute. It may have an expression attribute containing an XQuery
 * expression, which is called to retrieve the content to be indexed. If no expression attribute
 * is present, the field will share content with its parent expression.
 *
 * Optionally an if attribute may contain an XQuery expression to be evaluated. If the effective
 * boolean value of the result is false, the field will not be created.
 *
 * A field may also be associated with an analyzer, could have a type and may be stored or not.
 *
 * @author Wolfgang Meier
 */
public class LuceneFieldConfig extends AbstractFieldConfig {

    private static final String ATTR_FIELD_NAME = "name";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_BINARY = "binary";
    private static final String ATTR_STORE = "store";
    private static final String ATTR_ANALYZER = "analyzer";
    private static final String ATTR_IF = "if";

    protected String fieldName;
    protected int type = Type.STRING;
    protected boolean binary = false;
    protected boolean store = true;
    protected Analyzer analyzer= null;
    protected Optional<String> condition = Optional.empty();
    protected CompiledXQuery compiledCondition = null;

    LuceneFieldConfig(LuceneConfig config, Element configElement, Map<String, String> namespaces, AnalyzerConfig analyzers) throws DatabaseConfigurationException {
        super(config, configElement, namespaces);

        fieldName = configElement.getAttribute(ATTR_FIELD_NAME);
        if (StringUtils.isEmpty(fieldName)) {
            throw new DatabaseConfigurationException("Invalid config: attribute 'name' must be given");
        }

        final String typeStr = configElement.getAttribute(ATTR_TYPE);
        if (StringUtils.isNotEmpty(typeStr)) {
            try {
                this.type = Type.getType(typeStr);
            } catch (XPathException e) {
                throw new DatabaseConfigurationException("Invalid type declared for field " + fieldName + ": " + typeStr);
            }
        }

        final String storeStr = configElement.getAttribute(ATTR_STORE);
        if (StringUtils.isNotEmpty(storeStr)) {
            this.store = "yes".equalsIgnoreCase(storeStr) || "true".equalsIgnoreCase(storeStr);
        }

        final String analyzerOpt = configElement.getAttribute(ATTR_ANALYZER);
        if (StringUtils.isNotEmpty(analyzerOpt)) {
            analyzer = analyzers.getAnalyzerById(analyzerOpt);
            if (analyzer == null) {
                throw new DatabaseConfigurationException("Analyzer for field " + fieldName + " not found");
            }
        }

        final String cond = configElement.getAttribute(ATTR_IF);
        if (StringUtils.isNotEmpty(cond)) {
            this.condition = Optional.of(cond);
        }

        final String binaryStr = configElement.getAttribute(ATTR_BINARY);
        if (StringUtils.isNotEmpty(binaryStr)) {
            this.binary = StringUtils.equalsAnyIgnoreCase(binaryStr, "true", "yes");
        }
    }

    @Nonnull
    public String getName() {
        return fieldName;
    }

    @Nullable
    @Override
    public Analyzer getAnalyzer() {
        return analyzer;
    }

    @Override
    protected void build(DBBroker broker, DocumentImpl document, NodeId nodeId, Document luceneDoc, CharSequence text) {
        try {
            if (checkCondition(broker, document, nodeId)) {
                doBuild(broker, document, nodeId, luceneDoc, text);
            }
        } catch (XPathException e) {
            LOG.warn("XPath error while evaluating expression for field named '{}': {}: {}", fieldName, expression, e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            LOG.warn("Permission denied while evaluating expression for field named '{}': {}", fieldName, expression, e);
        }
    }

    private boolean checkCondition(DBBroker broker, DocumentImpl document, NodeId nodeId) throws PermissionDeniedException, XPathException {
        if (!condition.isPresent()) {
            return true;
        }

        if (compiledCondition == null && isValid) {
            compiledCondition = compile(broker, condition.get());
        }
        if (!isValid) {
            return false;
        }

        final XQuery xquery = broker.getBrokerPool().getXQueryService();
        final NodeProxy currentNode = new NodeProxy(null, document, nodeId);
        try {
            Sequence result = xquery.execute(broker, compiledCondition, currentNode);
            return result != null && result.effectiveBooleanValue();
        } catch (PermissionDeniedException | XPathException e) {
            isValid = false;
            throw e;
        } finally {
            compiledCondition.reset();
            compiledCondition.getContext().reset();
        }
    }

    @Override
    protected void processResult(Sequence result, Document luceneDoc) throws XPathException {
        for (SequenceIterator i = result.unorderedIterator(); i.hasNext(); ) {
            final String text = i.nextItem().getStringValue();
            final Field field = binary ? convertToDocValue(text) : convertToField(text);
            if (field != null) {
                luceneDoc.add(field);
            }
        }
    }

    @Override
    protected void processText(CharSequence text, Document luceneDoc) {
        final Field field;
        if (binary) {
            field = convertToDocValue(text.toString());
        } else {
            field = convertToField(text.toString());
        }
        if (field != null) {
            luceneDoc.add(field);
        }
    }

    private Field convertToField(String content) {
        try {
            switch (type) {
                case Type.INTEGER:
                case Type.LONG:
                case Type.UNSIGNED_LONG:
                    long lvalue = Long.parseLong(content);
                    return new LongField(fieldName, lvalue, LongField.TYPE_STORED);
                case Type.INT:
                case Type.UNSIGNED_INT:
                case Type.SHORT:
                case Type.UNSIGNED_SHORT:
                    int ivalue = Integer.parseInt(content);
                    return new IntField(fieldName, ivalue, IntField.TYPE_STORED);
                case Type.DECIMAL:
                case Type.DOUBLE:
                    double dvalue = Double.parseDouble(content);
                    return new DoubleField(fieldName, dvalue, DoubleField.TYPE_STORED);
                case Type.FLOAT:
                    float fvalue = Float.parseFloat(content);
                    return new FloatField(fieldName, fvalue, FloatField.TYPE_STORED);
                case Type.DATE:
                    DateValue dv = new DateValue(content);
                    long dl = dateToLong(dv);
                    return new LongField(fieldName, dl, LongField.TYPE_STORED);
                case Type.TIME:
                    TimeValue tv = new TimeValue(content);
                    long tl = timeToLong(tv);
                    return new LongField(fieldName, tl, LongField.TYPE_STORED);
                case Type.DATE_TIME:
                    DateTimeValue dtv = new DateTimeValue(content);
                    String dateStr = dateTimeToString(dtv);
                    return new TextField(fieldName, dateStr, Field.Store.YES);
                default:
                    return new TextField(fieldName, content, store ? Field.Store.YES : Field.Store.NO);
            }
        } catch (NumberFormatException | XPathException e) {
            // wrong type: ignore
            LOG.trace("Cannot convert field {} to type {}. Content was: {}", fieldName, Type.getTypeName(type), content);
        }
        return null;
    }

    private Field convertToDocValue(final String content) {
        try {
            return switch (type) {
                case Type.TIME -> {
                    final TimeValue timeValue = new TimeValue(content);
                    yield new BinaryDocValuesField(fieldName, new BytesRef(timeValue.toJavaObject(byte[].class)));
                }
                case Type.DATE_TIME -> {
                    final DateTimeValue dateTimeValue = new DateTimeValue(content);
                    yield new BinaryDocValuesField(fieldName, new BytesRef(dateTimeValue.toJavaObject(byte[].class)));
                }
                case Type.DATE -> {
                    final DateValue dateValue = new DateValue(content);
                    yield new BinaryDocValuesField(fieldName, new BytesRef(dateValue.toJavaObject(byte[].class)));
                }
                case Type.INTEGER, Type.LONG, Type.UNSIGNED_LONG, Type.INT, Type.UNSIGNED_INT, Type.SHORT, Type.UNSIGNED_SHORT -> {
                    final IntegerValue iv = new IntegerValue(content, Type.INTEGER);
                    yield new BinaryDocValuesField(fieldName, new BytesRef(iv.serialize()));
                }
                case Type.DOUBLE -> {
                    final DoubleValue dbv = new DoubleValue(content);
                    yield new BinaryDocValuesField(fieldName, new BytesRef(dbv.toJavaObject(byte[].class)));
                }
                case Type.FLOAT -> {
                    final FloatValue fv = new FloatValue(content);
                    yield new BinaryDocValuesField(fieldName, new BytesRef(fv.toJavaObject(byte[].class)));
                }
                case Type.DECIMAL -> {
                    final DecimalValue dv = new DecimalValue(content);
                    yield new BinaryDocValuesField(fieldName, new BytesRef(dv.toJavaObject(byte[].class)));
                }

                // everything else treated as string
                default -> new BinaryDocValuesField(fieldName, new BytesRef(content));
            };
        } catch (final NumberFormatException | XPathException e) {
            // wrong type: ignore
            LOG.error("Cannot convert field {} to type {}. Content was: {}", fieldName, Type.getTypeName(type), content);
            return null;
        }
    }

    private static long dateToLong(DateValue date) {
        final XMLGregorianCalendar utccal = date.calendar.normalize();
        return ((long)utccal.getYear() << 16) + ((long)utccal.getMonth() << 8) + ((long)utccal.getDay());
    }

    private static long timeToLong(TimeValue time) {
        return time.getTimeInMillis();
    }

    private static String dateTimeToString(DateTimeValue dtv) {
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

    private static void formatNumber(int number, int digits, StringBuilder sb) {
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
}
