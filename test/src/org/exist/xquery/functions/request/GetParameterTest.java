package org.exist.xquery.functions.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.exist.http.RESTTest;
import org.exist.xmldb.EXistResource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

/**
 * Tests expected behaviour of request:get-parameter() XQuery function
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */
public class GetParameterTest extends RESTTest {

    private final static String XQUERY = "for $param-name in request:get-parameter-names() return for $param-value in request:get-parameter($param-name, ()) return fn:concat($param-name, '=', $param-value)";
    private final static String XQUERY_FILENAME = "test-get-parameter.xql";

    private static Collection root;

    
    @BeforeClass
    public static void beforeClass() throws XMLDBException {
        root = DatabaseManager.getCollection("xmldb:exist://localhost:8088/xmlrpc/db", "admin", "");
        BinaryResource res = (BinaryResource)root.createResource(XQUERY_FILENAME, "BinaryResource");
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(XQUERY);
        root.storeResource(res);
    }

    @AfterClass
    public static void afterClass() throws XMLDBException {
        BinaryResource res = (BinaryResource)root.getResource(XQUERY_FILENAME);
        root.removeResource(res);
    }

    @Test
    public void testGetNoParameter() throws XMLDBException {
        testGet(null);
    }

    @Test
    public void testPostNoParameter() throws XMLDBException {
        testPost(null);
    }

    @Test
    public void testGetEmptyParameter() {
        testGet(new NameValues[] {
            new NameValues("param1", new String[]{})
        });
    }

    @Test
    public void testPostEmptyParameter() {
        testPost(new NameValues[] {
            new NameValues("param1", new String[]{})
        });
    }

    @Test
    public void testGetSingleValueParameter() {
        testGet(new NameValues[] {
            new NameValues("param1", new String[] {
                "value1"
            })
        });
    }

    @Test
    public void testPostSingleValueParameter() {
        testPost(new NameValues[] {
            new NameValues("param1", new String[] {
                "value1"
            })
        });
    }

    @Test
    public void testGetMultiValueParameter() {
        testGet(new NameValues[]{
            new NameValues("param1", new String[] {
                "value1",
                "value2",
                "value3",
                "value4"
            })
        });
    }

    @Test
    public void testPostMultiValueParameter() {
        testPost(new NameValues[]{
            new NameValues("param1", new String[] {
                "value1",
                "value2",
                "value3",
                "value4"
            })
        });
    }

    @Test
    public void testPostMultiValueParameterWithQueryStringMultiValueParameter() {
        testPost(
            new NameValues[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                }),
            },
            new NameValues[]{
                new NameValues("param2", new String[] {
                    "valueA",
                    "valueB",
                    "valueC",
                    "valueD"
                }),
            }
        );
    }

    @Test
    public void testPostMultiValueParameterWithQueryStringMultiValueParameterMerge() {
        testPost(
            new NameValues[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                }),
            },
            new NameValues[]{
                new NameValues("param1", new String[] {
                    "valueA",
                    "valueB",
                    "valueC",
                    "valueD"
                }),
            }
        );
    }

    private void testGet(NameValues queryStringParams[]) {

        StringBuilder expectedResponse = new StringBuilder();
        NameValuePair qsParams[] = convertNameValuesToNameValuePairs(queryStringParams, expectedResponse);

        GetMethod get = new GetMethod(COLLECTION_ROOT_URL + "/" + XQUERY_FILENAME);
        if(qsParams.length > 0) {
            get.setQueryString(qsParams);
        }

        testRequest(get, expectedResponse);
    }

    private void testPost(NameValues formParams[]) {

        StringBuilder expectedResponse = new StringBuilder();
        NameValuePair fParams[] = convertNameValuesToNameValuePairs(formParams, expectedResponse);

        PostMethod post = new PostMethod(COLLECTION_ROOT_URL + "/" + XQUERY_FILENAME);

        if(fParams.length > 0) {
            post.setRequestBody(fParams);
        }

        testRequest(post, expectedResponse);
    }

    private void testPost(NameValues queryStringParams[], NameValues formParams[]) {

        StringBuilder expectedResponse = new StringBuilder();
        NameValuePair qsParams[] = convertNameValuesToNameValuePairs(queryStringParams, expectedResponse);
        NameValuePair fParams[] = convertNameValuesToNameValuePairs(formParams, expectedResponse);

        PostMethod post = new PostMethod(COLLECTION_ROOT_URL + "/" + XQUERY_FILENAME);

        if(qsParams.length > 0) {
            post.setQueryString(qsParams);
        }

        if(fParams.length > 0) {
            post.setRequestBody(fParams);
        }

        testRequest(post, expectedResponse);
    }

    private void testRequest(HttpMethod method, StringBuilder expectedResponse) {

        try {
            int httpResult = client.executeMethod(method);

            byte buf[] = new byte[1024];
            int read = -1;
            StringBuilder responseBody = new StringBuilder();
            InputStream is = method.getResponseBodyAsStream();
            while((read = is.read(buf)) > -1) {
                responseBody.append(new String(buf, 0, read));
            }

            assertEquals(HttpStatus.SC_OK, httpResult);

            assertEquals(expectedResponse.toString(), responseBody.toString());

        } catch(HttpException he) {
            fail(he.getMessage());
        } catch(IOException ioe) {
            fail(ioe.getMessage());
        } finally {
            method.releaseConnection();
        }
    }

    private NameValuePair[] convertNameValuesToNameValuePairs(NameValues nameValues[], StringBuilder expectedResponse) {

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();

        if(nameValues != null) {
            for(NameValues param : nameValues) {
                for(String paramValue : param.getValues()) {
                    nameValuePairs.add(new NameValuePair(param.getName(), paramValue));

                    expectedResponse.append(param.getName());
                    expectedResponse.append("=");
                    expectedResponse.append(paramValue);
                }
            }
        }

        return nameValuePairs.toArray(new NameValuePair[nameValuePairs.size()]);
    }

    public class NameValues {

        final String name;
        final String values[];

        public NameValues(String name, String values[]) {
            this.name = name;
            this.values = values;
        }

        public String getName() {
            return name;
        }

        public String[] getValues() {
            return values;
        }

    }
}
