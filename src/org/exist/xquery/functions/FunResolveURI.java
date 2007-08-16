/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2000-2007 The eXist Project
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
package org.exist.xquery.functions;

import java.net.URI;
import java.net.URISyntaxException;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunResolveURI extends Function {
	
    public final static FunctionSignature signatures [] = {
    	new FunctionSignature(
    		      new QName("resolve-uri", Function.BUILTIN_FUNCTION_NS),
    		      "The purpose of this function is to enable a relative URI $a to be resolved against the static context's base URI.",
	      new SequenceType[] {
	    	  new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
	      },
	      new SequenceType(Type.ANY_URI, Cardinality.ZERO_OR_ONE)
	    ),
	    new FunctionSignature (
	  	      new QName("resolve-uri", Function.BUILTIN_FUNCTION_NS),
		      "The purpose of this function is to enable a relative URI $a to be resolved against the absolute URI $b. If $a is the empty sequence, the empty sequence is returned.",
		      new SequenceType[] {
		    	  new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
		    	  new SequenceType(Type.STRING, Cardinality.ONE)
			  },
		      new SequenceType(Type.ANY_URI, Cardinality.ZERO_OR_ONE)),
	};

    public FunResolveURI(XQueryContext context, FunctionSignature signature) {
	super(context, signature);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		if (contextItem != null)
		    contextSequence = contextItem.toSequence();
		
		AnyURIValue base;		
		if (getArgumentCount() == 1) {
			if (!context.isBaseURIDeclared())
				throw new XPathException("err:FONS0005: base URI of the static context has not been assigned a value.");
			base = context.getBaseURI();
		} else {
			try {
				Item item = getArgument(1).eval(contextSequence).itemAt(0).convertTo(Type.ANY_URI);
				base = (AnyURIValue)item;
			} catch (XPathException e) {
	        	throw new XPathException(getASTNode(), "err:FORG0002: invalid argument to fn:resolve-uri(): " + e.getMessage(), e);
			}
		}
		
		Sequence result;

		Sequence seq = getArgument(0).eval(contextSequence);
		if (seq.isEmpty()) {			
			result = Sequence.EMPTY_SEQUENCE;
		} else {
			AnyURIValue relative;
			try {
				Item item = seq.itemAt(0).convertTo(Type.ANY_URI);
				relative = (AnyURIValue)item;
			} catch (XPathException e) {				
	        	throw new XPathException(getASTNode(), "err:FORG0002: invalid argument to fn:resolve-uri(): " + e.getMessage(), e);
			}			
			URI relativeURI;
			URI baseURI;
			try {
				relativeURI = new URI(relative.getStringValue());
				baseURI = new URI(base.getStringValue() + "/");
			} catch (URISyntaxException e) {
				throw new XPathException(getASTNode(), "err:FORG0009: unable to resolve a relative URI against a base URI in fn:resolve-uri(): " + e.getMessage(), e);				
			}
			if (relativeURI.isAbsolute()) {
				result = relative;
            } else {
				result = new AnyURIValue(baseURI.resolve(relativeURI));
            }
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 

        return result;
    }

}
