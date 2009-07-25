/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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
package org.exist.xquery.modules.lucene;

import org.apache.log4j.Logger;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.FloatValue;
import org.exist.dom.QName;
import org.exist.dom.NodeProxy;
import org.exist.dom.Match;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;

public class Score extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(Score.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("score", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "INSERT DESCRIPTION HERE",
            new SequenceType[] {
                new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE, null)
            },
            new FunctionParameterSequenceType("result", Type.FLOAT, Cardinality.ZERO_OR_MORE, "the score")
        );

    public Score(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	logger.info("Entering " + LuceneModule.PREFIX + ":" + getName().getLocalName());
    	
        NodeValue nodeValue = (NodeValue) args[0].itemAt(0);
        if (nodeValue.getImplementationType() != NodeValue.PERSISTENT_NODE) {
        	logger.info("Exiting " + LuceneModule.PREFIX + ":" + getName().getLocalName());
            return Sequence.EMPTY_SEQUENCE;
        }
        NodeProxy proxy = (NodeProxy) nodeValue;
        Match match = proxy.getMatches();
        float score = 0.0f;
        while (match != null) {
            if (match.getIndexId() == LuceneIndex.ID) {
                float currentScore = ((LuceneIndexWorker.LuceneMatch)match).getScore();
                score += currentScore;
            }
            match = match.getNextMatch();
        }
    	logger.info("Exiting " + LuceneModule.PREFIX + ":" + getName().getLocalName());
        return new FloatValue(score);
    }
}
