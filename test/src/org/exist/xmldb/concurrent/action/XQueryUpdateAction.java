package org.exist.xmldb.concurrent.action;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

public class XQueryUpdateAction extends Action {

	private static final String query =
		"util:exclusive-lock(collection('/db/C1'),\n" +
		"	let $maxId := max(for $i in //node/@id return xs:integer($i)) + 1\n" +
		"	let $isLoggedIn := xmldb:login('/db/C1', 'guest', 'guest')\n" +
		"	let $update :=\n" +
		"		<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">\n" +
		"			<xu:append select=\"/root\">\n" +
		"				<node id=\"{$maxId}\">appended node</node>\n" +
		"			</xu:append>\n" +
		"		</xu:modifications>\n" +
		"	return\n" +
		"		xmldb:update('/db/C1', $update)" +
		")";
	
	public XQueryUpdateAction(final String collectionPath, final String resourceName) {
		super(collectionPath, resourceName);
	}

	@Override
	public boolean execute() throws XMLDBException {
		final Collection col = DatabaseManager.getCollection(collectionPath);
		final XQueryService service = (XQueryService) col.getService("XQueryService", "1.0");
		
		service.query(query);
		return true;
	}
}
