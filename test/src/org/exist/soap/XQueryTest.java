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

        admin.store(sessionId,data.getBytes(),"UTF-8",testColl + "/docA",true);
        admin.store(sessionId,data1.getBytes(),"UTF-8",testColl + "/docB",true);

        String rd = query.getResource(sessionId,testColl + "/docA", true,false);

        Collection coll = query.listCollection(sessionId,testColl);
        String[] colls = coll.getCollections().getElements();
        String[] ress = coll.getResources().getElements();
        assertEquals(ress.length,2);

        byte[] rd1 = query.getResourceData(sessionId,testColl + "/docB", true,false,false);

        String qry = "for $a in collection('" + testColl + "')/test/fruit return $a";
        assertEquals(doXQuery(qry),12);

        assertEquals(doXQueryB(qry),12);

        assertEquals(doXQueryC(qry),6);

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
            assertEquals(noHits,rsps.length);
        }
        return noHits;
    }
    
    private int doXQueryB(String qry) throws RemoteException {
        QueryResponse rsp = query.xquery(sessionId,qry.getBytes());
        int noHits = rsp.getHits();
        if (noHits > 0) {
            byte[][] rsps = query.retrieveData(sessionId,1,noHits,true,false,"none").getElements();
            assertEquals(noHits,rsps.length);
        }
        return noHits;
    }
    
    private int doXQueryC(String qry) throws RemoteException {
        QueryResponse rsp = query.xquery(sessionId,qry.getBytes());
        int noHits = rsp.getHits();
        if (noHits > 0) {
            String[] rsps = query.retrieveByDocument(sessionId,1,noHits,testColl + "/docA",true,false,"none");
            noHits = rsps.length;
//			assertEquals(noHits,rsps.length);
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
