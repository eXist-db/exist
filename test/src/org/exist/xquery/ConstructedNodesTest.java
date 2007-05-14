package org.exist.xquery;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import junit.framework.TestCase;
import junit.textui.TestRunner;

/** Tests for various constructed node operations (in-memory nodes)
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class ConstructedNodesTest extends TestCase
{
	private final static String TEST_DB_USER = "admin";
	private final static String TEST_DB_PWD = null;
	
	private XPathQueryService service;
	private Collection root = null;
	private Database database = null;
	
	
	public static void main(String[] args) throws XPathException
	{
		TestRunner.run(ConstructedNodesTest.class);
	}
	
	/**
	 * Constructor for ConstructedNodesTest.
	 * @param arg0
	 */
	public ConstructedNodesTest(String arg0)
	{
		super(arg0);
	}
	
	/**
	 * Iteratively constructs some nodes
	 */
	public void testIterateConstructNodes() throws XPathException
	{
		String xquery =
				"declare variable $categories := \n" +
				"	<categories>\n" +
				"		<category uid=\"1\">Fruit</category>\n" +
				"		<category uid=\"2\">Vegetable</category>\n" +
				"		<category uid=\"3\">Meat</category>\n" +
				"		<category uid=\"4\">Dairy</category>\n" +
				"	</categories>\n" +
				";\n\n" + 
				
				"for $category in $categories/category return\n" +
				"	element option {\n" +
				"		attribute value {\n" +
				"			$category/@uid\n" +
				"		},\n" +
				"		text { $category }\n" +
				"	}";
		
		String expectedResults [] = { 
			"<option value=\"1\">Fruit</option>",
			"<option value=\"2\">Vegetable</option>",
			"<option value=\"3\">Meat</option>",
			"<option value=\"4\">Dairy</option>"
		};
		
		ResourceSet result = null;
		
		try
		{
			result = service.query(xquery);
			
			assertEquals(expectedResults.length, result.getSize());
			
			for(int i = 0; i < result.getSize(); i++)
			{
				assertEquals(expectedResults[i], (String)result.getResource(i).getContent());
			}
		}
		catch (XMLDBException e)
		{
			System.out.println("testIterateConstructNodes(): " + e);
			fail(e.getMessage());
		}
	}
	
	/***
	 * Test sorting of constructed nodes
	 */
	public void testConstructedNodesSort()
	{
		String xquery =
			"declare variable $categories := \n" +
			"	<categories>\n" +
			"		<category uid=\"1\">Fruit</category>\n" +
			"		<category uid=\"2\">Vegetable</category>\n" +
			"		<category uid=\"3\">Meat</category>\n" +
			"		<category uid=\"4\">Dairy</category>\n" +
			"	</categories>\n" +
			";\n\n" + 
			
			"for $category in $categories/category order by $category/@uid descending return $category";
		
		String expectedResults [] = { 
				"<category uid=\"4\">Dairy</category>",
				"<category uid=\"3\">Meat</category>",
				"<category uid=\"2\">Vegetable</category>",
				"<category uid=\"1\">Fruit</category>"
		};
		
		ResourceSet result = null;
		
		try
		{
			result = service.query(xquery);
			
			assertEquals(expectedResults.length, result.getSize());
			
			for(int i = 0; i < result.getSize(); i++)
			{
				assertEquals(expectedResults[i], (String)result.getResource(i).getContent());
			}
		}
		catch (XMLDBException e)
		{
			System.out.println("testConstructedNodesSort(): " + e);
			fail(e.getMessage());
		}
	}
	
	/**
	 * Test retreiving sorted nodes by position
	 */
	public void bugtestConstructedNodesPosition()
	{
		String xquery =
			"declare variable $categories := \n" +
			"	<categories>\n" +
			"		<category uid=\"1\">Fruit</category>\n" +
			"		<category uid=\"2\">Vegetable</category>\n" +
			"		<category uid=\"3\">Meat</category>\n" +
			"		<category uid=\"4\">Dairy</category>\n" +
			"	</categories>\n" +
			";\n\n" + 
			
			"$categories/category[1],\n" +
			"$categories/category[position() eq 1]";
		
		String expectedResults [] = { 
				"<option value=\"1\">Fruit</option>",
				"<option value=\"1\">Fruit</option>"
		};
		
		ResourceSet result = null;
		
		try
		{
			result = service.query(xquery);
			
			assertEquals(expectedResults.length, result.getSize());
			
			for(int i = 0; i < result.getSize(); i++)
			{
				assertEquals(expectedResults[i], (String)result.getResource(i).getContent());
			}
		}
		catch (XMLDBException e)
		{
			System.out.println("testConstructedNodesPosition(): " + e);
			fail(e.getMessage());
		}
	}
	
	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception
	{
		// initialize driver
		Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
		database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);
		root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, TEST_DB_USER, TEST_DB_PWD);
		service = (XPathQueryService) root.getService( "XQueryService", "1.0" );
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception
	{
		DatabaseManager.deregisterDatabase(database);
		DatabaseInstanceManager dim =
			(DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
		dim.shutdown();
        
        // clear instance variables
        service = null;
        root = null;
	}
}
