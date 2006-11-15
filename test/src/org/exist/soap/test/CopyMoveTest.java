package org.exist.soap.test;

import java.net.URL;
import java.rmi.RemoteException;

import org.exist.soap.Admin;
import org.exist.soap.AdminService;
import org.exist.soap.AdminServiceLocator;
import org.exist.soap.Query;
import org.exist.soap.QueryServiceLocator;

import junit.framework.TestCase;
import org.exist.soap.QueryService;
import org.exist.start.Main;

public class CopyMoveTest extends TestCase {
    
    static Main mn = null;
    
    static String query_url = "http://localhost:8080/exist/services/Query";
    static String admin_url = "http://localhost:8080/exist/services/Admin";
    String testColl = "/db/test";
    
    Query query;
    Admin admin;
    String q_session;
    String a_session;
    
    public CopyMoveTest(String name) {
        super(name);
    }
    
    protected void setUp() throws Exception {
        
        if(mn==null){
            mn = new Main("jetty");
            mn.run(new String[]{"jetty"});
        }
        
        //super.setUp();
        QueryService service = new QueryServiceLocator();
        query = service.getQuery(new URL(query_url));
        q_session = query.connect("admin","");
        AdminService aservice = new AdminServiceLocator();
        admin = aservice.getAdmin(new URL(admin_url));
        a_session = admin.connect("admin","");
    }
    
    protected void tearDown() throws Exception {
        super.tearDown();
        try {
            query.disconnect(q_session);
            admin.disconnect(a_session);
        } catch (RemoteException rex) {
            rex.printStackTrace();
        }
        
        //mn.shutdown();
    }
    
    private void setupTestCollection() throws RemoteException {
        admin.removeCollection(a_session,testColl);
        admin.createCollection(a_session,testColl);
        
    }
    
    private void setupTestCollections() throws RemoteException {
        String collA = testColl + "/testA";
        admin.removeCollection(a_session,testColl);
        admin.createCollection(a_session,testColl);
        admin.createCollection(a_session,collA);
        admin.store(a_session,"<sample/>".getBytes(),"UTF-8",collA + "/docA",true);
        admin.store(a_session,"<sample/>".getBytes(),"UTF-8",collA + "/docB",true);
    }
    
    public void testCopyResourceChangeName() throws RemoteException {
        setupTestCollection();
        admin.store(a_session,"<sample/>".getBytes(),"UTF-8",testColl + "/original",true);
        admin.copyResource(a_session,testColl + "/original",testColl,"duplicate");
        String[] resources = query.listCollection(a_session,testColl).getResources().getElements();
        assertEquals(2,resources.length);
        assertTrue(resources[0].equals("duplicate") || resources[1].equals("duplicate"));
        String content = query.getResource(a_session,testColl + "/duplicate",true,false);
        System.out.println(content);
    }
    
    public void testMoveResource() throws RemoteException {
        setupTestCollection();
        admin.store(a_session,"<sample/>".getBytes(),"UTF-8",testColl + "/original",true);
        admin.moveResource(a_session,testColl + "/original",testColl,"duplicate");
        String[] resources = query.listCollection(a_session,testColl).getResources().getElements();
        assertEquals(1,resources.length);
        assertTrue(resources[0].equals("duplicate"));
    }
    
    public void testCopyCollectionChangeName() throws RemoteException {
        setupTestCollections();
        admin.copyCollection(a_session,testColl + "/testA",testColl,"testAcopy");
        String[] collections = query.listCollection(a_session,testColl).getCollections().getElements();
        assertEquals(collections.length,2);
        String[] resources = query.listCollection(a_session,testColl + "/testAcopy").getResources().getElements();
        assertEquals(resources.length,2);
    }
    
    public void testMoveCollection() throws RemoteException {
        setupTestCollections();
        admin.moveCollection(a_session,testColl + "/testA",testColl,"testAcopy");
        String[] collections = query.listCollection(a_session,testColl).getCollections().getElements();
        assertEquals(collections.length,1);
        assertEquals(collections[0],"testAcopy");
        String[] resources = query.listCollection(a_session,testColl + "/testAcopy").getResources().getElements();
        assertEquals(resources.length,2);
    }
    
    public void testRemoveThisEmptyTest() throws Exception {
//        assertEquals(1,1);
    }
}
