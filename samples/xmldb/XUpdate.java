package samples.xmldb;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * XUpdate.java
 * 
 * @author Wolfgang Meier
 */
public class XUpdate {

	static String driver = "org.exist.xmldb.DatabaseImpl";
	static String URI = "xmldb:exist:///db";
	static String xml =
		"<addressbook>"
			+ "<address><name>Dr. Jekyll</name><city>Berlin</city>"
			+ "<phone>054 776453</phone></address>"
			+ "</addressbook>";
	static String[][] addresses = { { "Mr. Hyde", "Frankfurt" }, {
			"Mr. Fischer", "Berlin" }, {
			"Mr. Dutschke", "Frankfurt" }
	};

	static String updateStart =
		"<xu:modifications version=\"1.0\""
			+ "    xmlns:xu=\"http://www.xmldb.org/xupdate\">"
			+ "<xu:insert-before select=\"//address[name &amp;= 'jekyll']\">"
		//+ "<xu:append select=\"/addressbook\">"
	+"<xu:element name=\"address\">";
	static String updateEnd = "</xu:element>" + "</xu:insert-before>"
		//+ "</xu:append>"
	+"</xu:modifications>";
	static String updatePhone =
		"<xu:modifications version=\"1.0\""
			+ " xmlns:xu=\"http://www.xmldb.org/xupdate\">"
			+ "<xu:append select=\"//address[name &amp;= 'fischer']\">"
			+ "<xu:element name=\"phone\">"
			+ "069 778695"
			+ "</xu:element>"
			+ "</xu:append>"
			+ "</xu:modifications>";
	static String remove =
		"<xu:modifications version=\"1.0\""
			+ " xmlns:xu=\"http://www.xmldb.org/xupdate\">"
			+ "<xu:remove select=\"//address[name &amp;= 'hyde']\"/>"
			+ "</xu:modifications>";
	static String appendNew =
		"<xu:modifications version=\"1.0\""
			+ " xmlns:xu=\"http://www.xmldb.org/xupdate\">"
			+ "<xu:append select=\"/addressbook\">"
            + "<xu:element name=\"address\">"
            + "<name>Dr. Müller</name><city>Darmstadt</city>"
            + "</xu:element>"
            + "</xu:append>"
			+ "</xu:modifications>";
	public static void main(String args[]) throws Exception {
		// initialize database drivers
		Class cl = Class.forName(driver);
		Database database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);

		// get the collection
		Collection col = DatabaseManager.getCollection(URI);
		col.setProperty("pretty", "true");
		col.setProperty("encoding", "ISO-8859-1");

		XMLResource res =
			(XMLResource) col.createResource("test.xml", "XMLResource");
		res.setContent(xml);
		col.storeResource(res);

		XUpdateQueryService xupdate =
			(XUpdateQueryService) col.getService("XUpdateQueryService", "1.0");
		for (int i = 0; i < addresses.length; i++) {
			xupdate.update(
				updateStart
					+ "<name>"
					+ addresses[i][0]
					+ "</name>"
					+ "<city>"
					+ addresses[i][1]
					+ "</city>"
					+ updateEnd);
		}
		xupdate.update(updatePhone);
		xupdate.update(remove);
	}

}
