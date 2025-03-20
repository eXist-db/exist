/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.jndi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * eXist JNDI Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows queries
 * against JNDI sources, including LDAP, returning an XML representation of the results.
 * 
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author ljo
 * @serial 2008-12-02
 * @version 1.0
 * 
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[], java.util.Map) 
 */
public class JNDIModule extends AbstractInternalModule 
{

	protected final static Logger LOG = LogManager.getLogger( JNDIModule.class );

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/jndi";

	public final static String PREFIX = "jndi";
    public final static String INCLUSION_DATE = "2008-12-04";
    public final static String RELEASED_IN_VERSION = "eXist-1.4";

	private final static FunctionDef[] functions = {
			new FunctionDef( GetDirContextFunction.signatures[0], 	GetDirContextFunction.class ),
			new FunctionDef( CloseContextFunction.signatures[0], 	CloseContextFunction.class ),
			new FunctionDef( SearchFunction.signatures[0], 			SearchFunction.class ),
			new FunctionDef( SearchFunction.signatures[1], 			SearchFunction.class ),
			new FunctionDef( CreateFunction.signatures[0], 			CreateFunction.class ),
			new FunctionDef( DeleteFunction.signatures[0], 			DeleteFunction.class ),
			new FunctionDef( RenameFunction.signatures[0], 			RenameFunction.class ),
			new FunctionDef( ModifyFunction.signatures[0], 			ModifyFunction.class )
	};

	public final static String 	JNDICONTEXTS_VARIABLE 	= "_eXist_jndi_contexts";
		
	private static long 		currentContextID 		= System.currentTimeMillis();


	public JNDIModule(Map<String, List<?>> parameters)
	{
		super( functions, parameters );
	}

	public String getNamespaceURI() 
	{
		return( NAMESPACE_URI );
	}

	public String getDefaultPrefix() 
	{
		return( PREFIX );
	}

	public String getDescription() 
	{
		return( "A module for performing JNDI queries against Directories, returning XML representations of the results." );
	}
	
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }

	/**
	 * Retrieves a previously stored Connection from the Context of an XQuery
	 * 
	 * @param context 		The Context of the XQuery containing the JNDI Context
	 * @param ctxID 		The ID of the JNDI Context to retrieve from the Context of the XQuery
	 *
	 * @return the JNDI context
	 */
	public final static Context retrieveJNDIContext( XQueryContext context, long ctxID ) 
	{
		Context jndiContext = null;
		
		// get the existing connections map from the context
		HashMap contexts = (HashMap)context.getAttribute( JNDIModule.JNDICONTEXTS_VARIABLE );
		
		if( contexts != null ) {	
		 	jndiContext = (Context)contexts.get(ctxID);
		}
		
		return( jndiContext );
	}

	
	/**
	 * Stores a Connection in the Context of an XQuery
	 * 
	 * @param context 			The Context of the XQuery to store the Connection in
	 * @param jndiContext		The connection to store
	 * 
	 * @return 			A unique ID representing the connection
	 */
	public final static synchronized long storeJNDIContext( XQueryContext context, Context jndiContext ) 
	{
		// get the existing connections map from the context
		HashMap contexts = (HashMap)context.getAttribute( JNDIModule.JNDICONTEXTS_VARIABLE );
		
		if( contexts == null ) {
			// if there is no connections map, create a new one
			contexts = new HashMap();
		}

		// get an id for the jndiContext
		long ctxID = getID();

		// place the connection in the connections map
		contexts.put(ctxID, jndiContext );

		// store the updated connections map back in the context
		context.setAttribute( JNDIModule.JNDICONTEXTS_VARIABLE, contexts );

		return( ctxID );
	}
	
	
	/**
	 * Closes a specified JNDI Context for the specified XQueryContext
	 * 
	 * @param context   	The context to close JNDI Contexts for
	 * @param ctxID 		The ID of the JNDI Context to retrieve from the Context of the XQuery
	 */
	public final static void closeJNDIContext( XQueryContext context, long ctxID ) 
	{
		// get the existing connections map from the context
		HashMap contexts = (HashMap)context.getAttribute( JNDIModule.JNDICONTEXTS_VARIABLE );
		
		closeJNDIContext( context, ctxID, contexts );
		
		// update the context
		context.setAttribute( JNDIModule.JNDICONTEXTS_VARIABLE, contexts );
	}
	
	
	/**
	 * Closes a specified JNDI Context for the specified XQueryContext
	 * 
	 * @param context 	The context to close JNDI Contexts for
	 * @param ctxID 			The ID of the JNDI Context to retrieve from the Context of the XQuery
	 * @param contexts 			The contexts hashmap
	 */
	private final static void closeJNDIContext( XQueryContext context, long ctxID, HashMap contexts ) 
	{
		Context ctx = null;

		if( contexts != null ) {	
			ctx = (Context)contexts.get(ctxID);
			
			if( ctx != null ) {
				try {
					// close the connection
					ctx.close();
					
					// remove it from the connections map
					contexts.remove( ctxID );				
				} 
				catch( NamingException ne ) {
					LOG.error( "Unable to close JNDI Context", ne );
				}
			}
		}
	}
	

	/**
	 * Closes all the open JNDI Contexts for the specified XQueryContext
	 * 
	 * @param xqueryContext 	The context to close JNDI Contexts for
	 */
	private final static void closeAllJNDIContexts( XQueryContext xqueryContext ) 
	{
		// get the existing connections map from the context
		HashMap contexts = (HashMap)xqueryContext.getAttribute( JNDIModule.JNDICONTEXTS_VARIABLE );
		
		if( contexts != null ) {
			// iterate over each connection
			Set keys = contexts.keySet();
			for (Object key : keys) {
				// get the connection
				Long ctxID = (Long) key;

				closeJNDIContext(xqueryContext, ctxID, contexts);
			}

			// update the context
			xqueryContext.setAttribute( JNDIModule.JNDICONTEXTS_VARIABLE, contexts );
		}
	}

	
	/**
	 * Returns a Unique ID based on the System Time
	 * 
	 * @return The Unique ID
	 */
	private static synchronized long getID() 
	{
		return currentContextID++;
	}

	
	/**
	 * Resets the Module Context and closes any open JNDI Contexts for the
	 * XQueryContext
	 * 
	 * @param xqueryContext		The XQueryContext
	 */
	public void reset( XQueryContext xqueryContext, boolean keepGlobals )
	{
		// reset the module context
		super.reset( xqueryContext, keepGlobals );

		// close any open JNDI Contexts
		closeAllJNDIContexts( xqueryContext );
	}
	
	
	/**
	 * Parses attributes into a JNDI BasicAttributes object
	 * 
	 * @param arg				The attributes as a sequence of nodes
	 * @return 					The BasicAttributes object
	 */
	protected static BasicAttributes parseAttributes( Sequence arg ) 
	{
		BasicAttributes attributes = new BasicAttributes();
		
		if( !( arg.isEmpty() ) ) {
		
			Node container = ( (NodeValue)arg.itemAt( 0 ) ).getNode();
			
			if( container != null && container.getNodeType() == Node.ELEMENT_NODE ) {
				
				NodeList attrs = ((Element)container).getElementsByTagName( "attribute" );
	
				for( int i = 0; i < attrs.getLength(); i++ ) {
					Element attr = ((Element)attrs.item( i ));
	
					String name  	= attr.getAttribute( "name" );
					String value 	= attr.getAttribute( "value" );
					String ordered 	= attr.getAttribute( "ordered" );
	
					if( name != null && value != null ) {
						Attribute existingAttr = attributes.get( name );
						
						if( existingAttr != null ) {
							existingAttr.add( value );
						} else {
							attributes.put( new BasicAttribute( name, value, ordered != null && "true".equalsIgnoreCase(ordered) ) );
						}
					} else {
						LOG.warn( "Name or value attribute missing for attribute" );
					}
				}
			}
		}
		
		return( attributes );
	}
}
