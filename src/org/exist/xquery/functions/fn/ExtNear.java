/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2002-2009 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.fn;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.EXistException;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.fulltext.FTIndex;
import org.exist.storage.NativeTextEngine;
import org.exist.storage.analysis.TextToken;
import org.exist.storage.analysis.Tokenizer;
import org.exist.util.GlobToRegex;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.PerformanceStats;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * text:near() function.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org> (July 31, 2002)
 */
public class ExtNear extends ExtFulltext {

    private int min_distance = 1;
    private int max_distance = 1;
    private Expression minDistance = null;
    private Expression maxDistance = null;

    public ExtNear(XQueryContext context) {
        super(context, Constants.FULLTEXT_AND);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.functions.ExtFulltext#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(newContextInfo);
        if (maxDistance != null) {
            maxDistance.analyze(newContextInfo);
        }
        if (minDistance != null) {
            minDistance.analyze(newContextInfo);
        }
    }

    public NodeSet preSelect(Sequence contextSequence, boolean useContext)
            throws XPathException {
        final long start = System.currentTimeMillis();
        //The expression can be called multiple times, so we need to
        //clear the previous preselectResult
        preselectResult = null;
        if (maxDistance != null) {
            max_distance = ((IntegerValue) maxDistance.eval(contextSequence).convertTo(Type.INTEGER)).getInt();
        }
        if (minDistance != null) {
            min_distance = ((IntegerValue) minDistance.eval(contextSequence).convertTo(Type.INTEGER)).getInt();
        }
        //Get the search terms
        final String arg = searchTerm.eval(contextSequence).getStringValue();
        String[] terms;
        try {
            terms = getSearchTerms(arg);
        } catch (final EXistException e) {
            throw new XPathException(e.getMessage());
        }
        //Lookup the terms in the full-text index. returns one node set for each term
        final NodeSet[] hits = getMatches(contextSequence.getDocumentSet(),
                useContext ? contextSequence.toNodeSet() : null,
                NodeSet.DESCENDANT, contextQName, terms);
        //Walk through the matches and compute the combined node set
        preselectResult = hits[0];
        if (preselectResult != null) {
            for (int k = 1; k < hits.length; k++) {
                if (hits[k] != null) {
                    preselectResult = preselectResult.deepIntersection(hits[k]);
                }
            }
        } else {
            preselectResult = NodeSet.EMPTY_SET;
        }
        if (terms.length > 1) {
            boolean hasWildcards = false;
            for (int i = 0; i < terms.length; i++) {
                hasWildcards |= NativeTextEngine.containsWildcards(terms[i]);
            }
            preselectResult = (NodeSet) (hasWildcards ?
                patternMatch(context, terms, preselectResult) : 
                exactMatch(context, terms, preselectResult));
        }
        if (context.getProfiler().traceFunctions())
            {context.getProfiler().traceIndexUsage(context, FTIndex.ID, this,
                PerformanceStats.OPTIMIZED_INDEX, System.currentTimeMillis() - start);}
        return preselectResult;
    }

    public Sequence evalQuery(String searchArg, NodeSet nodes) throws XPathException {
        if (maxDistance != null) {
            max_distance = ((IntegerValue) maxDistance.eval(nodes).convertTo(Type.INTEGER)).getInt();
        }
        if (minDistance != null) {
            min_distance = ((IntegerValue) minDistance.eval(nodes).convertTo(Type.INTEGER)).getInt();
        }
        String[] terms;
        try {
            terms = getSearchTerms(searchArg);
        } catch (final EXistException e) {
            throw new XPathException(e.getMessage());
        }
        final NodeSet hits = processQuery(terms, nodes);
        if (hits == null)
            {return Sequence.EMPTY_SEQUENCE;}
        if (terms.length == 1)
            {return hits;}
        boolean hasWildcards = false;
        for (int i = 0; i < terms.length; i++) {
            hasWildcards |= NativeTextEngine.containsWildcards(terms[i]);
        }
        return hasWildcards ? patternMatch(context, terms, hits) : exactMatch(context, terms, hits);
    }

    private Sequence exactMatch(XQueryContext context, String[] terms, NodeSet result) {
        //Walk through hits and calculate term-distances
        final NodeSet r = new ExtArrayNodeSet();
        final Tokenizer tok = context.getBroker().getTextEngine().getTokenizer();
        String term;
        for (final NodeProxy current : result) {
            final String value = current.getNodeValueSeparated();
            tok.setText(value);
            int j = 0;
            if (j < terms.length) {
                term = terms[j];
            } else {
                break;
            }
            int current_distance = -1;
            TextToken token;
            while ((token = tok.nextToken()) != null) {
                final String word = token.getText().toLowerCase();
                if (current_distance > max_distance) {
                    // reset
                    j = 0;
                    term = terms[j];
                    current_distance = -1;
                } //That else would cause some words to be ignored in the matching
                if (word.equalsIgnoreCase(term)) {
                    final boolean withIn = current_distance >= min_distance;
                    current_distance = 0;
                    j++;
                    if (j == terms.length) {
                        //All terms found
                        if (withIn) {
                            r.add(current);
                        }
                        break;
                    } else {
                        term = terms[j];
                    }
                } else if (j > 0 && word.equalsIgnoreCase(terms[0])) {
                    //First search term found: start again
                    j = 1;
                    term = terms[j];
                    current_distance = 0;
                    continue;
                } // that else MAY cause the distance counts to be off by one
                // but i'm not sure
                if (-1 < current_distance) {
                    ++current_distance;
                }
            }
        }
        return r;
    }

    private Sequence patternMatch(XQueryContext context, String[] terms,
            NodeSet result) throws XPathException {
        //Generate list of search term patterns
        final Pattern patterns[] = new Pattern[terms.length];
        final Matcher matchers[] = new Matcher[terms.length];
        for (int i = 0; i < patterns.length; i++) {
            try {
                patterns[i] = Pattern.compile(GlobToRegex.globToRegexp(terms[i]), 
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                matchers[i] = patterns[i].matcher("");
            } catch (final PatternSyntaxException e) {
                throw new XPathException("Malformed pattern: " + patterns[i]);
            }
        }
        //Walk through hits and calculate term-distances
        final ExtArrayNodeSet r = new ExtArrayNodeSet(100);
        final Tokenizer tok = context.getBroker().getTextEngine().getTokenizer();
        Matcher matcher;
        TextToken token;
        for (final NodeProxy current : result) {
            final String value = current.getNodeValueSeparated();
            tok.setText(value);
            int j = 0;
            if (j < patterns.length) {
                matcher = matchers[j];
            } else {
                break;
            }
            int current_distance = -1;
            while ((token = tok.nextToken()) != null) {
                final String word = token.getText().toLowerCase();
                if (current_distance > max_distance) {
                    //Reset
                    j = 0;
                    matcher = matchers[j];
                    current_distance = -1;
                }
                matcher.reset(word);
                matchers[0].reset(word);
                if (matcher.matches()) {
                    final boolean withIn = current_distance >= min_distance ? true : false;
                    current_distance = 0;
                    j++;
                    if (j == patterns.length) {
                        //All terms found
                        if (withIn) {
                            r.add(current);
                        }
                        break;
                    } else {
                        matcher = matchers[j];
                    }
                } else if (j > 0 && matchers[0].matches()) {
                    //First search term found: start again
                    j = 1;
                    matcher = matchers[j];
                    current_distance = 0;
                    continue;
                }
                if (-1 < current_distance) {
                    ++current_distance;
                }
            }
        }
        return r;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.functions.ExtFulltext#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("near(");
        path.dump(dumper);
        dumper.display(", ");
        searchTerm.dump(dumper);
        dumper.display(")");
    }

    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("near(");
        buf.append(path);
        buf.append(", ");
        buf.append(searchTerm);
        buf.append(")");
        return buf.toString();
    }

    public void setMaxDistance(Expression expr) {
        maxDistance = expr;
    }

    public void setMinDistance(Expression expr) {
        minDistance = expr;
    }
}
