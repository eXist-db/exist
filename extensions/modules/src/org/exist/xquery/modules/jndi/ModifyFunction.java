/*
 *  eXist SQL Module Extension GetConnectionFunction
 *  Copyright (C) 2008 Adam Retter <adam@exist-db.org>
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
 *  $Id: GetConnectionFunction.java 4126 2006-09-18 21:20:17 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.jndi;


import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NameNotFoundException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchResult;

import java.util.Properties;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * eXist JNDI Module Extension ModifyFunction
 * 
 * Modify a JNDI Directory entry
 * 
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @serial 2008-12-02
 * @version 1.0
 * 
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 *      org.exist.xquery.FunctionSignature)
 */

public class ModifyFunction extends BasicFunction 
{
	
	public final static String DSML_NAMESPACE = "http://www.dsml.org/DSML";

	public final static String DSML_PREFIX = "dsml";

	public final static FunctionSignature[] signatures = {
			
			new FunctionSignature(
					new QName( "modify", JNDIModule.NAMESPACE_URI, JNDIModule.PREFIX ),
							"Modify a JNDI Directory entry. $a is the directory context handle from a jndi:get-dir-context() call. $b is the DN. Expects "
							+ " entry attributes to be set in $c in the"
							+ " form <attributes><attribute name=\"\" value=\"\" operation=\"add | replace | remove\"/></attributes>. ",
					new SequenceType[] {
							new SequenceType( Type.INTEGER, Cardinality.EXACTLY_ONE ), 
							new SequenceType( Type.STRING, Cardinality.EXACTLY_ONE ), 
							new SequenceType( Type.ELEMENT, Cardinality.EXACTLY_ONE ) 
					},
					new SequenceType( Type.ITEM, Cardinality.EMPTY ) )
			};

	/**
	 * ModifyFunction Constructor
	 * 
	 * @param context 	The Context of the calling XQuery
	 */
	
	public ModifyFunction( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}

	
	/**
	 * evaluate the call to the xquery modify() function, it is really
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
		// Was context handle or DN specified?
		if( !( args[0].isEmpty() ) && !( args[1].isEmpty() ) ) {
			
			String dn = args[1].getStringValue();
			
			try {
				long ctxID = ((IntegerValue)args[0].itemAt(0)).getLong();
				
				DirContext ctx = (DirContext)JNDIModule.retrieveJNDIContext( context, ctxID );
				
				if( ctx == null ) {
					LOG.error( "jndi:modify() - Invalid JNDI context handle provided: " + ctxID );
				} else {	
					ModificationItem[] items = parseAttributes( args[ 2 ] );
					
					ctx.modifyAttributes( dn, items );
				}
			}
			catch( NamingException ne ) {
				LOG.error( "jndi:modify() Modify failed for dn [" + dn + "]: " + ne );
				throw( new XPathException( getASTNode(), "jndi:modify() Modify failed for dn [" + dn + "]: " + ne ) );
			}
		}
		
		return( Sequence.EMPTY_SEQUENCE );
	}
	
	
	/**
	 * Parses attributes into a JNDI ModificationItem array
	 * 
	 * @param arg				The attributes as a sequence of nodes
	 * @return 					The array of ModificationItems
	 */
	
	private ModificationItem[] parseAttributes( Sequence arg ) throws XPathException
	{
		ArrayList<ModificationItem> items = new ArrayList<ModificationItem>();
		
		ModificationItem[] mi = new ModificationItem[1];
		
		if( !( arg.isEmpty() ) ) {
		
			Node container = ( (NodeValue)arg.itemAt( 0 ) ).getNode();
			
			if( container != null && container.getNodeType() == Node.ELEMENT_NODE ) {
				
				NodeList attrs = ((Element)container).getElementsByTagName( "attribute" );
	
				for( int i = 0; i < attrs.getLength(); i++ ) {
					Element attr = ((Element)attrs.item( i ));
	
					String name  = attr.getAttribute( "name" );
					String value = attr.getAttribute( "value" );
					String op 	 = attr.getAttribute( "operation" );
	
					if( name != null && value != null && op != null ) {
						int opCode = 0;
						
						if(  op.equalsIgnoreCase( "add" ) ) {
							opCode = 1;
						} else if(  op.equalsIgnoreCase( "replace" ) ) {
							opCode = 2;
						} else if(  op.equalsIgnoreCase( "remove" ) ) {
							opCode = 3;
						}
						
						if( opCode == 0 ) {
							LOG.error( "jndi:modify() - Invalid operation code: [" + op + "]" );
							throw( new XPathException( getASTNode(), "jndi:modify() - Invalid operation code: [" + op + "]" ) );
						}

						ModificationItem item =	new ModificationItem( opCode, new BasicAttribute( name, value ) );
						
						items.add( item );
					} else {
						LOG.warn( "Name, value or operation attribute missing for attribute" );
					}
				}
			}
		}
		
		return( items.toArray( mi ) );
	}

}
