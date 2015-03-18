package org.exist.soap;

import java.net.URL;
import java.rmi.RemoteException;

import junit.framework.TestCase;
import org.exist.start.Main;

public class UsersAndPermissionsTest extends TestCase {
    
    static Main mn = null;
    
    String testUser = "BertieBeetle";
    String testPassword = "srfg.hj7Ld-";
    String testHome = "/db/home/BertieBeetle";
    String testGroup = "BertiesGroup";
    String testColl = "/db/test";
    
    Query query;
    Admin admin;
    String sessionId;
    
    public UsersAndPermissionsTest(String arg0) {
        super(arg0);
    }
    
    protected void setUp() throws Exception {
        
        if(mn==null){
            mn = new Main("jetty");
            mn.run(new String[]{"jetty"});
        }
        
        QueryService service = new QueryServiceLocator();
        query = service.getQuery(new URL(XQueryTest.query_url));
        AdminService aservice = new AdminServiceLocator();
        admin = aservice.getAdmin(new URL(XQueryTest.admin_url));
        sessionId = admin.connect("admin","");
    }
    
    protected void tearDown() throws Exception {
        super.tearDown();
        try {
            admin.disconnect(sessionId);
        } catch (RemoteException rex) {
            rex.printStackTrace();
        }
    }
    
    public void testCreateUser() throws RemoteException {
        UserDesc desc;
        try {
            desc = admin.getUser(sessionId,testUser);
            admin.removeUser(sessionId,testUser);
        } catch (RemoteException rex) {
            
        }
        String[] testGroups = {testGroup};
        admin.setUser(sessionId,testUser,testPassword,new Strings(testGroups),testHome);
        desc = admin.getUser(sessionId,testUser);
        assertNotNull(desc);
        
        // Create a test resource
        admin.removeCollection(sessionId,testColl);
        admin.createCollection(sessionId,testColl);
        String res = testColl + "/original";
        admin.store(sessionId,"<sample/>".getBytes(),"UTF-8",res,true);
        
        Permissions perms = admin.getPermissions(sessionId,res);

        admin.setPermissions(sessionId,res,testUser,testGroup,0777);
        
        Permissions newperms = admin.getPermissions(sessionId,res);
        
        assertEquals(newperms.getOwner(),testUser);
        assertEquals(newperms.getGroup(),testGroup);
        assertEquals(newperms.getPermissions(),0777);

        admin.setPermissions(sessionId,res,perms.getOwner(),perms.getGroup(),perms.getPermissions());

        admin.lockResource(sessionId,res,testUser);
        String lockOwner = admin.hasUserLock(sessionId,res);
        assertEquals(lockOwner,testUser);

        admin.unlockResource(sessionId,res);
        
        perms = admin.getPermissions(sessionId,res);

        admin.removeUser(sessionId,testUser);
        try {
            desc = admin.getUser(sessionId,testUser);
            assertTrue(false);
        } catch (RemoteException rex) {
            
        }
    }
}
