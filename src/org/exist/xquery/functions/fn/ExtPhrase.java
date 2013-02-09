/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2009 The eXist Project
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

import org.apache.log4j.Logger;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.EXistException;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.storage.NativeTextEngine;
import org.exist.storage.analysis.TextToken;
import org.exist.storage.analysis.Tokenizer;
import org.exist.util.GlobToRegex;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Sequence;

/**
 *  phrase() function : search an exact phrase in a NodeSet
 *
 *@author     Bruno Chatel <bcha@chadocs.com> (March 30, 2005)
 */
public class ExtPhrase extends ExtFulltext {
    private static final Logger logger = Logger.getLogger(ExtPhrase.class);

    /**
     * 
     * @param context
     */
    public ExtPhrase(XQueryContext context) {
        super(context, Constants.FULLTEXT_AND);
    }

    /**
     * 
     * @param searchArg
     * @param nodes 
     */
    public Sequence evalQuery(String searchArg,	NodeSet nodes) throws XPathException {
        String[] terms;
        try {
            terms = getSearchTerms(searchArg);
        } catch (final EXistException e) {
            throw new XPathException(e.getMessage());
        }
        final NodeSet hits = processQuery(terms, nodes);
        if (hits == null)
            {return Sequence.EMPTY_SEQUENCE;}
        boolean hasWildcards = false;
        for (int i = 0; i < terms.length; i++) {
            hasWildcards |=	NativeTextEngine.containsWildcards(terms[i]);
        }
        return hasWildcards	? patternMatch(context, terms, hits) : exactMatch(context, terms, hits);
    }

    /**
     * 
     * @param context
     * @param result
     */
    private Sequence exactMatch(XQueryContext context, String[] terms, NodeSet result) {
        TextToken token;
        final NodeSet r = new ExtArrayNodeSet();
        final Tokenizer tok = context.getBroker().getTextEngine().getTokenizer();
        //Define search phrase for matches
        String matchTerm = "";
        for (int k = 0; k < terms.length ; k++) {
            matchTerm = matchTerm + terms[k];
            if (k != terms.length - 1)
                {matchTerm = matchTerm + "\\W*";} 
        }
        //Iterate on results
        for (final NodeProxy current : result) {
            final Vector<NodeId> matchNodeIDs = new Vector<NodeId>();
            //Get first match
            Match nextMatch = current.getMatches();
            //Remove previously found matches on current
            current.setMatches(null);
            //Iterate on attach matches, with unicity of related nodeproxy gid
            String term;
            while(nextMatch != null) {
                final NodeId nodeId= nextMatch.getNodeId();
                //If current node id has not been previously processed
                if (!matchNodeIDs.contains(nodeId)) {
                    final NodeProxy mcurrent = new NodeProxy(current.getDocument(), nodeId);
                    Match match = null;
                    int firstOffset = -1;
                    matchNodeIDs.add(nodeId);
                    final String value = mcurrent.getNodeValue();
                    tok.setText(value);
                    int j = 0;
                    if (j < terms.length)
                        {term = terms[j];}
                    else
                        {break;}
                    int frequency = 0;
                    while ((token = tok.nextToken()) != null) {
                        final String word = token.getText().toLowerCase();
                        if (word.equalsIgnoreCase(term)) {
                            j++;
                            if (j == terms.length) {
                                //All terms found
                                if (match == null)
                                    {match = nextMatch.createInstance(getExpressionId(),
                                        nodeId, matchTerm);}
                                if (firstOffset < 0)
                                    {firstOffset = token.startOffset();}
                                match.addOffset(firstOffset, token.endOffset() - firstOffset);
                                frequency++;
                                //Start again on fist term
                                j = 0;
                                term = terms[j];
                                continue;
                            } else {
                                term = terms[j];
                                if (firstOffset < 0)
                                    {firstOffset = token.startOffset();}
                            }
                        } else if (j > 0 && word.equalsIgnoreCase(terms[0])) {
                            //First search term found: start again
                            j = 1;
                            term = terms[j];
                            firstOffset = token.startOffset();
                            continue;
                        } else {
                            //Reset
                            j = 0;
                            firstOffset = -1;
                            term = terms[j];
                        }
                    }
                    //If phrase found
                    if (frequency != 0) {
                        //Add new match to current
                        current.addMatch(match);
                        //Add current to result
                        r.add(current);
                        //Reset frequency
                        frequency=0;
                    }
                }
                //Process next match
                nextMatch = nextMatch.getNextMatch();
            }
        }
        return r;
    }

    /**
     * 
     * @param context
     * @param result
     * 
     */
    private Sequence patternMatch(XQueryContext context, String[] terms, NodeSet result) {
        //Generate list of search term patterns
        final Pattern patterns[] = new Pattern[terms.length];
        final Matcher matchers[] = new Matcher[terms.length];
        for (int i = 0; i < patterns.length; i++) {
            try {
                patterns[i] = Pattern.compile(GlobToRegex.globToRegexp(terms[i]), 
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                matchers[i] = patterns[i].matcher("");
            } catch (final PatternSyntaxException e) {
                logger.error("malformed pattern" + e.getMessage());
                return Sequence.EMPTY_SEQUENCE;
            }
        }
        //Walk through hits 
        final ExtArrayNodeSet r = new ExtArrayNodeSet();
        final Tokenizer tok = context.getBroker().getTextEngine().getTokenizer();
        Matcher matcher;
        for (final NodeProxy current : result) {
            Match nextMatch;
            final Vector<NodeId> matchGid = new Vector<NodeId>();
            //Get first match
            nextMatch = current.getMatches();
            //Remove previously found matches on current
            current.setMatches(null);
            //Iterate on attach matches, with unicity of related nodeproxy gid
            while (nextMatch != null) {
                final Hashtable<String, Match> matchTable = new Hashtable<String, Match>();
                final NodeId nodeId = nextMatch.getNodeId(); 
                //If current node id has not been previously processed
                if (!matchGid.contains(nodeId)) {
                    final NodeProxy mcurrent = new NodeProxy(current.getDocument(), nodeId);
                    //Add it in node id array
                    matchGid.add(nodeId);
                    final String value = mcurrent.getNodeValue();
                    tok.setText(value);
                    int j = 0;
                    if (j < patterns.length) {
                        matcher = matchers[j];
                    } else
                        {break;}
                    String matchTerm = null;
                    TextToken token;
                    while ((token = tok.nextToken()) != null) {
                        String word = token.getText().toLowerCase();
                        matcher.reset(word);
                        matchers[0].reset(word);
                        if (matcher.matches()) {
                            j++;
                            if (matchTerm == null)
                                {matchTerm=word;}
                            else
                                {matchTerm = matchTerm + "\\W*" + word;}
                            if (j == patterns.length) {
                                //All terms found
                                if (matchTable.containsKey(matchTerm)) {
                                    //Previously found matchTerm
                                    final Match match = matchTable.get(matchTerm);
                                    match.addOffset(token.startOffset(), matchTerm.length());
                                } else {
                                    final Match match = nextMatch.createInstance(getExpressionId(),
                                        nodeId, matchTerm);
                                    match.addOffset(token.startOffset(), matchTerm.length());
                                    matchTable.put(matchTerm,match);
                                }
                                //Start again on fist term
                                j = 0;
                                matcher = matchers[j];
                                matchTerm = null;
                                continue;
                            } else {
                                matcher = matchers[j];
                            }
                        } else if (j > 0 && matchers[0].matches()) {
                            //First search term found: start again
                            j = 1;
                            //Pattern term = patterns[j];
                            matcher = matchers[j];
                            matchTerm = word;
                            continue;
                        } else {
                            //Reset
                            j = 0;
                            matcher = matchers[j];
                            matchTerm = null;
                            continue;
                        }
                    }
                    //One or more match found
                    if (matchTable.size() != 0) {
                        final Enumeration<Match> eMatch = matchTable.elements();
                        while (eMatch.hasMoreElements()){
                            final Match match = eMatch.nextElement();
                            current.addMatch(match);
                        }
                        //Add current to result
                        r.add(current);
                    }
                }
                //Process next match
                nextMatch = nextMatch.getNextMatch();
            }
        }
        return r;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.functions.ExtFulltext#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("phrase(");
        path.dump(dumper);
        dumper.display(", ");
        searchTerm.dump(dumper);
        dumper.display(")");
    }

    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("phrase(");
        buf.append(path);
        buf.append(", ");
        buf.append(searchTerm);
        buf.append(")");
        return buf.toString();
    }

}
