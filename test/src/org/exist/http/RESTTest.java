package org.exist.http;

import org.apache.commons.httpclient.HttpClient;
import org.exist.jetty.JettyStart;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import static org.junit.Assert.fail;

public abstract class RESTTest {

    // jetty.port.standalone
    protected final static String REST_URL = "http://localhost:" + System.getProperty("jetty.port");
    protected final static String COLLECTION_ROOT_URL = REST_URL + XmldbURI.ROOT_COLLECTION;
    protected static JettyStart server = null;
    protected static HttpClient client = new HttpClient();

    @BeforeClass
    public static void startupServer() {
        try {
            if(server == null) {
                server = new JettyStart();
                server.run();
            }
        } catch(Exception e) {
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void shutdownServer() {
        server.shutdown();
    }
}
