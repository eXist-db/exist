/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
 *  http://exist-db.org
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

import java.util.Date;

//import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * @author Andrzej Taramina (andrzej@chaeron.com)
 * @author Loren Cahlander
 */

public class SystemTime extends Function 
{
//	private static final Logger logger = LogManager.getLogger(SystemTime.class);

	public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName( "system-time", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
            "Returns the current xs:time (with timezone) as reported by the Java method System.currentTimeMillis(). " +
            "Contrary to fn:current-time, this function is not stable, i.e. the returned xs:time will change " +
            "during the evaluation time of a query and can be used to measure time differences.",
            null,
            new FunctionReturnSequenceType(Type.TIME, Cardinality.EXACTLY_ONE, "the current xs:time (with timezone)" )
		),
	
		 new FunctionSignature(
            new QName( "system-date", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
            "Returns the current xs:date (with timezone) as reported by the Java method System.currentTimeMillis(). " +
            "Contrary to fn:current-date, this function is not stable, i.e. the returned xs:date will change " +
            "during the evaluation time of a query and can be used to measure time differences.",
            null,
            new FunctionReturnSequenceType( Type.DATE, Cardinality.EXACTLY_ONE, "the current xs:date (with timezone)" ) 
		),
			
		new FunctionSignature(
            new QName( "system-dateTime", UtilModule.NAMESPACE_URI, UtilModule.PREFIX ),
            "Returns the current xs:dateTime (with timezone) as reported by the Java method System.currentTimeMillis(). " +
            "Contrary to fn:current-dateTime, this function is not stable, i.e. the returned xs:dateTime will change " +
            "during the evaluation time of a query and can be used to measure time differences.",
            null,
            new FunctionReturnSequenceType( Type.DATE_TIME, Cardinality.EXACTLY_ONE, "the current xs:dateTime (with timezone)" )
		)
	};

    
    public SystemTime( XQueryContext context, FunctionSignature signature ) 
	{
        super( context, signature );
    }
	

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

		Sequence result = new DateTimeValue( new Date() );
		
		if( isCalledAs("system-dateTime" ) ) {
			// do nothing, result already in right form
		} else if( isCalledAs("system-date" ) ) {
			result = result.convertTo( Type.DATE );
		} else if( isCalledAs("system-time" ) ) {
			result = result.convertTo( Type.TIME );
		} else {
			throw( new Error( "can't handle function " + mySignature.getName().getLocalPart() ) );
		}

		if( context.getProfiler().isEnabled() ) {
			context.getProfiler().end( this, "", result );   
		}

		return( result );
    }
	
	
	public int getDependencies() 
	{
        return( Dependency.CONTEXT_SET );
    }

}
