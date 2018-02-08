package org.exist.extensions.exquery.restxq.impl.multipart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.http.RESTTest;
import org.exist.util.Base64Encoder;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.UserManagementService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

public abstract class MultipartTest extends RESTTest {
	
    protected static final String DRIVER = "org.exist.xmldb.DatabaseImpl";
    
    protected static final String USER = "admin";
    protected static final String PW = "";
    
    protected static final String RESTXQ_TEST_FOLDER = "extensions/exquery/restxq/src/test";
    protected static final String MULTIPART_IMPL_FOLDER = "java/org/exist/extensions/exquery/restxq/impl/multipart";
    protected static final String XQUERY_FILENAME = "MultipartTestEndpoint.xql";
    
    protected static final String XQUERY_PATH = RESTXQ_TEST_FOLDER + "/" + MULTIPART_IMPL_FOLDER + "/" + XQUERY_FILENAME;
    
    protected static final String REQUEST_URL = "http://localhost:8080/exist/restxq";
    
    protected static Collection root;
    
    protected static BinaryResource res;
    

    @BeforeClass
	public static void beforeCalss() throws ClassNotFoundException, InstantiationException, IllegalAccessException, XMLDBException, IOException {
    	final Class<?> cl = Class.forName(DRIVER);
        final Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);
        
        root = DatabaseManager.getCollection("xmldb:exist://localhost:" + existWebServer.getPort() + "/exist/xmlrpc/db", USER, PW);
		
		final File xqueryFile = new File(XQUERY_PATH);
		if(!xqueryFile.canRead()){
			throw new IOException("cannot read file: " + XQUERY_PATH);
		}
		
		res = (BinaryResource)root.createResource(XQUERY_PATH, "BinaryResource");
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(xqueryFile);
        root.storeResource(res);
        UserManagementService ums = (UserManagementService)root.getService("UserManagementService", "1.0");
        ums.chmod(res, 0777);
    }
    
    @AfterClass
    public static void afterClass() throws XMLDBException {
        root.removeResource(res);
    }
    
	protected PostMethod sendPostRequest(final String url, final String request, final String contentTypeHeader) throws HttpException, IOException, ParserConfigurationException, SAXException{
        final PostMethod post = new PostMethod(url);
        final Base64Encoder enc = new Base64Encoder();
        enc.translate((USER + ":" + PW).getBytes());
        post.addRequestHeader("Authorization", "Basic " + new String(enc.getCharArray()));
        post.addRequestHeader("Content-Type", contentTypeHeader);
        
        final RequestEntity requestEntity = new StringRequestEntity(request, "multipart/related; boundary='TEST_BOUNDARY'", "UTF-8");
		post.setRequestEntity(requestEntity);
		client.executeMethod(post);
		
		return post;
    }
    
    protected String readResponse(final HttpMethod methode) throws IOException{
    	final byte buf[] = new byte[1024];
        int read = -1;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final InputStream is = methode.getResponseBodyAsStream();
        while((read = is.read(buf)) > -1) {
            baos.write(buf, 0, read);
        }
        final byte actualResponse[] = baos.toByteArray();
		baos.close();
        
        return new String(actualResponse);
    }

}
