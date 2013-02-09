/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2006-2009 The eXist Project
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

import java.net.URI;
import java.net.URISyntaxException;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements the fn:resolve-uri() function.
 *
 * @author perig
 *
 */


public class FunResolveURI extends Function {
	
	protected static final String FUNCTION_DESCRIPTION_1_PARAM = 
        "Resolves $relative against the value of " +
		"the base-uri property from the static context ";
	protected static final String FUNCTION_DESCRIPTION_2_PARAM = 
        "Resolves $relative against $base ";

	protected static final String FUNCTION_DESCRIPTION_COMMON = 
        "using an algorithm such as the ones described " +
		"in [RFC 2396] or [RFC 3986], and the resulting absolute " +
		"URI reference is returned. An error may be raised " +
		"[err:FORG0009] in the resolution process.\n\n" +
		"If $relative is an absolute URI reference, it is returned " +
		"unchanged.\n\n" +
		"If $relative or $base is not a valid xs:anyURI an error " +
		"is raised [err:FORG0002].\n\n" +
		"If $relative is the empty sequence, the empty sequence is returned.";
	
	protected static final FunctionParameterSequenceType RELATIVE_ARG = new FunctionParameterSequenceType("relative", Type.STRING, Cardinality.ZERO_OR_ONE, "The relative URI");
	protected static final FunctionParameterSequenceType BASE_ARG = new FunctionParameterSequenceType("base", Type.STRING, Cardinality.ONE, "The base URI");
	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ZERO_OR_ONE, "the absolute URI");
	
    public final static FunctionSignature signatures [] = {
    	new FunctionSignature(
    		      new QName("resolve-uri", Function.BUILTIN_FUNCTION_NS),
    		      FUNCTION_DESCRIPTION_1_PARAM + FUNCTION_DESCRIPTION_COMMON,
	      new SequenceType[] { RELATIVE_ARG },
	      RETURN_TYPE
	    ),
	    new FunctionSignature (
	  	      new QName("resolve-uri", Function.BUILTIN_FUNCTION_NS),
		      FUNCTION_DESCRIPTION_2_PARAM + FUNCTION_DESCRIPTION_COMMON,
		      new SequenceType[] { RELATIVE_ARG, BASE_ARG },
		      RETURN_TYPE
	  	 ),
	};

    public FunResolveURI(XQueryContext context, FunctionSignature signature) {
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
        
		if (contextItem != null)
		    {contextSequence = contextItem.toSequence();}
		
		AnyURIValue base;		
		if (getArgumentCount() == 1) {
			if (!context.isBaseURIDeclared())
				{throw new XPathException(this, ErrorCodes.FONS0005, 
					"base URI of the static context has not been assigned a value.");}
			base = context.getBaseURI();
		} else {
			try {
				final Item item = getArgument(1).eval(contextSequence).itemAt(0).convertTo(Type.ANY_URI);
				base = (AnyURIValue)item;
			} catch (final XPathException e) {
	        	throw new XPathException(this, ErrorCodes.FORG0002, "invalid argument to fn:resolve-uri(): " + e.getMessage(), null, e);
			}
		}
		
		Sequence result;

		final Sequence seq = getArgument(0).eval(contextSequence);
		if (seq.isEmpty()) {			
			result = Sequence.EMPTY_SEQUENCE;
		} else {
			AnyURIValue relative;
			try {
				final Item item = seq.itemAt(0).convertTo(Type.ANY_URI);
				relative = (AnyURIValue)item;
			} catch (final XPathException e) {				
	        	throw new XPathException(this, ErrorCodes.FORG0002, "invalid argument to fn:resolve-uri(): " + e.getMessage(), seq, e);
			}			
			URI relativeURI;
			URI baseURI;
			try {
				relativeURI = new URI(relative.getStringValue());
				baseURI = new URI(base.getStringValue() );
			} catch (final URISyntaxException e) {
				throw new XPathException(this, ErrorCodes.FORG0009, "unable to resolve a relative URI against a base URI in fn:resolve-uri(): " + e.getMessage(), null, e);
			}
			if (relativeURI.isAbsolute()) {
				result = relative;
            } else {
				result = new AnyURIValue(baseURI.resolve(relativeURI));
            }
        }
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 

        return result;
    }

}
