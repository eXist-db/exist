/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xmldb.test.concurrent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

import org.exist.util.Occurrences;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.IndexQueryService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

/**
 * Static utility methods used by the tests.
 * 
 * @author wolf
 */
public class DBUtils {

	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
	
	public static Collection setupDB(String uri) throws Exception {
		Class cl = Class.forName(DRIVER);
        Database database = (Database)cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        return DatabaseManager.getCollection(uri);
	}
	
	public static void shutdownDB(String uri) throws XMLDBException
    {
		Collection collection = DatabaseManager.getCollection(uri, "admin", null);
		if (collection != null)
		{
			DatabaseInstanceManager manager = (DatabaseInstanceManager)
				collection.getService("DatabaseInstanceManager","1.0");
			manager.shutdown();
			collection.close();
        }
    }
	
	public static File generateXMLFile(String outputFile, int elementCnt, int attrCnt, String[] wordList) throws Exception {
		File file = new File(outputFile);
		if(file.exists() && !file.canWrite())
			throw new IllegalArgumentException("Cannot write to output file " + outputFile);
		Writer writer = new BufferedWriter(new FileWriter(file));
		
		XMLGenerator gen = new XMLGenerator(elementCnt, attrCnt, 3, wordList);
		gen.generateXML(writer);
		writer.close();
		return file;
	}
	
	public static Collection addCollection(Collection parent, String name)
	throws XMLDBException
	{
		CollectionManagementService service = getCollectionManagementService(
				parent);
		
		return service.createCollection(name);
	}
	
	public static void removeCollection(Collection parent, String name) throws XMLDBException {
		CollectionManagementService service = getCollectionManagementService(
				parent);
		service.removeCollection(name);
	}
	
	public static CollectionManagementService getCollectionManagementService(
	        Collection col) throws XMLDBException {
	        return (CollectionManagementService)col.getService(
	            "CollectionManagementService", "1.0");
	    }

	public static void addXMLResource(Collection col, String resourceId, File file) throws XMLDBException {
		XMLResource res = (XMLResource)col.createResource(
				resourceId, "XMLResource");
		res.setContent(file);
		col.storeResource(res);
	}
	
	public static String[] wordList(Collection root) throws XMLDBException {
		IndexQueryService service = (IndexQueryService)
		root.getService("IndexQueryService", "1.0");
		Occurrences[] terms = service.scanIndexTerms("a", "z", true);
		String[] words = new String[terms.length];
		for(int i = 0; i < terms.length; i++) {
			words[i] = terms[i].getTerm().toString();
		}
		System.out.println("Size of the word list: " + words.length);
		return words;
	}
}
