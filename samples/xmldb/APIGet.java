package samples.xmldb;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.exist.dom.ElementImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

/**
 *  Retrieve a document from the repository. To run this example enter:
 *  bin/run.sh samples.APISearch xpath-query in the root directory of the
 *  distribution.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    10. Juli 2002
 */
public class APIGet {

	protected static String driver = "org.exist.xmldb.DatabaseImpl";

	protected static String URI = "xmldb:exist://";

	/**  Description of the Method */
	protected static void usage() {
		System.out.println("usage: samples.APIGet [ collection ] docName");
		System.exit(0);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  args           Description of the Parameter
	 *@exception  Exception  Description of the Exception
	 */
	public static void main(String args[]) throws Exception {
		if (args.length < 1) {
			usage();
		}

		String collection = "/db";

		// if collection does not start with "/" add it
		collection = (args[0].charAt(0) == '/') ? args[0] : "/db/" + args[0];

		// initialize database drivers
		Class cl = Class.forName(driver);
		Database database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);

		// get the collection
		Collection col = DatabaseManager.getCollection(URI + collection);
		col.setProperty("pretty", "true");
		col.setProperty("encoding", "ISO-8859-1");
		XMLResource res = (XMLResource)col.getResource(args[1]);
		System.out.println(res.getContent());
	}
}
