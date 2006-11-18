package org.exist.xmlrpc;

import junit.framework.TestCase;
import junit.textui.TestRunner;
import org.exist.StandaloneServer;
import org.exist.xmldb.XmldbURI;
import org.mortbay.util.MultiException;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.log4j.BasicConfigurator;

import java.util.Iterator;
import java.util.Vector;
import java.util.Hashtable;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.*;

/**
 * Test for deadlocks when moving resources from one collection to another.
 * Uses two threads: one stores a document, then moves it to another collection.
 * Based on XML-RPC. The second thread tries to execute a query via REST.
 *
 * Due to the complex move task, threads will deadlock almost immediately if 
 * something's wrong with collection locking.
 */
public class MoveResourceTest extends TestCase {

    public static void main(String[] args) {
        BasicConfigurator.configure();
        TestRunner.run(MoveResourceTest.class);
    }
    
    private StandaloneServer server;

    private final static String URI = "http://localhost:8088/xmlrpc";

    private final static String REST_URI = "http://localhost:8088";

    public MoveResourceTest(String string) {
        super(string);
    }

    public void testMove() {
        Thread thread1 = new MoveThread();
        Thread thread2 = new CheckThread();
        Thread thread3 = new CheckThread();

        thread1.start();
        thread2.start();
        thread3.start();
        try {
            thread1.join();
            thread2.join();
            thread3.join();
        } catch (InterruptedException e) {
        }

        System.out.println("DONE.");
    }
    
    private void createCollection(XmlRpcClient client, XmldbURI collection) throws IOException, XmlRpcException {
        Vector params = new Vector();
        params.addElement(collection.toString());
        Boolean result = (Boolean)client.execute("createCollection", params);
        assertTrue(result.booleanValue());
    }

    private String readData() throws IOException {
        String existHome = System.getProperty("exist.home");
        File existDir = existHome==null ? new File(".") : new File(existHome);
        File f = new File(existDir,"samples/shakespeare/r_and_j.xml");
        assertNotNull(f);

        Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuffer buf = new StringBuffer();
        char[] ch = new char[1024];
        int len;
        while ((len = reader.read(ch)) > 0) {
            buf.append(ch, 0, len);
        }
        return buf.toString();
    }

    private class MoveThread extends Thread {

        public void run() {
            for (int i = 0; i < 100; i++) {
                try {
                    XmldbURI sourceColl = XmldbURI.ROOT_COLLECTION_URI.append("source" + i);
                    XmldbURI targetColl1 = XmldbURI.ROOT_COLLECTION_URI.append("target");
                    XmldbURI targetColl2 = targetColl1.append("test" + i);
                    XmldbURI sourceResource = sourceColl.append("source.xml");
                    XmldbURI targetResource = targetColl2.append("copied.xml");

                    System.out.println("Creating collections ...");
                    XmlRpcClient xmlrpc = getClient();

                    createCollection(xmlrpc, sourceColl);
                    createCollection(xmlrpc, targetColl1);
                    createCollection(xmlrpc, targetColl2);

                    System.out.println("Storing document ...");
                    Vector params = new Vector();
                    params.addElement(readData());
                    params.addElement(sourceResource.toString());
                    params.addElement(new Integer(1));

                    Boolean result = (Boolean)xmlrpc.execute("parse", params);
                    assertTrue(result.booleanValue());

                    System.out.println("Document stored.");

                    System.out.println("Moving resource ...");
                    params.clear();
                    params.addElement(sourceResource.toString());
                    params.addElement(targetColl2.toString());
                    params.addElement("copied.xml");

                    xmlrpc.execute( "moveResource", params );

                    System.out.println("Retrieving document " + targetResource);
                    Hashtable options = new Hashtable();
                    options.put("indent", "yes");
                    options.put("encoding", "UTF-8");
                    options.put("expand-xincludes", "yes");
                    options.put("process-xsl-pi", "no");

                    params.clear();
                    params.addElement( targetResource.toString() );
                    params.addElement( options );

                    byte[] data = (byte[]) xmlrpc.execute( "getDocument", params );
                    assertTrue(data != null && data.length > 0);
//                    System.out.println( new String(data, "UTF-8") );

                    synchronized (this) {
                        wait(250);
                    }

                    System.out.println("Removing created collections ...");
                    params.clear();
                    params.addElement(sourceColl.toString());
                    xmlrpc.execute("removeCollection", params);

                    params.setElementAt(targetColl1.toString(), 0);
                    xmlrpc.execute("removeCollection", params);
                    System.out.println("Collections removed.");
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }
        }
    }

    private class CheckThread extends Thread {

        public void run() {
            String reqUrl = REST_URI + "/db?_query=" + URLEncoder.encode("collection('/db')//SPEECH[SPEAKER = 'JULIET']");
            for (int i = 0; i < 200; i++) {
                try {
                    URL url = new URL(reqUrl);
                    HttpURLConnection connect = (HttpURLConnection) url.openConnection();
                    connect.setRequestMethod("GET");
                    connect.connect();
    
                    int r = connect.getResponseCode();
                    assertEquals("Server returned response code " + r, 200, r);

                    System.out.println(readResponse(connect.getInputStream()));

                    synchronized (this) {
                        wait(250);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }
        }

        protected String readResponse(InputStream is) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                String line;
                StringBuffer out = new StringBuffer();
                while ((line = reader.readLine()) != null) {
                    out.append(line);
                    out.append("\r\n");
                }
                return out.toString();
            } catch (Exception e) {
                fail(e.getMessage());
            }
            return null;
        }
    }

    protected void setUp() {
		//Don't worry about closing the server : the shutdown hook will do the job
		initServer();
	}

    protected XmlRpcClient getClient() {
		try {
			XmlRpc.setEncoding("UTF-8");
			XmlRpcClient xmlrpc = new XmlRpcClient(URI);
			xmlrpc.setBasicAuthentication("admin", "");
			return xmlrpc;
	    } catch (Exception e) {
	        fail(e.getMessage());
	    }
	    return null;
	}

    private void initServer() {
		try {
			if (server == null) {
				server = new StandaloneServer();
				if (!server.isStarted()) {
					try {
						System.out.println("Starting standalone server...");
						String[] args = {};
						server.run(args);
						while (!server.isStarted()) {
							Thread.sleep(1000);
						}
					} catch (MultiException e) {
						boolean rethrow = true;
						Iterator i = e.getExceptions().iterator();
						while (i.hasNext()) {
							Exception e0 = (Exception)i.next();
							if (e0 instanceof BindException) {
								System.out.println("A server is running already !");
								rethrow = false;
								break;
							}
						}
						if (rethrow) throw e;
					}
				}
			}
	    } catch (Exception e) {
	        fail(e.getMessage());
	    }
	}
}
