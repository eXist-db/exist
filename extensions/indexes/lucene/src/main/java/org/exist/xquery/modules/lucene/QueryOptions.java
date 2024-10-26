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
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;

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
    public static final String OPTION_FIELDS = "fields";
    public static final String OPTION_QUERY_ANALYZER_ID = "query-analyzer-id";

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

    public QueryOptions() {
        // default options
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

    public QueryOptions(AbstractMapType map) throws XPathException {
        for (final IEntry<AtomicValue, Sequence> entry: map) {
            final String key = entry.key().getStringValue();
            if (key.equals(OPTION_FIELDS) && !entry.value().isEmpty()) {
                fields = new HashSet<>();
                for (SequenceIterator i = entry.value().unorderedIterator(); i.hasNext(); ) {
                    fields.add(i.nextItem().getStringValue());
                }
            } else if (key.equals(OPTION_FACETS) && entry.value().hasOne() && entry.value().getItemType() == Type.MAP) {
                // map to hold the facet values for each dimension
                final Map<String, FacetQuery> tf = new HashMap<>();
                // iterate over each dimension and collect its values into a FacetQuery
                for (final IEntry<AtomicValue, Sequence> facet: (AbstractMapType) entry.value().itemAt(0)) {
                    final Sequence value = facet.value();
                    FacetQuery values;
                    if (value.hasOne() && value.getItemType() == Type.ARRAY) {
                        values = new FacetQuery((ArrayType) facet.value().itemAt(0));
                    } else {
                        values = new FacetQuery(value);
                    }
                    tf.put(facet.key().getStringValue(), values);
                }
                facets = Optional.of(tf);
            } else {
                set(key, entry.value().getStringValue());
            }
        }
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
                allowLeadingWildcard = value.equalsIgnoreCase("yes");
                break;
            case OPTION_PHRASE_SLOP:
                try {
                    phraseSlop = Optional.of(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    throw new XPathException((Expression) null, LuceneModule.EXXQDYFT0004, "Option " + OPTION_PHRASE_SLOP + " must be an integer");
                }
                break;
            case OPTION_FILTER_REWRITE:
                filterRewrite = value.equalsIgnoreCase("yes");
                break;
            case OPTION_LOWERCASE_EXPANDED_TERMS:
                lowercaseExpandedTerms = value.equalsIgnoreCase("yes");
                break;
            case OPTION_QUERY_ANALYZER_ID:
                queryAnalyzerId = value;
            default:
                // unknown option, ignore
                break;
        }
    }

    public void configureParser(CommonQueryParserConfiguration parser) {
        if (parser instanceof QueryParserBase) {
            switch (defaultOperator) {
                case OR:
                    ((QueryParserBase) parser).setDefaultOperator(QueryParser.OR_OPERATOR);
                    break;
                default:
                    ((QueryParserBase) parser).setDefaultOperator(QueryParser.AND_OPERATOR);
                    break;
            }
        }
        if (allowLeadingWildcard)
            parser.setAllowLeadingWildcard(true);
        phraseSlop.ifPresent(parser::setPhraseSlop);
        if (filterRewrite)
            parser.setMultiTermRewriteMethod(MultiTermQuery.CONSTANT_SCORE_FILTER_REWRITE);
        else
            parser.setMultiTermRewriteMethod(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE);
        if (lowercaseExpandedTerms) {
            parser.setLowercaseExpandedTerms(lowercaseExpandedTerms);
        }
    }

    public String  getQueryAnalyzerId() { return queryAnalyzerId; }
}
