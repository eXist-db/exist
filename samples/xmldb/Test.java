import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmldb.api.*;

public class Test {

	public static void main(String[] args) throws Exception {

		Collection col = null;

		try {

			String driver = "org.exist.xmldb.DatabaseImpl";
			Class c = Class.forName(driver);
			Database database = (Database) c.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			col =
				DatabaseManager.getCollection(
					"xmldb:exist://localhost:8080/exist/xmlrpc/db");

			String xpath = "//SPECIALITE or //PATHO";
			XPathQueryService service =
				(XPathQueryService) col.getService("XPathQueryService", "1.0");
			ResourceSet resultSet = service.query(xpath);
			ResourceIterator results = resultSet.getIterator();
			final XMLSerializer serializer =
				new XMLSerializer(
					System.out,
					new OutputFormat("xml", "ISO-8859-1", false));
			while (results.hasMoreResources()) {
				XMLResource res = (XMLResource) results.nextResource();
				Element elem = (Element) res.getContentAsDOM();
				serializer.serialize(elem);
			}
		} catch (XMLDBException e) {
			System.err.println("XML:DB Exception occured " + e.errorCode);
			System.err.println(e.getMessage());
		} finally {
			if (col != null) {
				col.close();
			}
		}
	}
}
