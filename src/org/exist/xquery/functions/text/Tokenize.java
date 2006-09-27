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
 *  $Id: FuzzyIndexTerms.java 3063 2006-04-05 20:49:44Z brihaye $
 */
package org.exist.xquery.functions.text;


import org.exist.dom.QName;
import org.exist.storage.analysis.SimpleTokenizer;
import org.exist.storage.analysis.TextToken;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;



/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class Tokenize extends BasicFunction {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("make-token", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"split a string into a token",
			new SequenceType[]{
					new SequenceType(Type.STRING, Cardinality.ONE)},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));
	
	public Tokenize(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		if(args[0].isEmpty())
			return Sequence.EMPTY_SEQUENCE;
		
		ValueSequence result = new ValueSequence();
		SimpleTokenizer tokenizer = new SimpleTokenizer();
		tokenizer.setText(args[0].getStringValue());
		TextToken token = tokenizer.nextToken(false);
		while(token != null && token.getType() != TextToken.EOF) {
			result.add(new StringValue(token.getText()));
			token = tokenizer.nextToken(false);
		}
		return result;
	}
}
