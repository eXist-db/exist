/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.http.urlrewrite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.easymock.EasyMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.replay;
import org.exist.Namespaces;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * @author aretter
 */
public class URLRewriteTest {

    @Test
    public void constructorAddsMultipleParameterValuesForSameParameterName() {

        final String ELEMENT_ADD_PARAMETER = "add-parameter";
        final String PARAM_NAME = "param1";
        final String PARAM_VALUE_1 = "value1.1";
        final String PARAM_VALUE_2 = "value1.2";

        Element mockConfig = EasyMock.createMock(Element.class);
        Element mockParameter1 = EasyMock.createMock(Element.class);
        Element mockParameter2 = EasyMock.createMock(Element.class);

        expect(mockConfig.hasAttribute("absolute")).andReturn(false);
        expect(mockConfig.hasAttribute("method")).andReturn(false);

        expect(mockConfig.hasChildNodes()).andReturn(true);
        expect(mockConfig.getFirstChild()).andReturn(mockParameter1);

        expect(mockParameter1.getNodeType()).andReturn(Node.ELEMENT_NODE);
        expect(mockParameter1.getNamespaceURI()).andReturn(Namespaces.EXIST_NS);
        expect(mockParameter1.getLocalName()).andReturn(ELEMENT_ADD_PARAMETER);
        expect(mockParameter1.getAttribute("name")).andReturn(PARAM_NAME);
        expect(mockParameter1.getAttribute("value")).andReturn(PARAM_VALUE_1);

        expect(mockParameter1.getNextSibling()).andReturn(mockParameter2);

        expect(mockParameter2.getNodeType()).andReturn(Node.ELEMENT_NODE);
        expect(mockParameter2.getNamespaceURI()).andReturn(Namespaces.EXIST_NS);
        expect(mockParameter2.getLocalName()).andReturn(ELEMENT_ADD_PARAMETER);
        expect(mockParameter2.getAttribute("name")).andReturn(PARAM_NAME);
        expect(mockParameter2.getAttribute("value")).andReturn(PARAM_VALUE_2);

        expect(mockParameter2.getNextSibling()).andReturn(null);


        replay(mockConfig, mockParameter1, mockParameter2);

        TestableURLRewrite urlRewrite = new TestableURLRewrite(mockConfig, null);

        verify(mockConfig, mockParameter1, mockParameter2);

        final Map<String, List<String>> testParameters = new HashMap<String, List<String>>();
        List<String> values = new ArrayList<String>();
        values.add(PARAM_VALUE_1);
        values.add(PARAM_VALUE_2);
        testParameters.put(PARAM_NAME, values);

        assertEquals(testParameters.size(), urlRewrite.getParameters().size());

        for(String paramName : testParameters.keySet()) {
            assertEquals(testParameters.get(paramName), urlRewrite.getParameters().get(paramName));
        }

    }


    private class TestableURLRewrite extends URLRewrite {

        public TestableURLRewrite(Element config, String uri) {
            super(config, uri);
        }

        public TestableURLRewrite(TestableURLRewrite other) {
            super(other);
        }

        public Map<String, List<String>> getParameters() {
            return parameters;
        }

        @Override
        public void doRewrite(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        protected URLRewrite copy() {
            return new TestableURLRewrite(this);
        }
    }
}
