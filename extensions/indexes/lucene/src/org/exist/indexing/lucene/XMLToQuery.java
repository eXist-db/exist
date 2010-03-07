/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * \$Id\$
 */

package org.exist.indexing.lucene;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.apache.lucene.search.*;
import org.apache.lucene.search.regex.RegexQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanFirstQuery;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Token;
import org.exist.xquery.XPathException;

import java.io.StringReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Parses the XML representation of a Lucene query and transforms
 * it into a tree of {@link org.apache.lucene.search.Query} objects.
 */
public class XMLToQuery {

    private LuceneIndex index;

    public XMLToQuery(LuceneIndex index) {
        this.index = index;
    }

    public Query parse(String field, Element root, Analyzer analyzer, Properties options) throws XPathException {
        Query query;
        String localName = root.getLocalName();
        if ("query".equals(localName))
            query = parseChildren(field, root, analyzer, options);
        else if ("term".equals(localName))
            query = termQuery(field, root);
        else if ("wildcard".equals(localName))
            query = wildcardQuery(field, root, options);
        else if ("prefix".equals(localName))
            query = prefixQuery(field, root, options);
        else if ("fuzzy".equals(localName))
            query = fuzzyQuery(field, root);
        else if ("bool".equals(localName))
            query = booleanQuery(field, root, analyzer, options);
        else if ("phrase".equals(localName))
            query = phraseQuery(field, root, analyzer);
        else if ("near".equals(localName))
            query = nearQuery(field, root, analyzer);
        else if ("first".equals(localName))
            query = getSpanFirst(field, root, analyzer);
        else if ("regex".equals(localName))
            query = regexQuery(field, root, options);
        else
            throw new XPathException("Unknown element in lucene query expression: " + localName);
        setBoost(root, query);
        return query;
    }

    private Query phraseQuery(String field, Element node, Analyzer analyzer) throws XPathException {
        NodeList termList = node.getElementsByTagName("term");
        if (termList.getLength() == 0) {
            PhraseQuery query = new PhraseQuery();
            String qstr = getText(node);
            TokenStream stream = analyzer.tokenStream(field, new StringReader(qstr));
            Token token = new Token();
            try {
                while ((token = stream.next(token)) != null) {
                    String str = new String(token.termBuffer(), 0, token.termLength());
                    query.add(new Term(field, str));
                }
            } catch (IOException e) {
                throw new XPathException("Error while parsing phrase query: " + qstr);
            }
            int slop = getSlop(node);
            if (slop > -1)
                query.setSlop(slop);
            return query;
        } else {
            MultiPhraseQuery query = new MultiPhraseQuery();
            for (int i = 0; i < termList.getLength(); i++) {
                Element elem = (Element) termList.item(i);
                String text = getText(elem);
                if (text.indexOf('?') > -1 || text.indexOf('*') > 0) {
                    Term[] expanded = expandTerms(field, text);
                    if (expanded.length > 0)
                        query.add(expanded);
                } else
                    query.add(new Term(field, text));
            }
            int slop = getSlop(node);
            if (slop > -1)
                query.setSlop(slop);
            return query;
        }
    }

    private SpanQuery nearQuery(String field, Element node, Analyzer analyzer) throws XPathException {
        int slop = getSlop(node);
        if (slop < 0)
            slop = 0;
        boolean inOrder = true;
        if (node.hasAttribute("ordered"))
            inOrder = node.getAttribute("ordered").equals("yes");

        if (!hasElementContent(node)) {
            String qstr = getText(node);
            TokenStream stream = analyzer.tokenStream(field, new StringReader(qstr));
            List<SpanTermQuery> list = new ArrayList<SpanTermQuery>(8);
            Token token = new Token();
            try {
                while ((token = stream.next(token)) != null) {
                    String str = new String(token.termBuffer(), 0, token.termLength());
                    list.add(new SpanTermQuery(new Term(field, str)));
                }
            } catch (IOException e) {
                throw new XPathException("Error while parsing phrase query: " + qstr);
            }

            return new SpanNearQuery(list.toArray(new SpanTermQuery[list.size()]), slop, inOrder);
        } else {
            SpanQuery[] children = parseSpanChildren(field, node, analyzer);
            return new SpanNearQuery(children, slop, inOrder);
        }
    }

    private SpanQuery[] parseSpanChildren(String field, Element node, Analyzer analyzer) throws XPathException {
        List<SpanQuery> list = new ArrayList<SpanQuery>(8);
        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if ("term".equals(child.getLocalName()))
                    list.add(getSpanTerm(field, (Element) child));
                else if ("near".equals(child.getLocalName()))
                    list.add(nearQuery(field, (Element) child, analyzer));
                else if ("first".equals(child.getLocalName()))
                    list.add(getSpanFirst(field, (Element) child, analyzer));
                else
                    throw new XPathException("Unknown query element: " + child.getNodeName());
            }
            child = child.getNextSibling();
        }
        return list.toArray(new SpanQuery[list.size()]);
    }

    private SpanQuery getSpanTerm(String field, Element node) {
        return new SpanTermQuery(new Term(field, getText(node)));
    }

    private SpanQuery getSpanFirst(String field, Element node, Analyzer analyzer) throws XPathException {
        SpanQuery query;
        if (hasElementContent(node)) {
            SpanQuery[] children = parseSpanChildren(field, node, analyzer);
            if (children.length != 1)
                throw new XPathException("Query element 'first' expects exactly one child element");
            query = children[0];
        } else {
            query = new SpanTermQuery(new Term(field, getText(node)));
        }
        int end = 0;
        if (node.hasAttribute("end")) {
            try {
                end = Integer.parseInt(node.getAttribute("end"));
            } catch (NumberFormatException e) {
                throw new XPathException("Attribute 'end' to query element 'first' should be a " +
                        "valid integer. Got: " + node.getAttribute("end"));
            }
        }
        return new SpanFirstQuery(query, end);
    }

    private int getSlop(Element node) throws XPathException {
        String slop = node.getAttribute("slop");
        if (slop != null && slop.length() > 0) {
            try {
                return Integer.parseInt(slop);
            } catch (NumberFormatException e) {
                throw new XPathException("Query parameter 'slop' should be an integer value. Got: " + slop);
            }
        }
        return -1;
    }

    private Term[] expandTerms(String field, String queryStr) throws XPathException {
        IndexReader reader = null;
        try {
            reader = index.getReader();
            List<Term> termList = new ArrayList<Term>(8);
            WildcardTermEnum terms = new WildcardTermEnum(reader, new Term(field, queryStr));
            Term term;
            do {
                term = terms.term();
                if (term != null && term.field().equals(field)) {
                    termList.add(term);
                }
            } while (terms.next());
            terms.close();
            Term[] matchingTerms = new Term[termList.size()];
            return termList.toArray(matchingTerms);
        } catch (IOException e) {
            throw new XPathException("Lucene index error while creating query: " + e.getMessage(), e);
        } finally {
            index.releaseReader(reader);
        }
    }

    private Query termQuery(String field, Element node) {
        return new TermQuery(new Term(field, getText(node)));
    }

    private Query wildcardQuery(String field, Element node, Properties options) {
        WildcardQuery query = new WildcardQuery(new Term(field, getText(node)));
        setRewriteMethod(query, node, options);
        return query;
    }

    private Query prefixQuery(String field, Element node, Properties options) {
        PrefixQuery query = new PrefixQuery(new Term(field, getText(node)));
        setRewriteMethod(query, node, options);
        return query;
    }

    private Query fuzzyQuery(String field, Element node) throws XPathException {
        float minSimilarity = FuzzyQuery.defaultMinSimilarity;
        String attr = node.getAttribute("min-similarity");
        if (attr != null && attr.length() > 0) {
            try {
                minSimilarity = Float.parseFloat(attr);
            } catch (NumberFormatException e) {
                throw new XPathException("Query parameter 'min-similarity' should be a float value. Got: " + attr);
            }
        }
        return new FuzzyQuery(new Term(field, getText(node)), minSimilarity);
    }

    private Query regexQuery(String field, Element node, Properties options) throws XPathException {
        RegexQuery query = new RegexQuery(new Term(field, getText(node)));
        setRewriteMethod(query, node, options);
        return query;
    }

    private Query booleanQuery(String field, Element node, Analyzer analyzer, Properties options) throws XPathException {
        BooleanQuery query = new BooleanQuery();
        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                Query childQuery = parse(field, elem, analyzer, options);
                BooleanClause.Occur occur = getOccur(elem);
                query.add(childQuery, occur);
            }
            child = child.getNextSibling();
        }
        return query;
    }

    private void setRewriteMethod(MultiTermQuery query, Element node, Properties options) {
        String option = node.getAttribute("filter-rewrite");
        if (option == null)
            option = "yes";
        if (options != null)
            option = options.getProperty(LuceneIndexWorker.OPTION_FILTER_REWRITE, "yes");

        if (option.equalsIgnoreCase("yes"))
            query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_FILTER_REWRITE);
        else
            query.setRewriteMethod(MultiTermQuery.CONSTANT_SCORE_BOOLEAN_QUERY_REWRITE);
    }

    private BooleanClause.Occur getOccur(Element elem) {
        BooleanClause.Occur occur = BooleanClause.Occur.SHOULD;
        String occurOpt = elem.getAttribute("occur");
        if (occurOpt != null) {
            if (occurOpt.equals("must"))
                occur = BooleanClause.Occur.MUST;
            else if (occurOpt.equals("not"))
                occur = BooleanClause.Occur.MUST_NOT;
            else if (occurOpt.equals("should"))
                occur = BooleanClause.Occur.SHOULD;
        }
        return occur;
    }

    private Query parseChildren(String field, Element root, Analyzer analyzer, Properties options) throws XPathException {
        Query query = null;
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Query childQuery = parse(field, (Element) child, analyzer, options);
                if (query != null) {
                    if (query instanceof BooleanQuery)
                        ((BooleanQuery) query).add(childQuery, BooleanClause.Occur.SHOULD);
                    else {
                        BooleanQuery boolQuery = new BooleanQuery();
                        boolQuery.add(query, BooleanClause.Occur.SHOULD);
                        boolQuery.add(childQuery, BooleanClause.Occur.SHOULD);
                        query = boolQuery;
                    }
                } else
                    query = childQuery;
            }
            child = child.getNextSibling();
        }
        return query;
    }

    private void setBoost(Element node, Query query) throws XPathException {
        String boost = node.getAttribute("boost");
        if (boost != null && boost.length() > 0) {
            try {
                query.setBoost(Float.parseFloat(boost));
            } catch (NumberFormatException e) {
                throw new XPathException("Bad value for boost in query parameter. Got: " + boost);
            }
        }
    }

    private String getText(Element root) {
        StringBuffer buf = new StringBuffer();
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.TEXT_NODE)
                buf.append(child.getNodeValue().toLowerCase());
            child = child.getNextSibling();
        }
        return buf.toString();
    }

    private boolean hasElementContent(Element root) {
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE)
                return true;
            child = child.getNextSibling();
        }
        return false;
    }
}
