/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xquery.modules.lucene;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.indexing.lucene.LuceneMatch;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.exist.dom.QName;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.Match;
import org.exist.indexing.lucene.LuceneIndex;

public class Score extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(Score.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("score", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Returns a computed relevance score for the given node. The score is the sum of all " +
            "relevance scores provided by Lucene for the node and its descendants. In general, the score " +
            "will be a number between 0.0 and 1.0 if the query had $node as context. If the query targeted " +
            "multiple descendants of $node (e.g. 'title' and 'author' within a 'book'), the score will be the " +
            "sum of all sub-scores and may thus be greater than 1.",
            new SequenceType[] {
                new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE,
                    "the context node")
            },
            new FunctionReturnSequenceType(Type.FLOAT, Cardinality.ZERO_OR_MORE,
                "sum of all relevance scores provided by Lucene for all matches below the given context node")
        );

    public Score(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	
        NodeValue nodeValue = (NodeValue) args[0].itemAt(0);
        if (nodeValue.getImplementationType() != NodeValue.PERSISTENT_NODE) {
            return Sequence.EMPTY_SEQUENCE;
        }
        NodeProxy proxy = (NodeProxy) nodeValue;
        Match match = proxy.getMatches();
        float score = 0.0f;
        while (match != null) {
            if (match.getIndexId().equals(LuceneIndex.ID)) {
                float currentScore = ((LuceneMatch)match).getScore();
                score += currentScore;
            }
            match = match.getNextMatch();
        }
        return new FloatValue(score);
    }
}
