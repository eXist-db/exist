/*
 *  eXist SQL Module Extension GetConnectionFunction
 *  Copyright (C) 2008-09 Adam Retter <adam@exist-db.org>
 *  www.adamretter.co.uk
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

package org.exist.xquery.modules.jndi;


import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist JNDI Module Extension SearchFunction
 * 
 * Search a JNDI Directory
 * 
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @serial 2008-12-02
 * @version 1.0
 * 
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 *      org.exist.xquery.FunctionSignature)
 */

public class SearchFunction extends BasicFunction 
{
	protected static final Logger logger = Logger.getLogger(SearchFunction.class);
	
	public final static String DSML_NAMESPACE = "http://www.dsml.org/DSML";

	public final static String DSML_PREFIX = "dsml";

	public final static FunctionSignature[] signatures = {
			
			new FunctionSignature(
					new QName( "search", JNDIModule.NAMESPACE_URI, JNDIModule.PREFIX ),
							"Searches a JNDI Directory by attributes.",
					new SequenceType[] {
						new FunctionParameterSequenceType( "directory-context", Type.INTEGER, Cardinality.EXACTLY_ONE, "The directory context handle from a jndi:get-dir-context() call" ), 
						new FunctionParameterSequenceType( "dn", Type.STRING, Cardinality.EXACTLY_ONE, "The Distinguished Name" ), 
						new FunctionParameterSequenceType( "search-attributes", Type.ELEMENT, Cardinality.EXACTLY_ONE, "The search attributes in the form <attributes><attribute name=\"\" value=\"\"/></attributes>." ) 
					},
					new FunctionReturnSequenceType( Type.NODE, Cardinality.ZERO_OR_ONE, "the search results in DSML format" ) ),
			
			new FunctionSignature(
					new QName( "search", JNDIModule.NAMESPACE_URI, JNDIModule.PREFIX ),
							"Searches a JNDI Directory by filter.",
					new SequenceType[] {
						new FunctionParameterSequenceType( "directory-context", Type.INTEGER, Cardinality.EXACTLY_ONE, "The directory context handle from a jndi:get-dir-context() call" ), 
						new FunctionParameterSequenceType( "dn", Type.STRING, Cardinality.EXACTLY_ONE, "The Distinguished Name" ), 
						new FunctionParameterSequenceType( "filter", Type.STRING, Cardinality.EXACTLY_ONE, "The filter.  The format and interpretation of filter follows RFC 2254 with the following interpretations for \'attr\' and \'value\'  mentioned in the RFC. \'attr\' is the attribute's identifier. \'value\' is the string represention the attribute's value. The translation of this string representation into the attribute's value is directory-specific. " ),
						new FunctionParameterSequenceType( "scope", Type.STRING, Cardinality.EXACTLY_ONE, "The scope, which has a value of 'object', 'onelevel' or 'subtree'" ) 
					},
					new FunctionReturnSequenceType( Type.NODE, Cardinality.ZERO_OR_ONE, "the search results in DSML format" ) )
			};

	/**
	 * SearchFunction Constructor
	 * 
	 * @param context 	The Context of the calling XQuery
	 */
	
	public SearchFunction( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}

	
	/**
	 * evaluate the call to the xquery search() function, it is really
	 * the main entry point of this class
	 * 
	 * @param args				arguments from the get-connection() function call
	 * @param contextSequence 	the Context Sequence to operate on (not used here internally!)
	 * @return 					A xs:long representing a handle to the connection
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
	 *      org.exist.xquery.value.Sequence)
	 */
	
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		Sequence    xmlResult     = Sequence.EMPTY_SEQUENCE;
		
		// Was context handle or DN specified?
		if( !( args[0].isEmpty() ) && !( args[1].isEmpty() ) ) {
			
			String dn = args[1].getStringValue();
			
			try {
				long ctxID = ((IntegerValue)args[0].itemAt(0)).getLong();
				
				DirContext ctx = (DirContext)JNDIModule.retrieveJNDIContext( context, ctxID );
				
				if( ctx == null ) {
					logger.error( "jndi:search() - Invalid JNDI context handle provided: " + ctxID );
				} else {
					NamingEnumeration<SearchResult> results = null;
					
					if( args.length == 3 ) {                									// Attributes search			
						BasicAttributes attributes = JNDIModule.parseAttributes( args[ 2 ] );
						
						results = ctx.search( dn, attributes );
					} else {                													// Filter search
						int 	scopeCode 	= 0;
						
						String 	filter 		= args[2].getStringValue();
						String 	scope 		= args[3].getStringValue();
						
						if(  scope.equalsIgnoreCase( "object" ) ) {
							scopeCode = 0;
						} else if(  scope.equalsIgnoreCase( "onelevel" ) ) {
							scopeCode = 1;
						} else if(  scope.equalsIgnoreCase( "subtree" ) ) {
							scopeCode = 2;
						}
						
						results = ctx.search( dn, filter, new SearchControls( scopeCode, 0, 0, null, false, false ) );
					}
					
					xmlResult = renderSearchResultsAsDSML( results, dn );
				}
			}
			catch( NameNotFoundException nf ) {
				logger.warn( "jndi:search() Not found for dn [" + dn + "]", nf );
			}
			catch( NamingException ne ) {
				logger.error( "jndi:search() Search failed for dn [" + dn + "]: ", ne );
				throw( new XPathException( this, "jndi:search() Search failed for dn [" + dn + "]: " + ne ) );
			}
		}
		
		return( xmlResult );
	}

	
	
	private Sequence renderSearchResultsAsDSML( NamingEnumeration results, String dn ) throws NamingException
	{
		Sequence    xmlResult     = Sequence.EMPTY_SEQUENCE;
		
		MemTreeBuilder builder = context.getDocumentBuilder();
					
		builder.startDocument();
    	builder.startElement( new QName( "dsml", DSML_NAMESPACE, DSML_PREFIX ), null );
		builder.addAttribute( new QName( "dn", null, null ), dn );
		builder.startElement( new QName( "directory-entries", DSML_NAMESPACE, DSML_PREFIX ), null );
		
		while( results.hasMore() ) {
			SearchResult result = (SearchResult)results.next();
			
			builder.startElement( new QName( "entry", DSML_NAMESPACE, DSML_PREFIX ), null );
			builder.addAttribute( new QName( "dn", null, null ), result.getName() );

			// Handle objectClass attributes
			Attribute ocattr = result.getAttributes().get( "objectClass" );
			
			if( ocattr != null ) {
				builder.startElement( new QName( "objectclass", DSML_NAMESPACE, DSML_PREFIX ), null );
				
				for( int i = 0; i < ocattr.size(); i++ ) {
						Object value = ocattr.get( i );
						
						builder.startElement( new QName( "oc-value", DSML_NAMESPACE, DSML_PREFIX ), null );
						builder.characters( value.toString() );
						builder.endElement();
					}
				
				builder.endElement();
			}
			
			NamingEnumeration attrs = result.getAttributes().getAll();
			
			// Handle all other attributes
			while( attrs.hasMore() ) {
				Attribute attr = (Attribute)attrs.next();
				
				String name = attr.getID();
				
				if( !name.equals( "objectClass" ) ) {
				
					builder.startElement( new QName( "attr", DSML_NAMESPACE, DSML_PREFIX ), null );
					builder.addAttribute( new QName( "name", null, null ), name );
					
					for( int i = 0; i < attr.size(); i++ ) {
						Object value = attr.get( i );
						
						builder.startElement( new QName( "value", DSML_NAMESPACE, DSML_PREFIX ), null );
						if( name.equals( "userPassword" ) ) {
							builder.characters( new String( (byte[])value ) );
						} else {
							builder.characters( value.toString() );
						}
						builder.endElement();
					}
					
					builder.endElement();
				}
			}
			
			builder.endElement();
		}
		
		builder.endElement();
		builder.endElement();
    
    	xmlResult = (NodeValue)builder.getDocument().getDocumentElement();
		
		return( xmlResult );
	}
}
