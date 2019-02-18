/**
 * 
 */
package org.exist.http.underheavyload;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author dmitriy
 *
 */
public class Client implements Runnable {

	private static final Logger LOG = LogManager.getLogger(Client.class);

    private boolean running;

    private ClientsManager manager;

	public Client() {
		running = true; 
	}

	public Client(ClientsManager clients) {
		this.manager = clients;
		running = true; 
	}
	
//	@Override
	public void run() {
		HttpClient client = new HttpClient();

		// connect to a login page to retrieve session ID
		PostMethod method = new PostMethod(getURL());

        // post auth information with it
        method.setParameter("username", "admin");
        method.setParameter("password", "");
		
		try {
			client.executeMethod(method);
			String redirectLocation = null;
            Header locationHeader = method.getResponseHeader("location");
            if (locationHeader != null) {
                redirectLocation = locationHeader.getValue();
            } else {
                // The response is invalid and did not provide the new location for
                // the resource. Report an error or possibly handle the response
                // like a 404 Not Found error.
                //LOG.debug(method.getResponseBodyAsString());
            }
            method.setURI(new URI(redirectLocation, true));
            client.executeMethod(method);

            // store the session info for the next call
            Header[] headers = method.getResponseHeaders();
            
            //TODO: fetch links
            
			while (running) {
            	Thread.sleep(1000);

            	// connect to a page you're interested...
            	PostMethod getMethod = new PostMethod("http://localhost:" + System.getProperty("jetty.port") + "/exist/admin/admin.xql?panel=xqueries");

            	// ...using the session ID retrieved before
            	for (Header header : headers) {
            		getMethod.setRequestHeader(header);
            	}
            	client.executeMethod(method);

            	// log the page source
            	//LOG.info(method.getResponseBodyAsString());
            }
		} catch (Exception e) {
			LOG.error(e);
		}
	}
	
	protected void shutdown() {
		running = false; 
	}

	private String getURL() {
		if (manager == null)
		    return "http://localhost:" + System.getProperty("jetty.port") + "/exist/admin";
		else 
			return manager.getURL();
	}

	public static void main(String[] args) {
        Client client = new Client();
        client.run();
    }
}
