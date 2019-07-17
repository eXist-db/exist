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


import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist JNDI Module Extension RenameFunction
 * 
 * Rename a JNDI Directory entry
 * 
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @serial 2008-12-02
 * @version 1.0
 * 
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 *      org.exist.xquery.FunctionSignature)
 */
public class RenameFunction extends BasicFunction 
{
	protected static final Logger logger = LogManager.getLogger(RenameFunction.class);
	
	public final static String DSML_NAMESPACE = "http://www.dsml.org/DSML";

	public final static String DSML_PREFIX = "dsml";

	public final static FunctionSignature[] signatures = {
			
			new FunctionSignature(
					new QName( "rename", JNDIModule.NAMESPACE_URI, JNDIModule.PREFIX ),
							"Rename a JNDI Directory entry.",
					new SequenceType[] {
						new FunctionParameterSequenceType( "directory-context", Type.INTEGER, Cardinality.EXACTLY_ONE, "The directory context handle from a jndi:get-dir-context() call" ), 
						new FunctionParameterSequenceType( "old-dn", Type.STRING, Cardinality.EXACTLY_ONE, "The Distinguished Name to rename" ),
						new FunctionParameterSequenceType( "new-dn", Type.STRING, Cardinality.EXACTLY_ONE, "The new Distinguished Name" )
					},
					new SequenceType( Type.ITEM, Cardinality.EMPTY ) )
			};

	public RenameFunction( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}

	@Override
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		// Was context handle or DN specified?
		if( !( args[0].isEmpty() ) && !( args[1].isEmpty() ) && !( args[2].isEmpty() ) ) {
			
			String dn 		= args[1].getStringValue();
			String newDN 	= args[2].getStringValue();
			
			try {
				long ctxID = ((IntegerValue)args[0].itemAt(0)).getLong();
				
				DirContext ctx = (DirContext)JNDIModule.retrieveJNDIContext( context, ctxID );
				
				if( ctx == null ) {
					logger.error( "jndi:rename() - Invalid JNDI context handle provided: " + ctxID );
				} else {	
					ctx.rename( dn, newDN );
				}
			}
			catch( NamingException ne ) {
				logger.error( "jndi:rename() Rename failed for dn [" + dn + "], new dn [" + newDN + "]: ", ne );
				throw( new XPathException( this, "jndi:rename() Rename failed for dn [" + dn + "], new dn [" + newDN + "]: " + ne ) );
			}
		}
		
		return( Sequence.EMPTY_SEQUENCE );
	}

}
