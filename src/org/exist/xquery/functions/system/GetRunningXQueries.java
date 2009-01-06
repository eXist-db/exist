/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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

import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XQueryWatchDog;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Return a list of the currently running XQueries (must be dba)
 * 
 * @author Andrzej Taramina (andrzej@chaeron.com)
 */
public class GetRunningXQueries extends BasicFunction
{
	final static String NAMESPACE_URI                       = SystemModule.NAMESPACE_URI;
    final static String PREFIX                              = SystemModule.PREFIX;
    

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "get-running-xqueries", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
			"Get a list of running XQueries (dba role only).",
			null,
			new SequenceType( Type.ITEM, Cardinality.EXACTLY_ONE )
		);
		

	public GetRunningXQueries( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		if( !context.getUser().hasDbaRole() ) {
			throw( new XPathException( getASTNode(), "Permission denied, calling user '" + context.getUser().getName() + "' must be a DBA to get the list of running xqueries" ) );
		}
			
		return( getRunningXQueries() );
	}
	
	
	private Sequence getRunningXQueries()
	{
		Sequence    xmlResponse     = null;
        
        MemTreeBuilder builder = context.getDocumentBuilder();
        
        builder.startDocument();
        builder.startElement( new QName( "xqueries", NAMESPACE_URI, PREFIX ), null );
        
        //Add all the running xqueries
        XQueryWatchDog watchdogs[] = getContext().getBroker().getBrokerPool().getProcessMonitor().getRunningXQueries();
        for (int i = 0; i < watchdogs.length; i++) {
        	XQueryContext 	context 	= watchdogs[i].getContext();
        	
        	getRunningXQuery( builder, context, watchdogs[i] );
        }
        
        builder.endElement();
        
        xmlResponse = (NodeValue)builder.getDocument().getDocumentElement();
        
        return( xmlResponse );
	}
	
	private void getRunningXQuery( MemTreeBuilder builder, XQueryContext context, XQueryWatchDog watchdog ) 
	{
		builder.startElement( new QName( "xquery", NAMESPACE_URI, PREFIX ), null );
		
		builder.addAttribute( new QName( "id", null, null ), "" + context.hashCode() );
		builder.addAttribute( new QName( "sourceType", null, null ), context.getSourceType() );
		builder.addAttribute( new QName( "terminating", null, null ), ( watchdog.isTerminating() ? "true" : "false" ) );
		
		builder.startElement( new QName( "sourceKey", NAMESPACE_URI, PREFIX ), null );
		builder.characters( context.getSourceKey() );
		builder.endElement();

		builder.startElement( new QName( "xqueryExpression", NAMESPACE_URI, PREFIX ), null );
		builder.characters( context.getRootExpression().toString() );
		builder.endElement();
		
		builder.endElement();
	}
	
}
