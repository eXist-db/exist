package org.exist.examples.soap;

import org.exist.soap.*;

public class XUpdateExample {

	private final static String document =
		"<?xml version=\"1.0\"?>" +
		"<notes>" +
		"<note id=\"1\">Complete documentation.</note>" +
		"</notes>";
	
	private final static String xupdate =
		"<?xml version=\"1.0\"?>" +
		"<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
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
		admin.store(session, document.getBytes("UTF-8"), "UTF-8", "/db/test/notes.xml", true);
		admin.xupdateResource(session, "/db/test/notes.xml", xupdate);
		
		String data = query.getResource(session, 
			"/db/test/notes.xml",
			true, false);
		System.out.println(data);
		admin.disconnect(session);
    }
}

