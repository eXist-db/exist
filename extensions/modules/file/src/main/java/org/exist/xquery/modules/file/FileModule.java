/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *  $Id$
 */
package org.exist.xquery.modules.file;

import java.util.List;
import java.util.Map;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * eXist File Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows various file-oriented
 * activities.
 * 
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author ljo
 * @serial 2008-03-06
 * @version 1.0
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[], java.util.Map) 
 */
public class FileModule extends AbstractInternalModule
{
	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/file";
	
	public final static String PREFIX = "file";
    public final static String INCLUSION_DATE = "2008-03-07";
    public final static String RELEASED_IN_VERSION = "eXist-1.4";

	
	private final static FunctionDef[] functions = {
        new FunctionDef( Directory.signatures[0],               Directory.class ),
		new FunctionDef( DirectoryList.signatures[0],           DirectoryList.class ),
		new FunctionDef( FileRead.signatures[0], 				FileRead.class ),
		new FunctionDef( FileRead.signatures[1], 				FileRead.class ),
		new FunctionDef( FileReadBinary.signatures[0], 			FileReadBinary.class ), 
		new FunctionDef( FileReadUnicode.signatures[0], 		FileReadUnicode.class ),
		new FunctionDef( FileReadUnicode.signatures[1], 		FileReadUnicode.class ),
		new FunctionDef( SerializeToFile.signatures[0], 		SerializeToFile.class ),
        new FunctionDef( SerializeToFile.signatures[1],         SerializeToFile.class ),
		new FunctionDef( SerializeToFile.signatures[2], 		SerializeToFile.class ),
        new FunctionDef( SerializeToFile.signatures[3],         SerializeToFile.class ),
		new FunctionDef( FileExists.signatures[0], 				FileExists.class ),
		new FunctionDef( FileIsReadable.signatures[0], 			FileIsReadable.class ),
		new FunctionDef( FileIsWriteable.signatures[0], 		FileIsWriteable.class ),
		new FunctionDef( FileIsDirectory.signatures[0], 		FileIsDirectory.class ),
		new FunctionDef( FileDelete.signatures[0], 				FileDelete.class ),
        new FunctionDef( FileMove.signatures[0], 				FileMove.class ),
        new FunctionDef( DirectoryCreate.signatures[0], 		DirectoryCreate.class ),
        new FunctionDef( DirectoryCreate.signatures[1], 		DirectoryCreate.class ),
        new FunctionDef( Sync.signature,						Sync.class)
	};
	
	
	public FileModule(Map<String, List<?>> parameters)
	{
		super( functions, parameters );
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
		return( "A module for performing various operations on files and directories stored in the server file system." );
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
