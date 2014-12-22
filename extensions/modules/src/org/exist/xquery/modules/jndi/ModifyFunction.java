/*
 *  eXist SQL Module Extension ModifyFunction
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


import java.util.ArrayList;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
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
	protected static final Logger logger = Logger.getLogger(ModifyFunction.class);
	
	public final static String DSML_NAMESPACE = "http://www.dsml.org/DSML";

	public final static String DSML_PREFIX = "dsml";

	public final static FunctionSignature[] signatures = {
			
			new FunctionSignature(
					new QName( "modify", JNDIModule.NAMESPACE_URI, JNDIModule.PREFIX ),
							"Modify a JNDI Directory entry.",
					new SequenceType[] {
						new FunctionParameterSequenceType( "directory-context", Type.INTEGER, Cardinality.EXACTLY_ONE, "The directory context handle from a jndi:get-dir-context() call" ), 
						new FunctionParameterSequenceType( "dn", Type.STRING, Cardinality.EXACTLY_ONE, "The Distinguished Name" ), 
						new FunctionParameterSequenceType( "attributes", Type.ELEMENT, Cardinality.EXACTLY_ONE, "The entry attributes to be set in the"
							+ " form <attributes><attribute name=\"\" value=\"\" operation=\"add | replace | remove\"/></attributes>. "
							+ " You can also optionally specify ordered=\"true\" for an attribute." ) 
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
					logger.error( "jndi:modify() - Invalid JNDI context handle provided: " + ctxID );
				} else {	
					ModificationItem[] items = parseAttributes( args[ 2 ] );
					
					if( items.length > 0 ) {
						ctx.modifyAttributes( dn, items );
					}
				}
			}
			catch( NamingException ne ) {
				logger.error( "jndi:modify() Modify failed for dn [" + dn + "]: ", ne );
				throw( new XPathException( this, "jndi:modify() Modify failed for dn [" + dn + "]: " + ne ) );
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
	
					String name  	= attr.getAttribute( "name" );
					String value 	= attr.getAttribute( "value" );
					String op 	 	= attr.getAttribute( "operation" );
					String ordered 	= attr.getAttribute( "ordered" );
	
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
							logger.error( "jndi:modify() - Invalid operation code: [" + op + "]" );
							throw( new XPathException( this, "jndi:modify() - Invalid operation code: [" + op + "]" ) );
						}
						
						Attribute existingAttr = null;
						
						// Scan the existing list of ModificationItems backwards for one that matches the name we're trying to add.
						// If the last such entry matches the opCode, then just add the value to the existing attribute (ModItem),
						// Otherwise create a new ModificationItem.
						//
						// This basically collapses nearby identically named attributes that have the same opCode into one, except for removes
						
						for( int j = items.size() - 1; j >= 0; j-- ) {
							ModificationItem item  = (ModificationItem)items.get( j );
							
							if( name.equals( item.getAttribute().getID() ) ) {
								if( item.getModificationOp() == opCode && opCode != 3 ) {
									existingAttr = item.getAttribute();
								} 
								
								break;
							} 
						}
			
						if( existingAttr != null ) {
							existingAttr.add( value );
						} else {
							items.add( new ModificationItem( opCode, new BasicAttribute( name, value, ordered != null && ordered.equalsIgnoreCase( "true" ) ) ) );
						}
					} else {
						logger.warn( "Name, value or operation attribute missing for attribute" );
					}
				}
			}
		}
		
		return( items.toArray( mi ) );
	}

}
