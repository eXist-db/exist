/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.text;

import java.util.List;

import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.TermMatcher;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FuzzyMatchAll extends AbstractMatchFunction {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("fuzzy-match-all", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Fuzzy keyword search, which compares strings based on the Levenshtein distance " +
			"(or edit distance). The function tries to match each of the keywords specified in the " +
			"keyword string against the string value of each item in the sequence $source.",
			new SequenceType[]{
					new FunctionParameterSequenceType("source", Type.NODE, Cardinality.ZERO_OR_MORE, "The source"),
					new FunctionParameterSequenceType("keyword", Type.STRING, Cardinality.EXACTLY_ONE, "The keyword string")},
			new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the sequence of nodes that match the keywords"),
			true);

	public FuzzyMatchAll(XQueryContext context) {
		super(context, Constants.FULLTEXT_AND, signature);
	}

	public FuzzyMatchAll(XQueryContext context, int type, FunctionSignature signature) {
		super(context, type, signature);
	}
	
	public Sequence evalQuery(NodeSet nodes,
								List terms) throws XPathException {
		if (terms == null || terms.size() == 0)
			return Sequence.EMPTY_SEQUENCE; // no search terms
		double threshold = 0.65;
		if (getArgumentCount() == 3) {
			Sequence thresOpt = getArgument(2).eval(nodes);
			//TODO : get rid of getLength()
			if(!thresOpt.hasOne())
				throw new XPathException(this, "third argument to " + getName() +
						"should be a single double value");
			threshold = ((DoubleValue) thresOpt.convertTo(Type.DOUBLE)).getDouble();
		}
		NodeSet hits[] = new NodeSet[terms.size()];
		String term;
		TermMatcher matcher;
		for (int k = 0; k < terms.size(); k++) {
		    term = (String)terms.get(k);
			if(term.length() == 0)
				hits[k] = null;
			else {
				matcher = new FuzzyMatcher(term, threshold);
				hits[k] =
					context.getBroker().getTextEngine().getNodes(
					    context,
						nodes.getDocumentSet(),
						nodes, NodeSet.ANCESTOR, null,
						matcher, term.substring(0, 1));
			}
		}
		return mergeResults(hits);
	}
}
