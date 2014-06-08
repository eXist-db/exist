/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 */
package org.exist.storage.md.xquery;

import java.util.List;
import java.util.Map;

import org.exist.storage.md.MDStorageManager;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class MetadataModule extends AbstractInternalModule {
	
    public final static String INCLUSION_DATE = "2012-04-01";
    public final static String RELEASED_IN_VERSION = "eXist-2.0";

	public static final FunctionDef[] functions = {
		new FunctionDef( Check.signature, Check.class ),
		
		new FunctionDef( Reindex.signature, Reindex.class ),

		new FunctionDef( DocumentByPair.signatures[0], DocumentByPair.class ),
		new FunctionDef( DocumentByUUID.signatures[0], DocumentByUUID.class ),
		new FunctionDef( Keys.signatures[0], Keys.class ),
		new FunctionDef( Keys.signatures[1], Keys.class ),
		
		new FunctionDef( PairGet.signatures[0], PairGet.class ),
		new FunctionDef( PairGet.signatures[1], PairGet.class ),
		new FunctionDef( PairGet.signatures[2], PairGet.class ),
		
		new FunctionDef( PairSet.signatures[0], PairSet.class ),
		new FunctionDef( PairSet.signatures[1], PairSet.class ),
        
		new FunctionDef( PairDelete.signatures[0], PairDelete.class ),
        new FunctionDef( PairDelete.signatures[1], PairDelete.class ),

        new FunctionDef( UUID.signatures[0], UUID.class ),
		new FunctionDef( UUID.signatures[1], UUID.class ),

		new FunctionDef( Search.signatures[0], Search.class ),
        new FunctionDef( Search.signatures[1], Search.class )
	};
	
	public MetadataModule(Map<String, List<? extends Object>> parameters) throws XPathException {
		super( functions, parameters );
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "Metadata storage xquery interface"; 
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return MDStorageManager.NAMESPACE_URI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return MDStorageManager.PREFIX;
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}