/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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
package org.exist.xmldb.test.concurrent;

import java.io.File;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;

/**
 * Replace an existing resource.
 * 
 * @author wolf
 */
public class ReplaceResourceAction extends Action {

	private String[] wordList;
	private File tempFile;
	
	/**
	 * @param collectionPath
	 * @param resourceName
	 */
	public ReplaceResourceAction(String collectionPath, String resourceName, String[] wordList) {
		super(collectionPath, resourceName);
		this.wordList = wordList;
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.Action#execute()
	 */
	public boolean execute() throws Exception {
		Collection col = DatabaseManager.getCollection(collectionPath);
		tempFile = DBUtils.generateXMLFile(1000, 10, wordList);
		try {
			DBUtils.addXMLResource(col, "R1.xml", tempFile);
		
			return false;
		} finally {
			tempFile.delete();
		}
	}
}
