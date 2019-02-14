package org.exist.xquery.functions.request;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.PartSource;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.exist.http.RESTTest;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.UserManagementService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
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

    private final static String TEST_FILE_CONTENT = "hello world";
    private final static String TEST_FILE_NAME = "helloworld.txt";

    private static Collection root;

    
    @BeforeClass
    public static void beforeClass() throws XMLDBException {
        root = DatabaseManager.getCollection("xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc/db", "admin", "");
        BinaryResource res = (BinaryResource)root.createResource(XQUERY_FILENAME, "BinaryResource");
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(XQUERY);
        root.storeResource(res);
        UserManagementService ums = (UserManagementService)root.getService("UserManagementService", "1.0");
        ums.chmod(res, 0777);
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

    @Test
    public void testMultipartPostMultiValueParameterAndFile() {
        testMultipartPost(
            new Param[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                }),
                new TextFileUpload(TEST_FILE_NAME, TEST_FILE_CONTENT)
            }
        );
    }

    @Test
    public void testMultipartPostFileAndMultiValueParameter() {
        testMultipartPost(
            new Param[]{
                new TextFileUpload(TEST_FILE_NAME, TEST_FILE_CONTENT),
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                })
            }
        );
    }

    @Test
    public void testMultipartPostMultiValueParameterAndFileAndMultiValueParameter() {
        testMultipartPost(
            new Param[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                }),
                new TextFileUpload(TEST_FILE_NAME, TEST_FILE_CONTENT),
                new NameValues("param2", new String[] {
                    "valueA",
                    "valueB",
                    "valueC",
                    "valueD"
                })
            }
        );
    }

    @Test
    public void testMultipartPostAndMultiValueParameterAndFileAndMultiValueParameterWithQueryStringMultiValueParameters() {
        testMultipartPost(
            new NameValues[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                })
            },
            new Param[]{
                new NameValues("param2", new String[] {
                    "valueA",
                    "valueB",
                    "valueC",
                    "valueD"
                }),
                new TextFileUpload(TEST_FILE_NAME, TEST_FILE_CONTENT),
                new NameValues("param3", new String[] {
                    "valueZ",
                    "valueY",
                    "valueX",
                    "valueW"
                })
            }
        );
    }

    @Test
    public void testMultipartPostAndMultiValueParameterAndFileAndMultiValueParameterWithQueryStringMultiValueParametersMerged() {
        testMultipartPost(
            new NameValues[]{
                new NameValues("param1", new String[] {
                    "value1",
                    "value2",
                    "value3",
                    "value4"
                })
            },
            new Param[]{
                new NameValues("param1", new String[] {
                    "value5",
                    "value6",
                    "value7",
                    "value8"
                }),
                new TextFileUpload(TEST_FILE_NAME, TEST_FILE_CONTENT),
                new NameValues("param2", new String[] {
                    "valueA",
                    "valueB",
                    "valueC",
                    "valueD"
                })
            }
        );
    }

    private void testGet(NameValues queryStringParams[]) {

        StringBuilder expectedResponse = new StringBuilder();
        NameValuePair qsParams[] = convertNameValuesToNameValuePairs(queryStringParams, expectedResponse);

        GetMethod get = new GetMethod(getCollectionRootUri() + "/" + XQUERY_FILENAME);
        if(qsParams.length > 0) {
            get.setQueryString(qsParams);
        }

        testRequest(get, expectedResponse);
    }

    private void testPost(NameValues formParams[]) {

        StringBuilder expectedResponse = new StringBuilder();
        NameValuePair fParams[] = convertNameValuesToNameValuePairs(formParams, expectedResponse);

        PostMethod post = new PostMethod(getCollectionRootUri() + "/" + XQUERY_FILENAME);

        if(fParams.length > 0) {
            post.setRequestBody(fParams);
        }

        testRequest(post, expectedResponse);
    }

    private void testPost(NameValues queryStringParams[], NameValues formParams[]) {

        StringBuilder expectedResponse = new StringBuilder();
        NameValuePair qsParams[] = convertNameValuesToNameValuePairs(queryStringParams, expectedResponse);
        NameValuePair fParams[] = convertNameValuesToNameValuePairs(formParams, expectedResponse);

        PostMethod post = new PostMethod(getCollectionRootUri() + "/" + XQUERY_FILENAME);

        if(qsParams.length > 0) {
            post.setQueryString(qsParams);
        }

        if(fParams.length > 0) {
            post.setRequestBody(fParams);
        }

        testRequest(post, expectedResponse);
    }

    private void testMultipartPost(Param multipartParams[]) {
       
        List<Part> parts = new ArrayList<Part>();

        StringBuilder expectedResponse = new StringBuilder();

        for(Param multipartParam : multipartParams) {
            if(multipartParam instanceof NameValues) {
                for(NameValuePair nameValuePair : convertNameValueToNameValuePairs((NameValues)multipartParam, expectedResponse)) {
                    parts.add(new StringPart(nameValuePair.getName(), nameValuePair.getValue()));
                }
            } else if(multipartParam instanceof TextFileUpload) {
                parts.add(convertFileUploadToFilePart((TextFileUpload)multipartParam, expectedResponse));
            }
        }

        PostMethod post = new PostMethod(getCollectionRootUri() + "/" + XQUERY_FILENAME);
        post.setRequestEntity(new MultipartRequestEntity(parts.toArray(new Part[parts.size()]), post.getParams()));

        testRequest(post, expectedResponse);
    }

    private void testMultipartPost(NameValues queryStringParams[], Param multipartParams[]) {

        List<Part> parts = new ArrayList<Part>();

        StringBuilder expectedResponse = new StringBuilder();

        NameValuePair qsParams[] = convertNameValuesToNameValuePairs(queryStringParams, expectedResponse);

        for(Param multipartParam : multipartParams) {
            if(multipartParam instanceof NameValues) {
                for(NameValuePair nameValuePair : convertNameValueToNameValuePairs((NameValues)multipartParam, expectedResponse)) {
                    parts.add(new StringPart(nameValuePair.getName(), nameValuePair.getValue()));
                }
            } else if(multipartParam instanceof TextFileUpload) {
                parts.add(convertFileUploadToFilePart((TextFileUpload)multipartParam, expectedResponse));
            }
        }

        PostMethod post = new PostMethod(getCollectionRootUri() + "/" + XQUERY_FILENAME);

        if(qsParams.length > 0) {
            post.setQueryString(qsParams);
        }

        post.setRequestEntity(new MultipartRequestEntity(parts.toArray(new Part[parts.size()]), post.getParams()));

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

        } catch(IOException ioe) {
            fail(ioe.getMessage());
        } finally {
            method.releaseConnection();
        }
    }

    private NameValuePair[] convertNameValuesToNameValuePairs(final NameValues nameValues[], final StringBuilder expectedResponse) {
        final List<NameValuePair> nameValuePairs = new ArrayList<>();

        if(nameValues != null) {
            for(final NameValues param : nameValues) {
                nameValuePairs.addAll(convertNameValueToNameValuePairs(param, expectedResponse));
            }
        }

        return nameValuePairs.toArray(new NameValuePair[nameValuePairs.size()]);
    }

    private List<NameValuePair> convertNameValueToNameValuePairs(final NameValues nameValues, final StringBuilder expectedResponse) {
        final List<NameValuePair> nameValuePairs = new ArrayList<>();

        for(final String paramValue : nameValues.getData()) {
            nameValuePairs.add(new NameValuePair(nameValues.getName(), paramValue));

            expectedResponse.append(nameValues.getName());
            expectedResponse.append("=");
            expectedResponse.append(paramValue);
        }

        return nameValuePairs;
    }

    private FilePart convertFileUploadToFilePart(final TextFileUpload txtFileUpload, final StringBuilder expectedResponse) {

        final String filePartName = "fileUpload";

        final FilePart filePart = new FilePart(filePartName, new PartSource() {
            private byte data[] = txtFileUpload.getData().getBytes();

            @Override
            public long getLength() {
                return data.length;
            }

            @Override
            public String getFileName() {
                return txtFileUpload.getName();
            }

            @Override
            public InputStream createInputStream() throws IOException {
                return new FastByteArrayInputStream(data);
            }
        });

        expectedResponse.append(filePartName);
        expectedResponse.append("=");
        expectedResponse.append(txtFileUpload.getData());

        return filePart;
    }

    public class NameValues implements Param<String[]> {

        final String name;
        final String values[];

        public NameValues(final String name, final String values[]) {
            this.name = name;
            this.values = values;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String[] getData() {
            return values;
        }
    }

    public class TextFileUpload implements Param<String> {
        final String name;
        final String content;

        public TextFileUpload(final String name, final String content) {
            this.name = name;
            this.content = content;
        }

        @Override
        public String getData() {
            return content;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public interface Param<T> {
        String getName();
        T getData();
    }
}
