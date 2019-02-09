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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;


public class GetID extends Function 
{
	public final static String ATTRIBUTES_CONTEXTVAR 			= "_eXist_xquery_context_attributes";
		
	protected static final Logger logger = LogManager.getLogger( GetID.class );

	public final static FunctionSignature signatures[] = {
		
        new FunctionSignature(
            new QName( "get-id", ContextModule.NAMESPACE_URI, ContextModule.PREFIX ),
            "Returns the id of the calling XQuery. For example, this id could be used in the system:kill-running-xquery() function.",
            null,
            new FunctionReturnSequenceType( Type.ITEM, Cardinality.EXACTLY_ONE, "The id of the running XQuery" )
        )
    };
	
	
	public GetID( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}
	
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval( Sequence contextSequence, Item contextItem ) throws XPathException 
	{
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
		
		return( new IntegerValue( context.hashCode() ) );
    }
}
