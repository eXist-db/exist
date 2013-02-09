/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class FunIRIToURI extends Function {

	protected static final String FUNCTION_DESCRIPTION =

		"This function converts an xs:string containing an " +
		"IRI into a URI according to the rules spelled out " +
		"in Section 3.1 of [RFC 3987]. It is idempotent but " + 
		"not invertible.\n\n" +
		"If $iri contains a character that is invalid in an " +
		"IRI, such as the space character (see note below), " + 
		"the invalid character is replaced by its percent-encoded " +
		"form as described in [RFC 3986] before the conversion is performed.\n\n" +
		"If $iri is the empty sequence, returns the zero-length string.\n\n" +
		"Since [RFC 3986] recommends that, for consistency, " + 
		"URI producers and normalizers should use uppercase " +
		"hexadecimal digits for all percent-encodings, this " +
		"function must always generate hexadecimal values " +
		"using the upper-case letters A-F.\n\n" +
		"Notes:\n\n" +

		"This function does not check whether $iri is a legal " +
		"IRI. It treats it as an xs:string and operates on " + 
		"the characters in the xs:string.\n\n" +

		"The following printable ASCII characters are invalid " +
		"in an IRI: \"<\", \">\", \" \" \" (double quote), " +
		"space, \"{\", \"}\", \"|\", \"\\\", \"^\", and \"`\". " +
		"Since these characters should not appear in an IRI, " +
		"if they do appear in $iri they will be percent-encoded. " +
		"In addition, characters outside the range x20-x126 " +
		"will be percent-encoded because they are invalid in a URI.\n\n" +

		"Since this function does not escape the PERCENT SIGN " +
		"\"%\" and this character is not allowed in data within " +
		"a URI, users wishing to convert character strings, " +
		"such as file names, that include \"%\" to a URI " +
		"should manually escape \"%\" by replacing it with \"%25\".";

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("iri-to-uri", Function.BUILTIN_FUNCTION_NS),
			FUNCTION_DESCRIPTION,
			new SequenceType[] { new FunctionParameterSequenceType("iri", Type.STRING, Cardinality.ZERO_OR_ONE, "The IRI") },
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the URI"));
	
	public FunIRIToURI(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
		if(contextItem != null)
			{contextSequence = contextItem.toSequence();}
        
        Sequence result;
		final Sequence seq = getArgument(0).eval(contextSequence);
		if(seq.isEmpty())
			{result = StringValue.EMPTY_STRING;}
        else {
    		String value; 
   			value = URIUtils.iriToURI(seq.getStringValue());
    		result = new StringValue(value);
        }
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;
        
	}

}
