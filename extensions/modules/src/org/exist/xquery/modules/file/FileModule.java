/*
 *  eXist SQL Module Extension
 *  Copyright (C) 2006 Adam Retter <adam.retter@devon.gov.uk>
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
 *  $Id: SQLModule.java 3933 2006-09-18 21:08:38 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.file;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XQueryContext;

/**
 * eXist File Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows various file-oriented
 * activities.
 * 
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @serial 2008-03-06
 * @version 1.0
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[])
 */


public class FileModule extends AbstractInternalModule
{
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/file";
	
	public final static String PREFIX = "file";
	
	private final static FunctionDef[] functions = {
		new FunctionDef( DirectoryListFunction.signatures[0], DirectoryListFunction.class ),
		new FunctionDef( FileRead.signatures[0], FileRead.class ),
		new FunctionDef( FileRead.signatures[1], FileRead.class ),
		new FunctionDef( FileReadUnicode.signatures[0], FileReadUnicode.class ),
		new FunctionDef( FileReadUnicode.signatures[1], FileReadUnicode.class )
	};
	
	
	public FileModule() 
	{
		super( functions );
	}
	

	public String getNamespaceURI() 
	{
		return( NAMESPACE_URI );
	}
	

	public String getDefaultPrefix() {
		return( PREFIX );
	}
	

	public String getDescription() 
	{
		return( "A module for performing various file-based operations." );
	}
		
	
	/**
	 * Resets the Module Context 
	 * 
	 * @param xqueryContext The XQueryContext
	 */
	
	public void reset( XQueryContext xqueryContext )
	{
		super.reset( xqueryContext );
	}
}
