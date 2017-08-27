/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.xquery.modules.context;

import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * XQuery Extension module for accessing data in XQuery Contexts
 * 
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @version 1.0
 */
public class ContextModule extends AbstractInternalModule {

    private final static Logger logger = LogManager.getLogger( ContextModule.class );

    public final static String NAMESPACE_URI 		= "http://exist-db.org/xquery/context";

	public final static String PREFIX 				= "context";
    public final static String INCLUSION_DATE 		= "2010-02-01";
    public final static String RELEASED_IN_VERSION 	= "eXist-2.0";
        
	private final static FunctionDef[] signatures = {
		new FunctionDef( ContextAttributes.signatures[0], ContextAttributes.class ),
		new FunctionDef( ContextAttributes.signatures[1], ContextAttributes.class ),
		new FunctionDef( ContextAttributes.signatures[2], ContextAttributes.class ),
		new FunctionDef( ContextAttributes.signatures[3], ContextAttributes.class ),
		new FunctionDef( GetID.signatures[0], GetID.class )
	};

	public ContextModule(Map<String, List<?>> parameters) {
		super( signatures, parameters );
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
		return( "A module for accessing information in XQuery contexts" );
	}

    public String getReleaseVersion() 
	{
        return( RELEASED_IN_VERSION );
    }
}
