package org.exist.soap;

import java.net.URL;
import java.rmi.RemoteException;

import junit.framework.TestCase;
import org.exist.start.Main;

public class XQueryTest extends TestCase {
    
    static Main mn = null;
    
    static String jetty_port = System.getProperty("jetty.port");
    static String localhost = "http://localhost:" + jetty_port;
    static String query_url = localhost + "/exist/services/Query";
    static String admin_url = localhost + "/exist/services/Admin";

    String testColl = "/db/test";
    
    Query query;
    Admin admin;
    String sessionId;
    
    public XQueryTest(String arg0) {
        super(arg0);
    }
    
    public void testXQuery() throws RemoteException {
        admin.removeCollection(sessionId,testColl);
        admin.createCollection(sessionId,testColl);
        String data = "<test>" +
                "  <fruit name='apple'/>" +
                "  <fruit name='orange'/>" +
                "  <fruit name='pear'/>" +
                "  <fruit name='grape'/>" +
                "  <fruit name='banana'/>" +
                "  <fruit name='mango'/>" +
                "</test>";
        String data1 = "<test>" +
                "  <fruit name='guava'/>" +
                "  <fruit name='quince'/>" +
                "  <fruit name='pineapple'/>" +
                "  <fruit name='mandarine'/>" +
                "  <fruit name='persimmon'/>" +
                "  <fruit name='pomegranate'/>" +
                "</test>";
        System.out.println("====> Creating test documents");
        admin.store(sessionId,data.getBytes(),"UTF-8",testColl + "/docA",true);
        admin.store(sessionId,data1.getBytes(),"UTF-8",testColl + "/docB",true);
        System.out.println("====> getResource");
        String rd = query.getResource(sessionId,testColl + "/docA", true,false);
        System.out.println(rd);
        System.out.println("====> listCollection");
        Collection coll = query.listCollection(sessionId,testColl);
        String[] colls = coll.getCollections().getElements();
        if (colls != null)
            for (int i = 0; i < colls.length; i++) {
            System.out.println("  collection " + colls[i]);
            }
        String[] ress = coll.getResources().getElements();
        assertEquals(ress.length,2);
        if (ress != null)
            for (int i = 0; i < ress.length; i++) {
            System.out.println("  resources " + ress[i]);
            }
        System.out.println("====> getResourceData");
        byte[] rd1 = query.getResourceData(sessionId,testColl + "/docB", true,false,false);
        System.out.println(new String(rd1));
        System.out.println("====> performing xquery with retrieve");
        String qry = "for $a in collection('" + testColl + "')/test/fruit return $a";
        assertEquals(doXQuery(qry),12);
        System.out.println("====> performing xquery with retrieveData");
        assertEquals(doXQueryB(qry),12);
        System.out.println("====> performing xquery with retrieveByDocument");
        assertEquals(doXQueryC(qry),6);
        System.out.println("====> performing xquery, expecting 0 hits");
        String qry1 = "for $a in collection('" + testColl + "')/test/nuts return $a";
        assertEquals(doXQuery(qry1),0);
        String qry2 = "for $a in collection('" + testColl + "')/test/fruit[@name = 'apple'] return $a";
        assertEquals(doXQuery(qry2),1);
    }
    
    private int doXQuery(String qry) throws RemoteException {
        QueryResponse rsp = query.xquery(sessionId,qry.getBytes());
        int noHits = rsp.getHits();
        if (noHits > 0) {
            String[] rsps = query.retrieve(sessionId,1,noHits,true,false,"none");
            for (int i = 0; i < rsps.length; i++) {
                System.out.println(rsps[i]);
            }
            assertEquals(noHits,rsps.length);
        } else {
            System.out.println("No hits");
        }
        return noHits;
    }
    
    private int doXQueryB(String qry) throws RemoteException {
        QueryResponse rsp = query.xquery(sessionId,qry.getBytes());
        int noHits = rsp.getHits();
        if (noHits > 0) {
            byte[][] rsps = query.retrieveData(sessionId,1,noHits,true,false,"none").getElements();
            for (int i = 0; i < rsps.length; i++) {
                System.out.println(new String(rsps[i]));
            }
            assertEquals(noHits,rsps.length);
        } else {
            System.out.println("No hits");
        }
        return noHits;
    }
    
    private int doXQueryC(String qry) throws RemoteException {
        QueryResponse rsp = query.xquery(sessionId,qry.getBytes());
        int noHits = rsp.getHits();
        if (noHits > 0) {
            String[] rsps = query.retrieveByDocument(sessionId,1,noHits,testColl + "/docA",true,false,"none");
            for (int i = 0; i < rsps.length; i++) {
                System.out.println(rsps[i]);
            }
            noHits = rsps.length;
//			assertEquals(noHits,rsps.length);
        } else {
            System.out.println("No hits");
        }
        return noHits;
    }
    
    protected void setUp() throws Exception {
        
        if(mn==null){
            mn = new Main("jetty");
            mn.run(new String[]{"jetty"});
        }
        
        QueryService service = new QueryServiceLocator();
        query = service.getQuery(new URL(query_url));
        sessionId = query.connect("admin","");
        AdminService aservice = new AdminServiceLocator();
        admin = aservice.getAdmin(new URL(admin_url));
    }
    
    protected void tearDown() throws Exception {
        
        try {
            query.disconnect(sessionId);
        } catch (RemoteException rex) {
            rex.printStackTrace();
        }
        //mn.shutdownDB();
    }
    
    public void testRemoveThisEmptyTest() throws Exception {
//        assertEquals(1,1);
    }
    
}
