import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmldb.api.*;

public class Test {

	public static void main (String[] args) throws Exception {

		Collection col = null;

		try {

			String driver = "org.exist.xmldb.DatabaseImpl";
			Class c = Class.forName (driver);
			Database database = (Database) c.newInstance ();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase (database);
			col = DatabaseManager.getCollection("xmldb:exist:///db/peers");

			String xpath = "/peer/name[nickname='tests']";
			XPathQueryService service = (XPathQueryService) col.getService("XPathQueryService", "1.0");
			ResourceSet resultSet = service.query (xpath);
			ResourceIterator results = resultSet.getIterator ();
			while (results.hasMoreResources ()) {
				XMLResource res = (XMLResource)results.nextResource ();
				System.out.println(res.getContent());
			}
		} catch (XMLDBException e) {
			System.err.println ("XML:DB Exception occured " + e.errorCode);
			System.err.println(e.getMessage());
		} finally {
			if (col != null) {
				col.close();
			}
		}
	}
}
