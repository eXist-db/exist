package org.exist.examples.soap;

import org.exist.soap.Admin;
import org.exist.soap.AdminService;
import org.exist.soap.AdminServiceLocator;
import org.exist.soap.Query;
import org.exist.soap.QueryService;
import org.exist.soap.QueryServiceLocator;
import org.exist.storage.DBBroker;
import org.exist.xupdate.XUpdateProcessor;

public class XUpdateExample {

	private final static String document =
		"<?xml version=\"1.0\"?>" +
		"<notes>" +
		"<note id=\"1\">Complete documentation.</note>" +
		"</notes>";
	
	private final static String xupdate =
		"<?xml version=\"1.0\"?>" +
		"<xu:modifications version=\"1.0\" xmlns:xu=\"" + XUpdateProcessor.XUPDATE_NS + "\">" +
		"<xu:insert-after select=\"//note[1]\">" +
		"<xu:element name=\"note\">" +
		"<xu:attribute name=\"id\">2</xu:attribute>" +
		"Complete change log." +
		"</xu:element>" +
		"</xu:insert-after>" +
		"</xu:modifications>";
		
    public static void main( String[] args ) throws Exception {
        AdminService adminService = new AdminServiceLocator();
        Admin admin = adminService.getAdmin();
        QueryService queryService = new QueryServiceLocator();
        Query query = queryService.getQuery();
        
		String session = admin.connect("guest", "guest");
		admin.store(session, document.getBytes("UTF-8"), "UTF-8", DBBroker.ROOT_COLLECTION + "/test/notes.xml", true);
		admin.xupdateResource(session, DBBroker.ROOT_COLLECTION + "/test/notes.xml", xupdate);
		
		String data = query.getResource(session, 
			DBBroker.ROOT_COLLECTION + "/test/notes.xml",
			true, true);
		System.out.println(data);
		admin.disconnect(session);
    }
}

