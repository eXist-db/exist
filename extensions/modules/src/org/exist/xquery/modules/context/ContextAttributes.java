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
 *  $Id: BuiltinFunctions.java 9598 2009-07-31 05:45:57Z ixitar $
 */
package org.exist.xquery.modules.context;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.Function;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;


public class ContextAttributes extends Function 
{
	public final static String ATTRIBUTES_CONTEXTVAR 			= "_eXist_xquery_context_attributes";
		
	protected static final Logger logger = Logger.getLogger( ContextAttributes.class );

	public final static FunctionSignature signatures[] = {
		
        new FunctionSignature(
            new QName( "get-attribute", ContextModule.NAMESPACE_URI, ContextModule.PREFIX ),
            "Returns the value associated with the given name, which was stored in the XQuery" +
            "context. This function is useful for storing temporary information if you don't have " +
			"a servlet request or session, that is you're running an XQuery as a scheduled task.",
            new SequenceType[] {
                new FunctionParameterSequenceType( "name", Type.STRING, Cardinality.EXACTLY_ONE, "The attribute name" )	
            },
            new FunctionReturnSequenceType( Type.ITEM, Cardinality.ZERO_OR_MORE, "The attribute value" )
        ),
		
        new FunctionSignature(
            new QName( "set-attribute", ContextModule.NAMESPACE_URI, ContextModule.PREFIX ),
            "Set the value of an XQuery context attribute with the specified name " +
			"This function is useful for storing temporary information if you don't have " +
			"a servlet request or session, that is you're running an XQuery as a scheduled task.",
            new SequenceType[] {
               new FunctionParameterSequenceType( "name", Type.STRING, Cardinality.EXACTLY_ONE, "The attribute name" ),
			   new FunctionParameterSequenceType( "value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The value to be stored in the session by the attribute name" )
            },
            new FunctionReturnSequenceType( Type.ITEM, Cardinality.EMPTY, "Returns an empty sequence" )
        )
    };
	
	
	public ContextAttributes( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}
	
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval( Sequence contextSequence, Item contextItem ) throws XPathException 
	{
		Sequence ret = Sequence.EMPTY_SEQUENCE;
		
		if( context.getProfiler().isEnabled() ) {
			context.getProfiler().start( this );       
			context.getProfiler().message( this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName( this.getDependencies() ) );
			
			if( contextSequence != null ) {
				context.getProfiler().message( this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence );
			}
			
			if( contextItem != null ) {
				context.getProfiler().message( this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence() );
			}
		}
		
		// get attribute name parameter
		
		String attribName = getArgument( 0 ).eval( contextSequence, contextItem ).getStringValue();
		
        if( isCalledAs( "get-attribute" ) ) {
           	ret =  retrieveAttribute( context, attribName );
        } else {
			Sequence attribValue = getArgument( 1 ).eval( contextSequence, contextItem );
			
        	ret = storeAttribute( context, attribName, attribValue );
        }
		
		return( ret );
    }
	
	
	//***************************************************************************
	//*
	//*    Context Attribute Methods
	//*
	//***************************************************************************/
	
	/**
	 * Retrieves a previously stored Attribute from the Context of an XQuery
	 * 
	 * @param context 			The Context of the XQuery containing the attribute
	 * @param key	 			The key of the attribute to retrieve from the Context of the XQuery
	 */
	private Sequence retrieveAttribute( XQueryContext context, String key ) 
	{
		Sequence	attribute = Sequence.EMPTY_SEQUENCE;
		
		// get the existing attributes map from the context
		
		HashMap<String, Sequence> attributes = (HashMap)context.getXQueryContextVar( ATTRIBUTES_CONTEXTVAR );
		
		if( attributes != null ) {
			attribute = attributes.get( key );
		}
		
		return( (Sequence)attribute );
	}


	/**
	 * Stores an attribute in the Context of an XQuery
	 * 
	 * @param context 			The Context of the XQuery to store the attribute in
	 * @param key	 			The key of the attribute to store in the Context of the XQuery
	 * @param attribute 		The attribute to store
	 * 
	 * @return empty sequence
	 */
	private Sequence storeAttribute( XQueryContext context, String key, Sequence attribute ) 
	{
		synchronized( context ) {
			// get the existing attributes map from the context
			
			HashMap<String, Sequence> attributes = (HashMap)context.getXQueryContextVar( ATTRIBUTES_CONTEXTVAR );
			
			if( attributes == null ) {
				// if there is no attributes map, create a new one
				attributes = new HashMap<String, Sequence>();
			}
	
			// place the attribute in the attributes map
			attributes.put( key, attribute );
			
			// store the updated sessions map back in the context
			context.setXQueryContextVar( ATTRIBUTES_CONTEXTVAR, attributes );
		}

		return( Sequence.EMPTY_SEQUENCE );
	}


}
