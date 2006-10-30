/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.NodeSet;
import org.exist.storage.analysis.Tokenizer;
import org.exist.xquery.Constants;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.ExtRegexp;
import org.exist.xquery.value.Sequence;


/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public abstract class AbstractMatchFunction extends ExtRegexp {
	
	public AbstractMatchFunction(XQueryContext context, int type, FunctionSignature signature) {
		super(context, type, signature);
	}

	public abstract Sequence evalQuery(NodeSet nodes,	List terms) throws XPathException;
	
	public NodeSet mergeResults(NodeSet[] hits) {
		NodeSet result = hits[0];
		if(result != null) {
			for(int k = 1; k < hits.length; k++) {
				if(hits[k] != null)
					result = (type == Constants.FULLTEXT_AND ? 
							result.deepIntersection(hits[k]) : result.union(hits[k]));
			}
			return result;
		} else
			return NodeSet.EMPTY_SET;
	}
	
	protected List getSearchTerms(XQueryContext context,
										Sequence contextSequence)
			throws XPathException {
		String searchString = getArgument(1).eval(contextSequence)
				.getStringValue();
		List tokens = new ArrayList();
		Tokenizer tokenizer = context.getBroker().getTextEngine()
				.getTokenizer();
		tokenizer.setText(searchString);
		org.exist.storage.analysis.TextToken token;
		String word;
		while (null != (token = tokenizer.nextToken(true))) {
			word = token.getText();
			tokens.add(word);
		}
		return tokens;
	}
}
