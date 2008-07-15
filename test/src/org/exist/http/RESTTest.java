package org.exist.http;

import static org.junit.Assert.fail;

import java.net.BindException;
import java.util.Iterator;

import org.apache.commons.httpclient.HttpClient;
import org.exist.StandaloneServer;
import org.exist.storage.DBBroker;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mortbay.util.MultiException;

public abstract class RESTTest {

	protected final static String REST_URL = "http://localhost:8088";
	protected final static String COLLECTION_ROOT_URL = REST_URL
			+ DBBroker.ROOT_COLLECTION;

	private static StandaloneServer server = null;
	protected static HttpClient client = new HttpClient();

	@BeforeClass
	public static void startupServer() {
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
							Exception e0 = (Exception) i.next();
							if (e0 instanceof BindException) {
								System.out
										.println("A server is running already !");
								rethrow = false;
								break;
							}
						}
						if (rethrow)
							throw e;
					}
				}
			}
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	@AfterClass
	public static void shutdownServer() {
		server.shutdown();
	}
}
