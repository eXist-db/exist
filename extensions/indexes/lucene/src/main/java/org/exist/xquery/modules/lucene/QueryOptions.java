/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.modules.lucene;

import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.MultiTermQuery;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
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

    protected enum DefaultOperator {
        OR,
        AND
    }

    protected DefaultOperator defaultOperator = DefaultOperator.AND;
    protected boolean allowLeadingWildcard = false;
    protected Optional<Integer> phraseSlop = Optional.empty();

    protected boolean filterRewrite = false;
    protected boolean lowercaseExpandedTerms = false;
    protected Optional<Map<String, List<String>>> facets = Optional.empty();
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
            throw new XPathException(LuceneModule.EXXQDYFT0004, "Error while parsing options to ft:query: " + e.getMessage(), e);
        }
    }

    public QueryOptions(AbstractMapType map) throws XPathException {
        for (Map.Entry<AtomicValue, Sequence> entry: map) {
            final String key = entry.getKey().getStringValue();
            if (key.equals(OPTION_FIELDS) && !entry.getValue().isEmpty()) {
                fields = new HashSet<>();
                for (SequenceIterator i = entry.getValue().unorderedIterator(); i.hasNext(); ) {
                    fields.add(i.nextItem().getStringValue());
                }
            } else if (key.equals(OPTION_FACETS) && entry.getValue().hasOne() && entry.getValue().getItemType() == Type.MAP) {
                final Map<String, List<String>> tf = new HashMap<>();
                for (Map.Entry<AtomicValue, Sequence> facet: (AbstractMapType) entry.getValue().itemAt(0)) {
                    final List<String> values = new ArrayList<>(5);
                    for (SequenceIterator si = facet.getValue().unorderedIterator(); si.hasNext(); ) {
                        values.add(si.nextItem().getStringValue());
                    }
                    tf.put(facet.getKey().getStringValue(), values);
                }
                facets = Optional.of(tf);
            } else {
                set(key, entry.getValue().getStringValue());
            }
        }
    }

    public Optional<Map<String, List<String>>> getFacets() {
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
                    throw new XPathException(LuceneModule.EXXQDYFT0004, "Option " + OPTION_PHRASE_SLOP + " must be an integer");
                }
                break;
            case OPTION_FILTER_REWRITE:
                filterRewrite = value.equalsIgnoreCase("yes");
                break;
            case OPTION_LOWERCASE_EXPANDED_TERMS:
                lowercaseExpandedTerms = value.equalsIgnoreCase("yes");
                break;
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
        if (phraseSlop.isPresent()) {
            parser.setPhraseSlop(phraseSlop.get());
        }
        if (filterRewrite)
            parser.setMultiTermRewriteMethod(MultiTermQuery.CONSTANT_SCORE_FILTER_REWRITE);
        else
            parser.setMultiTermRewriteMethod(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE);
        if (lowercaseExpandedTerms) {
            parser.setLowercaseExpandedTerms(lowercaseExpandedTerms);
        }
    }
}
