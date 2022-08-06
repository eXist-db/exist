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
package org.exist.xquery.modules.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.InMemoryNodeSet;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.persistent.Match;
import org.exist.dom.persistent.NodeProxy;
import org.exist.indexing.lucene.*;
import org.exist.storage.NodePath;
import org.exist.util.ByteConversion;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Map;

public class Field extends BasicFunction {

    public static final String FN_FIELD = "field";
    public static final String FN_BINARY_FIELD = "binary-field";
    public static final String FN_HIGHLIGHT_FIELD_MATCHES = "highlight-field-matches";

    public static final FunctionParameterSequenceType CONTEXT_NODE_PARAMETER =
            new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE,
                    "the context node to check for attached fields");

    public static final FunctionParameterSequenceType FIELD_NAME_PARAMETER =
            new FunctionParameterSequenceType("field", Type.STRING, Cardinality.EXACTLY_ONE,
                    "name of the field");
    public static final FunctionParameterSequenceType TYPE_CAST_PARAMETER =
            new FunctionParameterSequenceType("type", Type.STRING, Cardinality.EXACTLY_ONE,
                "intended target type to cast the field value to. Casting may fail with a dynamic error.");

    protected static final FunctionSignature[] signatures = {
            new FunctionSignature(
                new QName(FN_FIELD, LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                "Returns the value of a field attached to a particular node obtained via a full text search.",
                new SequenceType[]{
                        CONTEXT_NODE_PARAMETER,
                        FIELD_NAME_PARAMETER
                },
                new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE,
                        "One or more string values corresponding to the values of the field attached")
            ),
            new FunctionSignature(
                    new QName(FN_FIELD, LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Returns the value of a field attached to a particular node obtained via a full text search." +
                            "Accepts an additional parameter to name the target type into which the field " +
                            "value should be cast. This is mainly relevant for fields having a different type than xs:string. " +
                            "As lucene does not record type information, numbers or dates would be returned as strings by default.",
                    new SequenceType[]{
                            CONTEXT_NODE_PARAMETER,
                            FIELD_NAME_PARAMETER,
                            TYPE_CAST_PARAMETER
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                            "Sequence corresponding to the values of the field attached, cast to the desired target type")
            ),
            new FunctionSignature(
                    new QName(FN_BINARY_FIELD, LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Returns the value of a binary field attached to a particular node obtained via a full text search." +
                            "Accepts an additional parameter to name the target type into which the field " +
                            "value should be cast. This is mainly relevant for fields having a different type than xs:string. " +
                            "As lucene does not record type information, numbers or dates would be returned as strings by default.",
                    new SequenceType[]{
                            CONTEXT_NODE_PARAMETER,
                            FIELD_NAME_PARAMETER,
                            TYPE_CAST_PARAMETER
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                            "Sequence corresponding to the values of the field attached, cast to the desired target type")
            ),
            new FunctionSignature(
                    new QName(FN_HIGHLIGHT_FIELD_MATCHES, LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                    "Highlights matches for the last executed lucene query within the value of a field " +
                    "attached to a particular node obtained via a full text search. Only fields listed in the 'fields' option of ft:query will be " +
                    "available to highlighting.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE,
                                    "the context node to check for attached fields which should be highlighted"),
                            new FunctionParameterSequenceType("field", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "name of the field to highlight")
                    },
                    new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.ZERO_OR_ONE,
                            "An exist:field containing the content of the requested field with all query " +
                                    "matches enclosed in an exist:match")
            )
    };

    public Field(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        NodeValue nodeValue = (NodeValue) args[0].itemAt(0);
        if (nodeValue.getImplementationType() != NodeValue.PERSISTENT_NODE) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String fieldName = args[1].itemAt(0).getStringValue();

        int type = Type.STRING;
        if (getArgumentCount() == 3) {
            final String typeStr = args[2].itemAt(0).getStringValue();
            type = Type.getType(typeStr);
        }

        final NodeProxy proxy = (NodeProxy) nodeValue;
        final LuceneMatch match = getMatch(proxy);
        if (match == null) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final String called = getSignature().getName().getLocalPart();

        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        try {
            switch (called) {
                case FN_FIELD:
                    return getFieldValues(fieldName, type, match, index);
                case FN_HIGHLIGHT_FIELD_MATCHES:
                    final Sequence result = getFieldValues(fieldName, type, match, index);
                    return highlightMatches(fieldName, proxy, match, result);
                case FN_BINARY_FIELD:
                    return getBinaryFieldValue(fieldName, type, match, index);
                default:
                    throw new XPathException(this, ErrorCodes.FOER0000, "Unknown function: " + getName());
            }
        } catch (IOException e) {
            throw new XPathException(this, LuceneModule.EXXQDYFT0002, "Error retrieving field: " + e.getMessage());
        }
    }

    private Sequence getBinaryFieldValue(final String fieldName, final int type, final LuceneMatch match, final LuceneIndexWorker index) throws IOException {
        final BytesRef fieldValue = index.getBinaryField(match.getLuceneDocId(), fieldName);
        if (fieldValue == null) {
            return Sequence.EMPTY_SEQUENCE;
        }
        return bytesToAtomic(fieldValue, type);
    }

    private Sequence getFieldValues(final String fieldName, final int type, final LuceneMatch match, final LuceneIndexWorker index) throws IOException, XPathException {
        Sequence result;
        final IndexableField[] fields = index.getField(match.getLuceneDocId(), fieldName);
        result = new ValueSequence(fields.length);
        for (final IndexableField field : fields) {
            if (field.numericValue() != null) {
                result.add(numberToAtomic(type, field.numericValue()));
            } else {
                result.add(stringToAtomic(type, field.stringValue()));
            }
        }
        return result;
    }

    /**
     * Highlight matches in field content using the analyzer defined for the field.
     *
     * @param fieldName the name of the field
     * @param proxy node on which the field is defined
     * @param match the lucene match attached to the node
     * @param text the content of the field
     * @return a sequence of exist:field elements containing the field content with matches enclosed in exist:match
     * @throws XPathException in case of error
     * @throws IOException in case of a lucene error
     */
    private Sequence highlightMatches(final String fieldName, final NodeProxy proxy, final LuceneMatch match, final Sequence text) throws XPathException, IOException {
        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        final Map<Object, Query> terms = index.getTerms(match.getQuery());
        final NodePath path = LuceneMatchListener.getPath(proxy);
        final LuceneConfig config = index.getLuceneConfig(context.getBroker(), proxy.getDocumentSet());
        LuceneIndexConfig idxConf = config.getConfig(path).next();
        if (idxConf == null) {
            // no lucene index: no fields to highlight
            return Sequence.EMPTY_SEQUENCE;
        }

        final Analyzer analyzer = idxConf.getAnalyzer();

        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();

            final InMemoryNodeSet result =  new InMemoryNodeSet(text.getItemCount());
            for (final SequenceIterator si = text.iterate(); si.hasNext(); ) {
                final int nodeNr = builder.startElement(Namespaces.EXIST_NS, "field", "exist:field", null);
                final String content = si.nextItem().getStringValue();
                int currentPos = 0;
                try (final Reader reader = new StringReader(content);
                     final TokenStream tokenStream = analyzer.tokenStream(fieldName, reader)) {
                    tokenStream.reset();
                    final MarkableTokenFilter stream = new MarkableTokenFilter(tokenStream);
                    while (stream.incrementToken()) {
                        String token = stream.getAttribute(CharTermAttribute.class).toString();
                        final Query query = terms.get(token);
                        if (query != null) {
                            if (match.getQuery() instanceof PhraseQuery) {
                                final Term phraseTerms[] = ((PhraseQuery) match.getQuery()).getTerms();
                                if (token.equals(phraseTerms[0].text())) {
                                    // Scan the following text and collect tokens to see
                                    // if they are part of the phrase.
                                    stream.mark();
                                    int t = 1;
                                    OffsetAttribute offset = stream.getAttribute(OffsetAttribute.class);
                                    final int startOffset = offset.startOffset();
                                    int endOffset = offset.endOffset();
                                    while (stream.incrementToken() && t < phraseTerms.length) {
                                        token = stream.getAttribute(CharTermAttribute.class).toString();
                                        if (token.equals(phraseTerms[t].text())) {
                                            offset = stream.getAttribute(OffsetAttribute.class);
                                            endOffset = offset.endOffset();
                                            t++;
                                            if (t == phraseTerms.length) {
                                                break;
                                            }
                                        } else {
                                            break;
                                        }
                                    }
                                    if (t == phraseTerms.length) {
                                        if (currentPos < startOffset) {
                                            builder.characters(content.substring(currentPos, startOffset));
                                        }
                                        builder.startElement(Namespaces.EXIST_NS, "match", "exist:match", null);
                                        builder.characters(content.substring(startOffset, endOffset));
                                        builder.endElement();
                                        currentPos = endOffset;
                                    }
                                } // End of phrase handling
                            } else {
                                final OffsetAttribute offset = stream.getAttribute(OffsetAttribute.class);
                                if (currentPos < offset.startOffset()) {
                                    builder.characters(content.substring(currentPos, offset.startOffset()));
                                }
                                builder.startElement(Namespaces.EXIST_NS, "match", "exist:match", null);
                                builder.characters(content.substring(offset.startOffset(), offset.endOffset()));
                                builder.endElement();
                                currentPos = offset.endOffset();
                            }
                        }
                    }
                }
                if (currentPos < content.length() - 1)  {
                    builder.characters(content.substring(currentPos));
                }
                builder.endElement();
                result.add(builder.getDocument().getNode(nodeNr));
            }
            return result;
        } finally {
            context.popDocumentContext();
        }
    }

    /**
     * Get the lucene match object attached to the given node
     *
     * @param proxy node to check for matches
     * @return the LuceneMatch object attached to the node or null
     */
    static @Nullable LuceneMatch getMatch(NodeProxy proxy) {
        Match match = proxy.getMatches();
        while (match != null) {
            if (match.getIndexId().equals(LuceneIndex.ID)) {
                return (LuceneMatch) match;
            }
            match = match.getNextMatch();
        }
        return null;
    }

    static AtomicValue bytesToAtomic(BytesRef field, int type) {
        final byte[] data = field.bytes;
        final int timezone;
        switch(type) {
            case Type.TIME:
                timezone = ByteConversion.byteToShortH(data, 5);
                return new TimeValue(
                        data[0],
                        data[1],
                        data[2],
                        ByteConversion.byteToShortH(data, 3),
                        timezone == Short.MAX_VALUE ? DatatypeConstants.FIELD_UNDEFINED : timezone
                );
            case Type.DATE_TIME:
                timezone = ByteConversion.byteToShortH(data, 11);
                return new DateTimeValue(
                    ByteConversion.byteToIntH(data, 0),
                    data[4],
                    data[5],
                    data[6],
                    data[7],
                    data[8],
                    ByteConversion.byteToShortH(data, 9),
                    timezone == Short.MAX_VALUE ? DatatypeConstants.FIELD_UNDEFINED : timezone
                );
            case Type.DATE:
                timezone = ByteConversion.byteToShortH(data, 6);
                return new DateValue(
                    ByteConversion.byteToIntH(data, 0),
                    data[4],
                    data[5],
                    timezone == Short.MAX_VALUE ? DatatypeConstants.FIELD_UNDEFINED : timezone
                );
            case Type.DOUBLE:
                final long bits = ByteConversion.byteToLong(data, 0) ^ 0x8000000000000000L;
                final double d = Double.longBitsToDouble(bits);
                return new DoubleValue(d);
            case Type.INTEGER:
            case Type.INT:
            case Type.UNSIGNED_INT:
            case Type.LONG:
            case Type.UNSIGNED_LONG:
                return new IntegerValue(ByteConversion.byteToLong(data, 0) ^ 0x8000000000000000L);
            default:
                return new StringValue(new String(field.bytes));
        }
    }

    static AtomicValue stringToAtomic(int type, String value) throws XPathException {
        switch(type) {
            case Type.TIME:
                return new TimeValue(value);
            case Type.DATE_TIME:
                return new DateTimeValue(value);
            case Type.DATE:
                return new DateValue(value);
            case Type.FLOAT:
                return new FloatValue(value);
            case Type.DOUBLE:
                return new DoubleValue(value);
            case Type.DECIMAL:
                return new DecimalValue(value);
            case Type.INTEGER:
            case Type.INT:
            case Type.UNSIGNED_INT:
            case Type.LONG:
            case Type.UNSIGNED_LONG:
                return new IntegerValue(value);
            default:
                return new StringValue(value);
        }
    }

    static AtomicValue numberToAtomic(int type, Number value) throws XPathException {
        switch(type) {
            case Type.TIME:
                final Date time = new Date(value.longValue());
                final GregorianCalendar gregorianCalendar = new GregorianCalendar();
                gregorianCalendar.setTime(time);
                final XMLGregorianCalendar calendar = TimeUtils.getInstance().newXMLGregorianCalendar(gregorianCalendar);
                return new TimeValue(calendar);
            case Type.DATE_TIME:
                throw new XPathException(LuceneModule.EXXQDYFT0004, "Cannot convert numeric field to xs:dateTime");
            case Type.DATE:
                final long dl = value.longValue();
                final int year = (int)(dl >> 16) & 0xFFFF;
                final int month = (int)(dl >> 8) & 0xFF;
                final int day = (int)(dl & 0xFF);
                final DateValue date = new DateValue();
                date.calendar.setYear(year);
                date.calendar.setMonth(month);
                date.calendar.setDay(day);
                return date;
            case Type.FLOAT:
                return new FloatValue(value.floatValue());
            case Type.DOUBLE:
                return new DoubleValue(value.floatValue());
            case Type.DECIMAL:
                return new DecimalValue(value.doubleValue());
            case Type.INTEGER:
            case Type.INT:
            case Type.UNSIGNED_INT:
            case Type.LONG:
            case Type.UNSIGNED_LONG:
                return new IntegerValue(value.longValue());
            default:
                return new StringValue(value.toString());
        }
    }
}
