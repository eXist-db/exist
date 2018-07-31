/*
 * Created on Sep 25, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.xmldb.concurrent.action;

import java.util.Random;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * @author wolf
 */
public class AttributeUpdateAction extends RemoveAppendAction {

	private static final String XUPDATE_START =
        "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">"
        + "<xu:update select=\"//ELEMENT/@attribute-1\">";
	
	private static final String XUPDATE_END =
		"</xu:update>" +
		"</xu:modifications>";
	
	private final Random rand = new Random();

	public AttributeUpdateAction(final String collectionPath, final String resourceName, final String[] wordList) {
		super(collectionPath, resourceName, wordList);
	}

	@Override
	public boolean execute() throws XMLDBException {
		final Collection col = DatabaseManager.getCollection(collectionPath, "admin", "");
		final XUpdateQueryService service = (XUpdateQueryService) col.getService("XUpdateQueryService", "1.0");
		final int attrSize = rand.nextInt(5);
		for (int i = 0; i < 10; i++) {
			final String xupdate = XUPDATE_START + xmlGenerator.generateText(attrSize) + XUPDATE_END;
			long mods = service.update(xupdate);
		}
		return true;
	}
}
