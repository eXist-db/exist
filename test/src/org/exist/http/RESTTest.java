package org.exist.http;

import org.apache.commons.httpclient.HttpClient;
import org.exist.test.ExistWebServer;
import org.exist.xmldb.XmldbURI;
import org.junit.ClassRule;

public abstract class RESTTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true);

    protected static String getRestUrl() {
        return "http://localhost:" + existWebServer.getPort();
    }

    protected static String getCollectionRootUri() {
        return getRestUrl() + XmldbURI.ROOT_COLLECTION;
    }

    protected static HttpClient client = new HttpClient();
}
