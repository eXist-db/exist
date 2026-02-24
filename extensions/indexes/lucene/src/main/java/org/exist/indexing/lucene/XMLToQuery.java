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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.IntField;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queries.spans.SpanFirstQuery;
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.ByteRunAutomaton;
import org.apache.lucene.util.automaton.LevenshteinAutomata;
import org.apache.lucene.util.automaton.RunAutomaton;
import org.apache.lucene.util.automaton.UTF32ToUTF8;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.modules.lucene.QueryOptions;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the XML representation of a Lucene query and transforms
 * it into a tree of {@link org.apache.lucene.search.Query} objects.
 */
public class XMLToQuery {

    private final LuceneIndex index;

    public XMLToQuery(LuceneIndex index) {
        this.index = index;
    }

    public Query parse(String field, Element root, Analyzer analyzer, QueryOptions options) throws XPathException {
        Query query = null;
        String localName = root.getLocalName();
        if (null != localName) {
            query = switch (localName) {
                case "query" -> parseChildren(field, root, analyzer, options);
                case "term" -> termQuery(getField(root, field), root, analyzer);
                case "wildcard" -> wildcardQuery(getField(root, field), root, options);
                case "prefix" -> prefixQuery(getField(root, field), root, options);
                case "fuzzy" -> fuzzyQuery(getField(root, field), root);
                case "bool" -> booleanQuery(getField(root, field), root, analyzer, options);
                case "phrase" -> phraseQuery(getField(root, field), root, analyzer);
                case "near" -> nearQuery(getField(root, field), root, analyzer);
                case "first" -> getSpanFirst(getField(root, field), root, analyzer);
                case "regex" -> regexQuery(getField(root, field), root, options);
                case "id" -> idQuery(getField(root, field), root);
                default ->
                        throw new XPathException((Expression) null, "Unknown element in lucene query expression: " + localName);
            };
        }

        if (query != null) {
            query = setBoost(root, query);
        }

        return query;
    }

    private Query phraseQuery(String field, Element node, Analyzer analyzer) throws XPathException {
        NodeList termList = node.getElementsByTagName("term");
        if (termList.getLength() == 0) {
            // Use PhraseQuery.Builder in Lucene 10
            PhraseQuery.Builder builder = new PhraseQuery.Builder();
            String qstr = getText(node);
            try (TokenStream stream = analyzer.tokenStream(field, new StringReader(qstr))) {
                CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
                stream.reset();
                int pos = 0;
                while (stream.incrementToken()) {
                    builder.add(new Term(field, termAttr.toString()), pos++);
                }
                stream.end();
            } catch (IOException e) {
                throw new XPathException((Expression) null, "Error while parsing phrase query: " + qstr);
            }
            int slop = getSlop(node);
            if (slop > -1)
                builder.setSlop(slop);
            return builder.build();
        }
        MultiPhraseQuery.Builder builder = new MultiPhraseQuery.Builder();
        for (int i = 0; i < termList.getLength(); i++) {
            Element elem = (Element) termList.item(i);
            String text = getText(elem);
            if (text.indexOf('?') > -1 || text.indexOf('*') > -1) {
                try {
                    Term[] expanded = expandTerms(field, text);
                    if (expanded.length > 0)
                        builder.add(expanded);
                } catch (IOException e) {
                    throw new XPathException((Expression) null, "IO error while expanding query terms: " + e.getMessage(), e);
                }
            } else {
                String termStr = getTerm(field, text, analyzer);
                if (termStr != null)
                    builder.add(new Term(field, termStr));
            }
        }
        int slop = getSlop(node);
        if (slop > -1)
            builder.setSlop(slop);
        return builder.build();
    }

    private SpanQuery nearQuery(String field, Element node, Analyzer analyzer) throws XPathException {
        int slop = getSlop(node);
        if (slop < 0)
            slop = 0;
        boolean inOrder = true;
        if (node.hasAttribute("ordered"))
            inOrder = "yes".equals(node.getAttribute("ordered"));

        if (!hasElementContent(node)) {
            String qstr = getText(node);
            List<SpanTermQuery> list = new ArrayList<>(8);
            try (TokenStream stream = analyzer.tokenStream(field, new StringReader(qstr))) {
                CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
                stream.reset();
                while (stream.incrementToken()) {
                    list.add(new SpanTermQuery(new Term(field, termAttr.toString())));
                }
                stream.end();
            } catch (IOException e) {
                throw new XPathException((Expression) null, "Error while parsing phrase query: " + qstr);
            }
            return spanNearOrSingle(list.toArray(new SpanTermQuery[0]), slop, inOrder);
        }
        SpanQuery[] children = parseSpanChildren(field, node, analyzer);
        return spanNearOrSingle(children, slop, inOrder);
    }

    /** SpanNearQuery requires at least 2 clauses; return single span or null for 0. */
    private SpanQuery spanNearOrSingle(SpanQuery[] clauses, int slop, boolean inOrder) {
        if (clauses.length == 0) return null;
        if (clauses.length == 1) return clauses[0];
        return new SpanNearQuery(clauses, slop, inOrder);
    }

    private SpanQuery[] parseSpanChildren(String field, Element node, Analyzer analyzer) throws XPathException {
        List<SpanQuery> list = new ArrayList<>(8);
        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                final String localName = child.getLocalName();
                if (null != localName) {
                    switch (localName) {
                        case "term":
                            getSpanTerm(list, field, (Element) child, analyzer);
                            break;
                        case "near": {
                            SpanQuery sq = nearQuery(field, (Element) child, analyzer);
                            if (sq != null) list.add(sq);
                            break;
                        }
                        case "first":
                            list.add(getSpanFirst(field, (Element) child, analyzer));
                            break;
                        case "regex":
                            list.add(getSpanRegex(field, (Element) child, analyzer));
                            break;
                        default:
                            throw new XPathException((Expression) null, "Unknown query element: " + child.getNodeName());
                    }
                }
            }
            child = child.getNextSibling();
        }
        return list.toArray(new SpanQuery[0]);
    }

    private void getSpanTerm(List<SpanQuery> list, String field, Element node, Analyzer analyzer) throws XPathException {
    	String termStr = getTerm(field, getText(node), analyzer);
    	if (termStr != null)
    		list.add(new SpanTermQuery(new Term(field, termStr)));
    }

    private SpanQuery getSpanRegex(String field, Element node, Analyzer analyzer) {
    	String regex = getText(node);
    	return new SpanMultiTermQueryWrapper<>(new RegexpQuery(new Term(field, regex)));
    }
    
    private SpanQuery getSpanFirst(String field, Element node, Analyzer analyzer) throws XPathException {
    	int slop = getSlop(node);
        if (slop < 0)
            slop = 0;
        boolean inOrder = true;
        if (node.hasAttribute("ordered"))
            inOrder = "yes".equals(node.getAttribute("ordered"));
        SpanQuery query = null;
        if (hasElementContent(node)) {
            SpanQuery[] children = parseSpanChildren(field, node, analyzer);
            query = new SpanNearQuery(children, slop, inOrder);
        } else {
        	String termStr = getTerm(field, getText(node), analyzer);
        	if (termStr != null)
        		query = new SpanTermQuery(new Term(field, termStr));
        }
        int end = 0;
        if (node.hasAttribute("end")) {
            try {
                end = Integer.parseInt(node.getAttribute("end"));
            } catch (NumberFormatException e) {
                throw new XPathException((Expression) null, "Attribute 'end' to query element 'first' should be a " +
                        "valid integer. Got: " + node.getAttribute("end"));
            }
        }
        return query != null ? new SpanFirstQuery(query, end) : null;
    }

    private int getSlop(Element node) throws XPathException {
        String slop = node.getAttribute("slop");
        if (!slop.isEmpty()) {
            try {
                return Integer.parseInt(slop);
            } catch (NumberFormatException e) {
                throw new XPathException((Expression) null, "Query parameter 'slop' should be an integer value. Got: " + slop);
            }
        }
        return -1;
    }

    private Term[] expandTerms(String field, String queryStr) throws XPathException, IOException {
        return expandTerms(field, (CharSequence) queryStr);
    }

    // Extracted overload to allow more flexible callers while retaining original signature
    private Term[] expandTerms(String field, CharSequence wildcardPattern) throws XPathException, IOException {
        return index.withReader(reader -> {
            final ByteRunAutomaton run = buildWildcardRunAutomaton(wildcardPattern.toString());

            final List<Term> termList = new ArrayList<>(8);
            for (LeafReaderContext ctx : reader.leaves()) {
                final Terms terms = ctx.reader().terms(field);
                if (terms == null) {
                    continue;
                }
                collectMatchingTermsForLeaf(field, run, terms, termList);
            }
            return termList.toArray(new Term[0]);
        });
    }

    // Build RunAutomaton from a wildcard pattern using RegExp
    private ByteRunAutomaton buildWildcardRunAutomaton(String wildcardPattern) {
        // Convert wildcard (*, ?) to a RegExp that matches the same semantics
        // Escape other regex meta characters to treat them literally
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wildcardPattern.length(); i++) {
            char c = wildcardPattern.charAt(i);
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append(".");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '.': case '+': case '(': case ')': case '[': case ']':
                case '{': case '}': case '^': case '$': case '|':
                    sb.append('\\').append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        final String regex = sb.toString();
        final org.apache.lucene.util.automaton.Automaton wildcardAutomaton =
                new org.apache.lucene.util.automaton.RegExp(regex).toAutomaton();
        return new org.apache.lucene.util.automaton.ByteRunAutomaton(wildcardAutomaton);
    }

    // Extracted to simplify expandTerms loop body
    private void collectMatchingTermsForLeaf(String field, ByteRunAutomaton run, Terms terms, List<Term> out) throws IOException {
        final TermsEnum te = terms.iterator();
        BytesRef term;
        while ((term = te.next()) != null) {
            if (matchesWildcard(run, term)) {
                out.add(new Term(field, term.utf8ToString()));
            }
        }
    }

    // Extracted and clarified name; delegates to run.run with BytesRef slices
    private static boolean matchesWildcard(ByteRunAutomaton run, BytesRef utf8Term) {
        return run.run(utf8Term.bytes, utf8Term.offset, utf8Term.length);
    }

    private Query termQuery(String field, Element node, Analyzer analyzer) throws XPathException {
    	String termStr = getTerm(field, getText(node), analyzer);
    	return termStr == null ? null : new TermQuery(new Term(field, termStr));
    }

    private Query idQuery(String field, Element node) {
        String text = getText(node);
        int docId = Integer.parseInt(text);
        return IntField.newExactQuery(field, docId);
    }

    private String getTerm(String field, String text, Analyzer analyzer) throws XPathException {
    	String term = null;
        try (TokenStream stream = analyzer.tokenStream(field, new StringReader(text))) {
            CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            if (stream.incrementToken()) {
                term = termAttr.toString();
            }
            stream.end();
            return term;
        } catch (IOException e) {
            throw new XPathException((Expression) null, "Lucene index error while creating query: " + e.getMessage(), e);
        }
    }
    
    private Query wildcardQuery(String field, Element node, QueryOptions options) {
        WildcardQuery query = new WildcardQuery(new Term(field, getText(node)));
        setRewriteMethod(query, node, options);
        return query;
    }

    private Query prefixQuery(String field, Element node, QueryOptions options) {
        PrefixQuery query = new PrefixQuery(new Term(field, getText(node)));
        setRewriteMethod(query, node, options);
        return query;
    }

    private Query fuzzyQuery(String field, Element node) throws XPathException {
        int maxEdits = FuzzyQuery.defaultMaxEdits;
        String attr = node.getAttribute("max-edits");
        if (!attr.isEmpty()) {
            try {
                maxEdits = Integer.parseInt(attr);
                if (maxEdits < 0 || maxEdits > LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE) {
                    throw new XPathException((Expression) null, "Query parameter max-edits must be <= " + LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE);
                }
            } catch (NumberFormatException e) {
                throw new XPathException((Expression) null, "Query parameter 'max-edits' should be an integer value. Got: " + attr);
            }
        }
        return new FuzzyQuery(new Term(field, getText(node)), maxEdits);
    }

    private Query regexQuery(String field, Element node, QueryOptions options) {
        RegexpQuery query = new RegexpQuery(new Term(field, getText(node)));
        setRewriteMethod(query, node, options);
        return query;
    }

    private Query booleanQuery(String field, Element node, Analyzer analyzer, QueryOptions options) throws XPathException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        String minOpt = node.getAttribute("min");
        if (!minOpt.isEmpty()) {
            try {
                int minMust = Integer.parseInt(minOpt);
                builder.setMinimumNumberShouldMatch(minMust);
            } catch (NumberFormatException ex) {
                // ignore
            }
        }

        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                Query childQuery = parse(field, elem, analyzer, options);
                if (childQuery != null) {
	                BooleanClause.Occur occur = getOccur(elem);
	                builder.add(childQuery, occur);
                }
            }
            child = child.getNextSibling();
        }
        return builder.build();
    }

    // Helper that applies rewrite lazily to a MultiTermQuery
    private static final class LazyRewriteQuery extends Query {
        private final MultiTermQuery inner;
        private final MultiTermQuery.RewriteMethod rewrite;

        LazyRewriteQuery(MultiTermQuery inner, MultiTermQuery.RewriteMethod rewrite) {
            this.inner = inner;
            this.rewrite = rewrite;
        }

        @Override
        public Query rewrite(IndexSearcher searcher) throws IOException {
            inner.getRewriteMethod();
            return inner.rewrite(searcher);
        }

        @Override
        public void visit(QueryVisitor visitor) {
            // Delegate structure traversal to the wrapped query
            inner.visit(visitor);
        }

        @Override
        public String toString(String field) {
            return inner.toString(field);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LazyRewriteQuery)) return false;
            LazyRewriteQuery other = (LazyRewriteQuery) obj;
            return inner.equals(other.inner) && rewrite.equals(other.rewrite);
        }

        @Override
        public int hashCode() {
            int h = inner.hashCode();
            h = 31 * h + rewrite.hashCode();
            return h;
        }
    }

    // Returns a Query that applies the rewrite lazily
    private Query setRewriteMethod(MultiTermQuery mtq, Element node, QueryOptions options) {
        boolean doFilterRewrite = options.filterRewrite();
        String option = node.getAttribute("filter-rewrite");
        if (!option.isEmpty()) {
            doFilterRewrite = "yes".equalsIgnoreCase(option);
        }

        if (doFilterRewrite) {
            return new LazyRewriteQuery(mtq, MultiTermQuery.CONSTANT_SCORE_REWRITE);
        } else {
            int topTermsLimit = 1024;
            String topAttr = node.getAttribute("top-terms");
            if (!topAttr.isEmpty()) {
                try {
                    int parsed = Integer.parseInt(topAttr);
                    if (parsed > 0) topTermsLimit = parsed;
                } catch (NumberFormatException ignore) { }
            }
            return new LazyRewriteQuery(mtq, new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(topTermsLimit));
        }
    }

    private BooleanClause.Occur getOccur(Element elem) {
        BooleanClause.Occur occur = BooleanClause.Occur.SHOULD;
        String occurOpt = elem.getAttribute("occur");
        if (!occurOpt.isEmpty()) {
            occur = switch (occurOpt) {
                case "must" -> BooleanClause.Occur.MUST;
                case "not" -> BooleanClause.Occur.MUST_NOT;
                case "should" -> BooleanClause.Occur.SHOULD;
                default -> occur;
            };
        }
        return occur;
    }

    private Query parseChildren(String field, Element root, Analyzer analyzer, QueryOptions options) throws XPathException {
        Query query = null;
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Query childQuery = parse(field, (Element) child, analyzer, options);
                if (query != null) {
                    if (query instanceof BooleanQuery) {
                        // migrate to builder
                        BooleanQuery.Builder b = new BooleanQuery.Builder();
                        BooleanQuery existing = (BooleanQuery) query;
                        for (BooleanClause c : existing) b.add(c);
                        b.add(childQuery, BooleanClause.Occur.SHOULD);
                        query = b.build();
                    } else {
                        BooleanQuery.Builder boolBuilder = new BooleanQuery.Builder();
                        boolBuilder.add(query, BooleanClause.Occur.SHOULD);
                        boolBuilder.add(childQuery, BooleanClause.Occur.SHOULD);
                        query = boolBuilder.build();
                    }
                } else
                    query = childQuery;
            }
            child = child.getNextSibling();
        }
        return query;
    }

    private Query setBoost(Element node, Query query) throws XPathException {
        String boost = node.getAttribute("boost");
        if (!boost.isEmpty()) {
            try {
                float b = Float.parseFloat(boost);
                if (!(query instanceof BoostQuery)) {
                    return new BoostQuery(query, b);
                }
            } catch (NumberFormatException e) {
                throw new XPathException((Expression) null, "Bad value for boost in query parameter. Got: " + boost);
            }
        }
        return query;
    }

    private String getText(Element root) {
        final StringBuilder buf = new StringBuilder();
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE) {
                buf.append(child.getNodeValue());
            }
            child = child.getNextSibling();
        }
        return buf.toString();
    }

    private boolean hasElementContent(final Element root) {
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
            child = child.getNextSibling();
        }
        return false;
    }

    private String getField(Element node, String defaultField) {
        final String field = node.getAttribute("field");
        if (field.isEmpty()) {
            return defaultField;
        }
        return field;
    }
}
