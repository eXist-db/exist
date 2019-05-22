package org.exist.http;

import org.exist.test.ExistWebServer;
import org.exist.xmldb.XmldbURI;
import org.junit.ClassRule;

public abstract class RESTTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    protected static String getRestUrl() {
        return "http://localhost:" + existWebServer.getPort();
    }

    protected static String getCollectionRootUri() {
        return getRestUrl() + XmldbURI.ROOT_COLLECTION;
    }
}
