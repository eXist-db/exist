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
public class Simple {

	static String driver = "org.exist.xmldb.DatabaseImpl";
	static String URI = "xmldb:exist:///db/shakespeare/plays";

	static String update =
		"<xu:modifications version=\"1.0\""
			+ "    xmlns:xu=\"http://www.xmldb.org/xupdate\">"
			+ "<xu:append select=\"//ACT[1]/SCENE[1]\">"
			+ "<xu:element name=\"SPEECH\">"
			+ "<SPEAKER>Wolfgang</SPEAKER>"
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

		XUpdateQueryService xupdate =
			(XUpdateQueryService) col.getService("XUpdateQueryService", "1.0");
	    xupdate.update(update);
	}

}
