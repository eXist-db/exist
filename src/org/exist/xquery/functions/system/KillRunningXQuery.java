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
 *  $Id:
 */
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Kill a running XQuery (must be dba)
 * 
 * @author Andrzej Taramina (andrzej@chaeron.com)
 */
public class KillRunningXQuery extends BasicFunction
{
    protected static final FunctionParameterSequenceType WAIT_TIME_PARAM = new FunctionParameterSequenceType( "wait-time", Type.LONG, Cardinality.EXACTLY_ONE, "The wait time in milliseconds before terminating the XQuery" );

	protected static final FunctionParameterSequenceType XQUERY_ID_PARAM = new FunctionParameterSequenceType( "xquery-id", Type.INTEGER, Cardinality.EXACTLY_ONE, "The XQuery ID obtained from get-running-xqueries()" );

	protected final static Logger logger = LogManager.getLogger(KillRunningXQuery.class);

	final static String NAMESPACE_URI                       = SystemModule.NAMESPACE_URI;
    final static String PREFIX                              = SystemModule.PREFIX;
    

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "kill-running-xquery", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
			"Kill a running XQuey (dba role only).",
			new SequenceType[] { XQUERY_ID_PARAM },
			new SequenceType( Type.ITEM, Cardinality.EMPTY )
		),
		
		new FunctionSignature(
			new QName( "kill-running-xquery", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
			"Kill a running XQuey (dba role only).",
			new SequenceType[] { XQUERY_ID_PARAM, WAIT_TIME_PARAM },
			new SequenceType( Type.ITEM, Cardinality.EMPTY )
		),
	};
		
		

	public KillRunningXQuery( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		if( !context.getSubject().hasDbaRole() ) {
			throw( new XPathException( this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to kill a running xquery" ) );
		}
		
		killXQuery( args );
			
		return( Sequence.EMPTY_SEQUENCE );
	}
	
	
	private void killXQuery( Sequence[] args ) throws XPathException 
	{
		int  id 		= 0;
		long waittime 	= 0;
		
		//determine the query id to kill
		if( args.length == 1 ) {
			if( !args[0].isEmpty() ) {
				id = ((NumericValue)args[0].itemAt(0)).getInt();
			}
		}
		
		//determine the wait time
		if( args.length == 2 ) {
			if( !args[1].isEmpty() ) {
				waittime = ((NumericValue)args[1].itemAt(0)).getLong();
			}
		}
        
        if( id != 0 ) {
            final XQueryWatchDog watchdogs[] = getContext().getBroker().getBrokerPool().getProcessMonitor().getRunningXQueries();
            for (int i = 0; i < watchdogs.length; i++) {
	        	final XQueryContext 	context 	= watchdogs[i].getContext();
	        	
	      		if( id == context.hashCode() ) {
	      			if( !watchdogs[i].isTerminating() ) {
	      				watchdogs[i].kill( waittime );
	      			}
	      			break;
	      		}
	        }
	    }
	}
	
}
