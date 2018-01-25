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
package org.exist.xmldb.concurrent;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistXQueryService;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Static utility methods used by the tests.
 *
 * @author wolf
 */
public class DBUtils {
    
    private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    
    public static Collection setupDB(String uri) throws Exception {
        Class<?> cl = Class.forName(DRIVER);
        Database database = (Database)cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        return DatabaseManager.getCollection(uri, "admin", "");
    }
    
    public static void shutdownDB(String uri) throws XMLDBException {
        Collection collection = DatabaseManager.getCollection(uri, "admin", "");
        if (collection != null) {
            DatabaseInstanceManager manager = (DatabaseInstanceManager)
            collection.getService("DatabaseInstanceManager","1.0");
            manager.shutdown();
            collection.close();
        }
    }
    
    /**
     * @param elementCnt
     * @param attrCnt
     * @param wordList
     * @return File
     */
    public static Path generateXMLFile(int elementCnt, int attrCnt, String[] wordList) throws Exception {
        return generateXMLFile(elementCnt, attrCnt, wordList, false);
    }
    
    /**
     * @param elementCnt
     * @param attrCnt
     * @param wordList
     * @param namespaces
     * @return File
     */
    public static Path generateXMLFile(int elementCnt, int attrCnt, String[] wordList, boolean namespaces) throws Exception {
        return generateXMLFile(3, elementCnt, attrCnt, wordList, false);
    }
    
    /**
     * @param depth
     * @param elementCnt
     * @param attrCnt
     * @param wordList
     * @param namespaces
     * @return File
     */
	public static Path generateXMLFile(int depth, int elementCnt, int attrCnt, String[] wordList, boolean namespaces) throws Exception {
		final Path file = Files.createTempFile(Thread.currentThread().getName(), ".xml");
		if(Files.exists(file) && !Files.isWritable(file)) {
			throw new IllegalArgumentException("Cannot write to output file " + file.toAbsolutePath());
		}

		try(final Writer writer = Files.newBufferedWriter(file, UTF_8)) {
			XMLGenerator gen = new XMLGenerator(elementCnt, attrCnt, depth, wordList, namespaces);
			gen.generateXML(writer);
		}
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

	public static void addXMLResource(Collection col, String resourceId, Path file) throws XMLDBException {
		XMLResource res = (XMLResource)col.createResource(
				resourceId, "XMLResource");
		res.setContent(file);
		col.storeResource(res);
	}
	
	public static void addXMLResource(Collection col, String resourceId, String contents) throws XMLDBException {
		XMLResource res = (XMLResource)col.createResource(
				resourceId, "XMLResource");
		res.setContent(contents);
		col.storeResource(res);
	}
	
	public static ResourceSet query(Collection collection, String xpath)
	throws XMLDBException
	{
		XPathQueryService service = getQueryService(collection);
		return service.query(xpath);
	}
	
    public static ResourceSet queryResource(Collection collection, String resource, String xpath)
    throws XMLDBException
    {
        XPathQueryService service = getQueryService(collection);
        return service.queryResource(resource, xpath);
    }
    
	public static ResourceSet xquery(Collection collection, String xquery)
	throws XMLDBException
	{
		EXistXQueryService service = getXQueryService(collection);
		Source source = new StringSource(xquery);
		return service.execute(source);
	}
	
	public static XPathQueryService getQueryService(Collection collection)
	throws XMLDBException
	{
		return (XPathQueryService) collection.getService(
				"XPathQueryService", "1.0");
	}

	public static EXistXQueryService getXQueryService(Collection collection)
	throws XMLDBException
	{
		return (EXistXQueryService) collection.getService(
				"XQueryService", "1.0");
	}
	
	/**
	 * @param root The root collection
	 * */
	public static String[] wordList(Collection root) throws XMLDBException {
        final String query = "util:index-keys(//*, \"\", function($key, $options) {\n" +
                "    $key\n" +
                "}, 100, \"lucene-index\")";
        EXistXQueryService service = getXQueryService(root);
        ResourceSet result = service.query(query);

        ArrayList<String> list = new ArrayList<String>();
        for (ResourceIterator iter = result.getIterator(); iter.hasMoreResources(); ) {
            Resource next = iter.nextResource();
            list.add(next.getContent().toString());
        }
        String[] words = new String[list.size()];
        list.toArray(words);
        System.out.println("Size of the word list: " + words.length);
        return words;
    }

    public static void main(String[] args) throws Exception {
        Collection collection = setupDB("xmldb:exist:///db");
        String[] words = wordList(collection);
        for (String word : words) {
            System.out.println(word);
        }
        shutdownDB("xmldb:exist:///db");
    }
}
