package org.exist.http.urlrewrite;

import java.util.HashMap;
import java.util.Map;

import com.googlecode.junittoolbox.ParallelRunner;
import org.easymock.EasyMock;
import javax.servlet.http.HttpServletRequest;
import org.exist.http.urlrewrite.XQueryURLRewrite.RequestWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 *
 * @author aretter
 */
@RunWith(ParallelRunner.class)
public class XQueryURLRewriteTest
{
    @Test
    public void adjustPathForSourceLookup_fullXmldbUri() {
        XQueryURLRewrite rewriter = new XQueryURLRewrite();


        String basePath = "xmldb:exist:///db/adamretter.org.uk/";
        String path = "/db/adamretter.org.uk/blog/entries/some-entry.xml?edit";

        String adjustedPath = rewriter.adjustPathForSourceLookup(basePath, path);

        assertEquals(adjustedPath, "blog/entries/some-entry.xml?edit");
    }

    @Test
    public void adjustPathForSourceLookup_dbUri() {
        XQueryURLRewrite rewriter = new XQueryURLRewrite();


        String basePath = "/";
        String path = "/db/adamretter.org.uk/blog/entries/some-entry.xml?edit";

        String adjustedPath = rewriter.adjustPathForSourceLookup(basePath, path);

        assertEquals(adjustedPath, "adamretter.org.uk/blog/entries/some-entry.xml?edit");
    }

    @Test
    public void adjustPathForSourceLookup_fsUri() {
        XQueryURLRewrite rewriter = new XQueryURLRewrite();


        String basePath = "/";
        String path = "/xquery/functions.xql";

        String adjustedPath = rewriter.adjustPathForSourceLookup(basePath, path);

        assertEquals(adjustedPath, "xquery/functions.xql");
    }

    @Test
    public void requestWrapper_copiesRequestParams() {

        final Map<String, String[]> testParameterMap = new HashMap<String, String[]>();
        testParameterMap.put("paramName1", new String[] {"value1", "value1.1"});
        testParameterMap.put("paramName2", new String[] {"value2", "value2.1"});

        HttpServletRequest mockHttpServletRequest = EasyMock.createMock(HttpServletRequest.class);

        //standard request wrapper stuff
        expect(mockHttpServletRequest.getContentType()).andReturn("text/xml");
        //end standard request wrapper stuff

        expect(mockHttpServletRequest.getParameterMap()).andReturn(testParameterMap);


        replay(mockHttpServletRequest);
        RequestWrapper wrapper = new RequestWrapper(mockHttpServletRequest);
        verify(mockHttpServletRequest);

        assertEquals(testParameterMap.size(), wrapper.getParameterMap().size());

        for(String paramName : testParameterMap.keySet()) {
            assertArrayEquals(testParameterMap.get(paramName), wrapper.getParameterMap().get(paramName));
        }
    }

    @Test
    public void requestWrapper_addsParamAftercopiesRequestParams() {

        final Map<String, String[]> testParameterMap = new HashMap<String, String[]>();
        testParameterMap.put("paramName1", new String[] {"value1", "value1.1"});
        testParameterMap.put("paramName2", new String[] {"value2", "value2.1"});

        final String newRequestParamName = "newParamName";
        final String newRequestParamValue = "newParamValue";

        HttpServletRequest mockHttpServletRequest = EasyMock.createMock(HttpServletRequest.class);

        //standard request wrapper stuff
        expect(mockHttpServletRequest.getContentType()).andReturn("text/xml");
        //end standard request wrapper stuff

        expect(mockHttpServletRequest.getParameterMap()).andReturn(testParameterMap);


        replay(mockHttpServletRequest);
        RequestWrapper wrapper = new RequestWrapper(mockHttpServletRequest);
        wrapper.addParameter(newRequestParamName, newRequestParamValue);
        verify(mockHttpServletRequest);

        final Map<String, String[]> newTestParameterMap = new HashMap<String, String[]>();
        newTestParameterMap.putAll(testParameterMap);
        newTestParameterMap.put(newRequestParamName, new String[] {newRequestParamValue });

        assertEquals(newTestParameterMap.size(), wrapper.getParameterMap().size());

        for(String paramName : newTestParameterMap.keySet()) {
            assertArrayEquals(newTestParameterMap.get(paramName), wrapper.getParameterMap().get(paramName));
        }
    }
}