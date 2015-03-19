/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2009 Wolfgang M. Meier
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
 *  $Id$
 */
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;


/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author Andrzej Taramina (andrzej@chaeron.com)
 */
public class LogFunction extends BasicFunction 
{
	protected static final FunctionParameterSequenceType PRIORITY_PARAMETER = new FunctionParameterSequenceType( "priority", Type.STRING, Cardinality.EXACTLY_ONE, "The logging priority: 'error', 'warn', 'debug', 'info', 'trace'");
	protected static final FunctionParameterSequenceType LOGGER_NAME_PARAMETER = new FunctionParameterSequenceType( "logger-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the logger, eg: my.app.log" );
	protected static final FunctionParameterSequenceType MESSAGE_PARAMETER = new FunctionParameterSequenceType( "message", Type.ITEM, Cardinality.ZERO_OR_MORE, "The message to log" );

	protected static final Logger logger = LogManager.getLogger(LogFunction.class);
	
	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "log", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
			"Logs the message to the current logger.",
			new SequenceType[] { PRIORITY_PARAMETER , MESSAGE_PARAMETER },
			new SequenceType( Type.ITEM, Cardinality.EMPTY )
			),
		new FunctionSignature(
			new QName( "log-system-out", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
			"Logs the message to System.out.",
			new SequenceType[] { MESSAGE_PARAMETER },
			new SequenceType( Type.ITEM, Cardinality.EMPTY )
			),
		new FunctionSignature(
			new QName( "log-system-err", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
			"Logs the message to System.err.",
			new SequenceType[] { MESSAGE_PARAMETER },
			new SequenceType( Type.ITEM, Cardinality.EMPTY )
			),
		new FunctionSignature(
			new QName( "log-app", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
			"Logs the message to the named logger",
			new SequenceType[] { PRIORITY_PARAMETER, LOGGER_NAME_PARAMETER, MESSAGE_PARAMETER },
			new SequenceType( Type.ITEM, Cardinality.EMPTY )
			)
		};
	
	public LogFunction(XQueryContext context, FunctionSignature signature) 
	{
		super( context, signature );
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		SequenceIterator i;
		
		if( isCalledAs( "log" ) ) {
			i = args[1].unorderedIterator();
			if( args[1].isEmpty() ) {
				return( Sequence.EMPTY_SEQUENCE );
			}
		} else if( isCalledAs( "log-app" ) ) {
			i = args[2].unorderedIterator();
			if( args[2].isEmpty() ) {
				return( Sequence.EMPTY_SEQUENCE );
			}
		} else {
			i = args[0].unorderedIterator();
			if( args[0].isEmpty() ) {
				return( Sequence.EMPTY_SEQUENCE );
			}
		}
		
		// add line of the log statement
		final StringBuilder buf = new StringBuilder();
                buf.append("(");
                
		buf.append("Line: ");
		buf.append(this.getLine());
                
                //add the source to the log statement
                if(getSource() != null && getSource().getKey() != null) {
                    buf.append(" ");
                    buf.append(getSource().getKey());
                }
                
		buf.append(") ");
		
		while(i.hasNext()) {
			final Item next = i.nextItem();
			if( Type.subTypeOf( next.getType(), Type.NODE ) ) {
				final Serializer serializer = context.getBroker().getSerializer();
				serializer.reset();
				try {
					buf.append( serializer.serialize( (NodeValue)next ) );
				} 
				catch( final SAXException e ) {
					throw( new XPathException(this, "An exception occurred while serializing node to log: " + e.getMessage(), e ) );
				}
			} else {
				buf.append(next.getStringValue());
			}
		}
		
		if( isCalledAs( "log" ) ) {
			final String priority = args[0].getStringValue();			
			if( priority.equalsIgnoreCase( "error" ) ) {
				logger.error( buf );
			} else if( priority.equalsIgnoreCase( "warn" ) ) {
				logger.warn( buf );
			} else if( priority.equalsIgnoreCase( "info" ) ) {
				logger.info( buf );
			} else if( priority.equalsIgnoreCase( "trace" ) ) {
				logger.trace( buf );
			} else {
				logger.debug( buf );
			}
		} else if( isCalledAs( "log-system-out" ) ) {
			System.out.println(buf);
		} else if( isCalledAs( "log-system-err" ) ) {
			System.err.println( buf );				
		} else if (isCalledAs("log-app")) {
			final String priority = args[0].getStringValue();		
			final String logname  = args[1].getStringValue();	
			Logger logger   = LOG;
			
			if( logname != null && logname.length() > 0 ) {
				logger = LogManager.getLogger( logname );
			} 
			
			if( priority.equalsIgnoreCase( "error" ) ) {
				logger.error( buf );
			} else if( priority.equalsIgnoreCase( "warn" ) ) {
				logger.warn( buf );
			} else if( priority.equalsIgnoreCase( "info" ) ) {
				logger.info( buf );
			} else if( priority.equalsIgnoreCase( "trace" ) ) {
				logger.trace( buf );
			} else {
				logger.debug( buf );
			}
			
		}
		
		return( Sequence.EMPTY_SEQUENCE );
	}
}
