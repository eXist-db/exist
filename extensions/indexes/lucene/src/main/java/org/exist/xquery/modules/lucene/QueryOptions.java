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

import io.lacuna.bifurcan.IEntry;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.MultiTermQuery;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.util.Configuration;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.NodeValue;

import javax.annotation.Nullable;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.*;

import static org.exist.xquery.modules.lucene.QueryOptions.DefaultOperator.OR;

public class QueryOptions {

    public static final String OPTION_DEFAULT_OPERATOR = "default-operator";
    public static final String OPTION_PHRASE_SLOP = "phrase-slop";
    public static final String OPTION_LEADING_WILDCARD = "leading-wildcard";
    public static final String OPTION_FILTER_REWRITE = "filter-rewrite";
    public static final String DEFAULT_OPERATOR_OR = "or";
    public static final String OPTION_LOWERCASE_EXPANDED_TERMS = "lowercase-expanded-terms";
    public static final String OPTION_FACETS = "facets";
    public static final String OPTION_QUERY_ANALYZER_ID = "query-analyzer-id";
    public static final String OPTION_FILTER_QUERY = "filter-query";
    public static final String OPTION_FILTER = "filter";

    protected enum DefaultOperator {
        OR,
        AND
    }

    protected String queryAnalyzerId = null;
    protected DefaultOperator defaultOperator = DefaultOperator.AND;
    protected boolean allowLeadingWildcard = false;
    protected Optional<Integer> phraseSlop = Optional.empty();

    protected boolean filterRewrite = false;
    protected boolean lowercaseExpandedTerms = false;
    protected Optional<Map<String, FacetQuery>> facets = Optional.empty();
    protected Set<String> fields = null;
    protected String filterQuery = null;
    protected String filterField = null;
    protected Object filterValue = null;

    public QueryOptions() {
        // default options
    }

    /**
     * Builds options from an already-evaluated argument sequence: a map ({@link #QueryOptions(AbstractMapType)})
     * or an XML element ({@link #QueryOptions(XQueryContext, NodeValue)}), or the defaults if
     * {@code optSeq} is {@code null} (the argument wasn't statically provided at all). Shared
     * dispatch for every {@code ft:query*} function's trailing {@code options} argument — see
     * {@link Query#parseOptions(org.exist.xquery.Function, Sequence, Item, int)} and
     * {@link AbstractVectorQueryFunction#parseOptionsArg(Sequence[])}.
     *
     * <p>Deliberately does <em>not</em> special-case a non-null but empty {@code optSeq} (an
     * argument that WAS provided but evaluated to {@code ()}): {@link Type#EMPTY_SEQUENCE} isn't
     * a subtype of {@link Type#ELEMENT} or {@link Type#MAP_ITEM}, so it falls through to the same
     * error every other wrong-shaped argument gets. ft:query/ft:query-field have always thrown
     * here (matching an explicit, if unusual, {@code ft:query(nodes, "text", ())} call); callers
     * that want a provided-but-empty options argument to mean "defaults" (as
     * ft:query-vector/ft:query-field-vector do) need to make that call themselves, by passing
     * {@code null} instead of the empty sequence — see parseOptionsArg.</p>
     *
     * @param context the XQuery context, needed to stream an XML-element root
     * @param errorExpr the expression to attribute a type error to
     * @param optSeq the evaluated options argument, or {@code null} if not provided
     * @throws XPathException if optSeq is non-null and neither a map nor an XML element (empty included)
     */
    public static QueryOptions fromSequence(final XQueryContext context, final Expression errorExpr,
            @Nullable final Sequence optSeq) throws XPathException {
        if (optSeq == null) {
            return new QueryOptions();
        }
        if (Type.subTypeOf(optSeq.getItemType(), Type.ELEMENT)) {
            return new QueryOptions(context, (NodeValue) optSeq.itemAt(0));
        }
        if (Type.subTypeOf(optSeq.getItemType(), Type.MAP_ITEM)) {
            return new QueryOptions((AbstractMapType) optSeq.itemAt(0));
        }
        throw new XPathException(errorExpr, LuceneModule.EXXQDYFT0004, "Options must be a map or XML element");
    }

    public QueryOptions(XQueryContext context, NodeValue root) throws XPathException {
        try {
            final int thisLevel = root.getNodeId().getTreeLevel();
            final XMLStreamReader reader = context.getXMLStreamReader(root);
            reader.next();
            reader.next();
            while (reader.hasNext()) {
                int status = reader.next();
                if (status == XMLStreamReader.START_ELEMENT) {
                    set(reader.getLocalName(), reader.getElementText());
                } else if (status == XMLStreamReader.END_ELEMENT) {
                    final NodeId otherId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                    final int otherLevel = otherId.getTreeLevel();
                    if (otherLevel == thisLevel) {
                        // finished `optRoot` element...
                        break;  // exit-while
                    }
                }
            }
        } catch (XMLStreamException | IOException e) {
            throw new XPathException((Expression) null, LuceneModule.EXXQDYFT0004, "Error while parsing options to ft:query: " + e.getMessage(), e);
        }
    }

    public QueryOptions(final AbstractMapType map) throws XPathException {
        for (final IEntry<AtomicValue, Sequence> entry: map) {
            final String key = entry.key().getStringValue();
            if (key.equals(OPTION_FACETS) && entry.value().hasOne() && entry.value().getItemType() == Type.MAP_ITEM) {

                // iterate over each dimension and collect its values into a FacetQuery
                final AbstractMapType subMap = (AbstractMapType) entry.value().itemAt(0);

                // map to hold the facet values for each dimension
                final Map<String, FacetQuery> tf = new HashMap<>(subMap.size());

                for (final IEntry<AtomicValue, Sequence> facet : subMap) {
                    final Sequence value = facet.value();
                    final FacetQuery values;
                    if (value.hasOne() && value.getItemType() == Type.ARRAY_ITEM) {
                        values = new FacetQuery((ArrayType) facet.value().itemAt(0));
                    } else {
                        values = new FacetQuery(value);
                    }
                    tf.put(facet.key().getStringValue(), values);
                }
                facets = Optional.of(tf);
            } else if (key.equals(OPTION_FILTER) && entry.value().hasOne() && entry.value().getItemType() == Type.MAP_ITEM) {
                final AbstractMapType filterMap = (AbstractMapType) entry.value().itemAt(0);
                filterField = getMapString(filterMap, "field");
                filterValue = getMapValue(filterMap, "value");
            } else {
                set(key, entry.value().getStringValue());
            }
        }
    }

    private static String getMapString(final AbstractMapType map, final String key) throws XPathException {
        final Sequence seq = map.get(new org.exist.xquery.value.StringValue(key));
        return seq != null && !seq.isEmpty() ? seq.getStringValue() : null;
    }

    private static Object getMapValue(final AbstractMapType map, final String key) throws XPathException {
        final Sequence seq = map.get(new org.exist.xquery.value.StringValue(key));
        if (seq == null || seq.isEmpty()) {
            return null;
        }
        final org.exist.xquery.value.Item item = seq.itemAt(0);
        if (item instanceof org.exist.xquery.value.IntegerValue iv) {
            return iv.getLong();
        }
        if (item instanceof org.exist.xquery.value.DecimalValue dv) {
            return dv.getDouble();
        }
        return item.getStringValue();
    }

    /**
     * Holds the values of a facet for drill down. To support
     * multiple query values for a hierarchical facet, values are
     * kept in a two-dimensional list.
     */
    public static class FacetQuery {
        final List<List<String>> values;

        /**
         * Create a single query value from a flat sequence.
         *
         * @param input input sequence
         * @throws XPathException in case of conversion errors
         */
        public FacetQuery(final Sequence input) throws XPathException {
            values = new ArrayList<>(1);
            List<String> subValues = new ArrayList<>(input.getItemCount());
            for (SequenceIterator si = input.unorderedIterator(); si.hasNext(); ) {
                final String value = si.nextItem().getStringValue();
                if (!value.isEmpty()) {
                    subValues.add(value);
                }
            }
            values.add(subValues);
        }

        /**
         * Create a multi-valued query from an XQuery array.
         *
         * @param input an XQuery array
         * @throws XPathException in case of conversion errors
         */
        public FacetQuery(final ArrayType input) throws XPathException {
            final Sequence items[] = input.toArray();
            values = new ArrayList<>(items.length);
            for (Sequence seq : items) {
                final List<String> subValues = new ArrayList<>(seq.getItemCount());
                for (SequenceIterator si = seq.unorderedIterator(); si.hasNext(); ) {
                    final String value = si.nextItem().getStringValue();
                    if (!value.isEmpty()) {
                        subValues.add(value);
                    }
                }
                values.add(subValues);
            }
        }

        /**
         * Add the values for the facet dimension to the drill down query.
         *
         * @param dimension the facet dimension
         * @param query the lucene drill down query
         * @param hierarchical true if the facet is hierarchical
         */
        public void toQuery(final String dimension, final DrillDownQuery query, final boolean hierarchical) {
            for (List<String> subValues : values) {
                if (hierarchical) {
                    final String[] result = new String[subValues.size()];
                    subValues.toArray(result);
                    query.add(dimension, result);
                } else {
                    for (String value : subValues) {
                        query.add(dimension, value);
                    }
                }
            }
        }
    }

    public Optional<Map<String, FacetQuery>> getFacets() {
        return facets;
    }

    public @Nullable String getFilterQuery() {
        return filterQuery;
    }

    public @Nullable String getFilterField() {
        return filterField;
    }

    public @Nullable Object getFilterValue() {
        return filterValue;
    }

    public @Nullable Set<String> getFields() {
        return fields;
    }

    public boolean filterRewrite() {
        return filterRewrite;
    }

    private void set(String key, String value) throws XPathException {
        switch (key) {
            case OPTION_DEFAULT_OPERATOR:
                if (value.equalsIgnoreCase(DEFAULT_OPERATOR_OR)) {
                    defaultOperator = OR;
                }
                break;
            case OPTION_LEADING_WILDCARD:
                allowLeadingWildcard = Configuration.parseBoolean(value, false);
                break;
            case OPTION_PHRASE_SLOP:
                try {
                    phraseSlop = Optional.of(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    throw new XPathException((Expression) null, LuceneModule.EXXQDYFT0004, "Option " + OPTION_PHRASE_SLOP + " must be an integer");
                }
                break;
            case OPTION_FILTER_REWRITE:
                filterRewrite = Configuration.parseBoolean(value, false);
                break;
            case OPTION_LOWERCASE_EXPANDED_TERMS:
                lowercaseExpandedTerms = Configuration.parseBoolean(value, false);
                break;
            case OPTION_QUERY_ANALYZER_ID:
                queryAnalyzerId = value;
                break;
            case OPTION_FILTER_QUERY:
                filterQuery = value;
                break;
            default:
                // unknown option, ignore
                break;
        }
    }

    public void configureParser(CommonQueryParserConfiguration parser) {
        if (parser instanceof QueryParserBase base) {
            switch (defaultOperator) {
                case OR:
                    base.setDefaultOperator(QueryParser.OR_OPERATOR);
                    break;
                default:
                    base.setDefaultOperator(QueryParser.AND_OPERATOR);
                    break;
            }
        }
        if (allowLeadingWildcard)
            parser.setAllowLeadingWildcard(true);
        phraseSlop.ifPresent(parser::setPhraseSlop);
        if (filterRewrite)
            parser.setMultiTermRewriteMethod(MultiTermQuery.CONSTANT_SCORE_REWRITE);
        else
            parser.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
        if (lowercaseExpandedTerms) {
            // parser.setLowercaseExpandedTerms(lowercaseExpandedTerms);
        }
    }

    public String  getQueryAnalyzerId() { return queryAnalyzerId; }
}
