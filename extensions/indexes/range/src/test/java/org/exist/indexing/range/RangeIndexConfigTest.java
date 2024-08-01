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
package org.exist.indexing.range;

import nl.altindag.log.LogCaptor;
import org.apache.logging.log4j.Logger;
import org.easymock.Capture;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;

import static org.easymock.EasyMock.*;
import static org.exist.collections.CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE;
import static org.exist.indexing.lucene.LuceneIndexConfig.MATCH_ATTR;
import static org.exist.indexing.lucene.LuceneIndexConfig.QNAME_ATTR;
import static org.junit.Assert.assertTrue;

public class RangeIndexConfigTest {

    /**
     * {@see https://github.com/eXist-db/exist/issues/1339}
     */
    @Test
    public void errorsHaveSourceContext() throws NoSuchFieldException, IllegalAccessException {
        final String badCreateQName = "tei:persName "; // Note the trailing
        final String mockCollectionXConfUri = "/db/system/conf/db/mock/" + DEFAULT_COLLECTION_CONFIG_FILE;

        final NodeList mockConfigNodes = createMock(NodeList.class);
        final Element mockConfigNode = createMock(Element.class);
        final NodeList mockCreates = createMock(NodeList.class);
        final Document mockCreateDocument = createMock(Document.class);
        final Element mockCreate = createMock(Element.class);
        final NodeList mockEmptyNodeList = createMock(NodeList.class);
        final Logger mockLogger = createMock(Logger.class);

        expect(mockConfigNodes.getLength()).andReturn(1).times(2);
        expect(mockConfigNodes.item(0)).andReturn(mockConfigNode);
        expect(mockConfigNode.getNodeType()).andReturn(Node.ELEMENT_NODE);
        expect(mockConfigNode.getLocalName()).andReturn(RangeIndexConfig.CONFIG_ROOT);
        expect(mockConfigNode.getChildNodes()).andReturn(mockCreates);

        expect(mockCreates.getLength()).andReturn(1).times(2);
        expect(mockCreates.item(0)).andReturn(mockCreate);
        expect(mockCreate.getNodeType()).andReturn(Node.ELEMENT_NODE);
        expect(mockCreate.getLocalName()).andReturn(RangeIndexConfig.CREATE_ELEM);

        // skip getFieldsAndConditions
        expect(mockCreate.getChildNodes()).andReturn(mockEmptyNodeList);
        expect(mockEmptyNodeList.getLength()).andReturn(0);

        expect(mockCreate.getAttribute(MATCH_ATTR)).andReturn(null);
        expect(mockCreate.hasAttribute(QNAME_ATTR)).andReturn(true);
        expect(mockCreate.getAttribute(QNAME_ATTR)).andReturn(badCreateQName);

        expect(mockCreate.getOwnerDocument()).andReturn(mockCreateDocument);
        expect(mockCreateDocument.getDocumentURI()).andReturn(mockCollectionXConfUri);

        final Capture<String> errorMsgCapture = newCapture();

        mockLogger.error(capture(errorMsgCapture));

        replay(mockConfigNodes, mockConfigNode, mockCreates, mockCreateDocument, mockCreate, mockEmptyNodeList, mockLogger);


        final Map<String, String> namespaces = new HashMap<>();
        namespaces.put("tei", "http://www.tei-c.org/ns/1.0");

        LogCaptor logCaptor = LogCaptor.forClass(RangeIndexConfig.class);

        final RangeIndexConfig config = new RangeIndexConfig(mockConfigNodes, namespaces);

        assertTrue(logCaptor.getLogs().get(0)
                .contains("Illegal QName: '" + badCreateQName + "'.. QName is invalid: INVALID_LOCAL_PART"));

        assertTrue(logCaptor.getLogs().get(0)
                .contains("(" + mockCollectionXConfUri + ")"));

        verify(mockConfigNodes, mockConfigNode, mockCreates, mockCreateDocument, mockCreate, mockEmptyNodeList);
    }

}
