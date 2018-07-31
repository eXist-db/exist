package org.exist.xmldb.concurrent.action;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XUpdateQueryService;

import static org.junit.Assert.assertEquals;

/**
 * @author wolf
 */
public class ComplexUpdateAction extends Action {

	private static final String sessionUpdate =
		"<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
		"<xu:update select=\"//USER-SESSION-DATA[1]\">" +
		"<xu:element name=\"USER-SESSION-STATUS\">" +
		"<xu:attribute name=\"access-type\">LAST-RESORT</xu:attribute>" +
		"<xu:attribute name=\"authServer\">10.12.1.10</xu:attribute>" +
		"<xu:attribute name=\"authprotocol\">NONE</xu:attribute>" +
		"<xu:attribute name=\"elapsed-time\">60000</xu:attribute>" +
		"<xu:attribute name=\"ip-addr\">192.168.1.97</xu:attribute>" +
		"<xu:attribute name=\"local-id\"></xu:attribute>" +
		"<xu:attribute name=\"mac-addr\">00:3f:cf:7f:8f:da</xu:attribute>" +
		"<xu:attribute name=\"session-id\">4917-AlphaMX3-(MX8)-Thu Sep 30 19:36:03 PDT 2004</xu:attribute>" +
		"<xu:attribute name=\"session-state\">ACTIVE</xu:attribute>" +
		"<xu:attribute name=\"ssid\">TRPZ-ENG</xu:attribute>" +
		"<xu:attribute name=\"start-time\">1096601394656</xu:attribute>" +
		"<xu:attribute name=\"user-name\">user137</xu:attribute>" +
		"<xu:attribute name=\"vlan-name\">default</xu:attribute>" +
		"<xu:attribute name=\"collected-time\">1096601435484</xu:attribute>" +
        "<USER-LOCATION-MEMBER ap-radio=\"1\" ap-type=\"AP\" dap=\"0\" " +
		"dp-system-ip=\"192.168.12.7\" module=\"1\" port=\"3\" " +
		"start-time=\"1096601358656\"/>" +
		"</xu:element>" +
		"<xu:element name=\"USER-SESSION-STATISTICS\">" +
		"<xu:attribute name=\"op-rate\">48</xu:attribute>" +
		"<xu:attribute name=\"rssi\">-65</xu:attribute>" +
		"<xu:attribute name=\"session-id\">4917-AlphaMX3-(MX8)-Thu Sep 30 19:36:03 PDT 2004</xu:attribute>" +
		"<xu:attribute name=\"snr\">50</xu:attribute>" +
		"<xu:attribute name=\"bps\">4448.6</xu:attribute>" +
		"<USER-SESSION-AP-ACCUM rx-badcrypt-bytes=\"55230\" rx-badcrypt-pkts=\"27576\" " +
		"rx-multi-bytes=\"55231\" rx-multi-pkts=\"27623\" rx-uni-bytes=\"55277\" " +
		"rx-uni-pkts=\"27555\" tx-timeouts=\"27554\" tx-uni-bytes=\"55250\" " +
		"tx-uni-pkts=\"27640\" type=\"CURRENT\"/>" +
        "<USER-SESSION-AP-ACCUM rx-badcrypt-bytes=\"88945\" " +
		"rx-badcrypt-pkts=\"29613\" rx-multi-bytes=\"88953\" "+
		"rx-multi-pkts=\"29614\" rx-uni-bytes=\"88998\" " +
		"rx-uni-pkts=\"29687\" tx-timeouts=\"29615\" " +
		"tx-uni-bytes=\"88966\" tx-uni-pkts=\"29614\" type=\"LIFETIME\"/>" +
        "</xu:element>" +
        "</xu:update>" +
        "</xu:modifications>";
	
	private static final String statusUpdate =
		"<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
		"<xu:update select=\"//USER-SESSION-DATA[1]/USER-SESSION-STATUS/@session-state\">INACTIVE</xu:update>" +
		"</xu:modifications>";
	
	private final int repeat;

	public ComplexUpdateAction(final String collectionPath, final String resourceName, final int repeat) {
		super(collectionPath, resourceName);
		this.repeat = repeat;
	}

	@Override
	public boolean execute() throws XMLDBException {
		final Collection col = DatabaseManager.getCollection(collectionPath, "admin", "");
		for(int i = 0; i < repeat; i++) {
			query(col, i); 
			col.close();
			
			update(col, sessionUpdate);
			// The following update will fail
			final String versionUpdate =
				"<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
				"<xu:update select=\"//USER-SESSION-DATA[1]/@version\">" + (i + 1) +
				"</xu:update></xu:modifications>";
			update(col, versionUpdate);
			update(col, statusUpdate);
		}
		return true;
	}

	private void query(final Collection col, final int repeat) throws XMLDBException {
		final XPathQueryService service = (XPathQueryService)col.getService("XPathQueryService", "1.0");
		ResourceSet r = service.query("//USER-SESSION-DATA");
		assertEquals(1, r.getSize());
		for(long i = 0; i < r.getSize(); i++) {
			XMLResource res = (XMLResource)r.getResource(i);
		}
		r = service.query("string(//USER-SESSION-DATA[1]/@version)");
		assertEquals(1, r.getSize());
		assertEquals(repeat, Integer.parseInt(r.getResource(0).getContent().toString()));
	}

	private void update(final Collection col, final String xupdate) throws XMLDBException {
		final XUpdateQueryService service = (XUpdateQueryService)
		col.getService("XUpdateQueryService", "1.0");
		long mods = service.updateResource(resourceName, xupdate);
	}
}
