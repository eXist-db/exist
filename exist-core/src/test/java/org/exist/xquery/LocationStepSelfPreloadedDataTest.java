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
package org.exist.xquery;

import java.util.Optional;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A self step with preloaded data selects from its context: a preloaded node that is not in the context is
 * not selected. The step is evaluated directly, one context at a time, which is what a predicate does when
 * the path in front of it looked up more nodes than the preloaded ones.
 */
class LocationStepSelfPreloadedDataTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String DOCUMENT = "/db/self-preloaded-data.xml";
    private static final int NODES = 4;

    private static DBBroker broker;
    private static XQueryContext context;

    private NodeSet nodes;

    @BeforeAll
    static void storeFixture() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
        context = new XQueryContext(pool);
        pool.getXQueryService().execute(broker, "xmldb:store('/db', 'self-preloaded-data.xml', "
                + "<test><p>a</p><p>b</p><p>c</p><p>d</p></test>)", null);
    }

    @AfterAll
    static void removeFixture() throws EXistException, PermissionDeniedException, XPathException {
        broker.getBrokerPool().getXQueryService().execute(broker,
                "xmldb:remove('/db', 'self-preloaded-data.xml')", null);
        broker.close();
        broker = null;
        context = null;
    }

    @BeforeEach
    void selectNodes() throws EXistException, PermissionDeniedException, XPathException {
        nodes = broker.getBrokerPool().getXQueryService()
                .execute(broker, "doc('" + DOCUMENT + "')//p", null).toNodeSet();
        assertEquals(NODES, nodes.getItemCount());
    }

    @Test
    void onlyThePreloadedContextNodeIsSelected() throws XPathException, QName.IllegalQNameException {
        final LocationStep step = selfStepWithPreloaded(1);

        for (int i = 0; i < NODES; i++) {
            final Sequence result = step.eval(node(i), null);
            if (i == 1) {
                assertSelected(1, result);
            } else {
                assertEquals(0, result.getItemCount(), "context node " + i + " is not preloaded");
            }
        }
    }

    @Test
    void preloadedSetIsKeptForTheNextContextNode() throws XPathException, QName.IllegalQNameException {
        final LocationStep step = selfStepWithPreloaded(1, 2);

        assertSelected(1, step.eval(node(1), null));
        assertSelected(2, step.eval(node(2), null));
        assertEquals(0, step.eval(node(0), null).getItemCount());
    }

    @Test
    void wholeContextSelectsEveryPreloadedNode() throws XPathException, QName.IllegalQNameException {
        final LocationStep step = selfStepWithPreloaded(0, 3);

        final Sequence result = step.eval(nodes, null);

        assertEquals(2, result.getItemCount());
        assertEquals(node(0).getNodeId(), ((NodeProxy) result.itemAt(0)).getNodeId());
        assertEquals(node(3).getNodeId(), ((NodeProxy) result.itemAt(1)).getNodeId());
    }

    private LocationStep selfStepWithPreloaded(final int... indexes)
            throws XPathException, QName.IllegalQNameException {
        final NodeSet preloaded = new NewArrayNodeSet();
        for (final int index : indexes) {
            preloaded.add(node(index));
        }
        final LocationStep step = new LocationStep(context, Constants.SELF_AXIS,
                new NameTest(Type.ELEMENT, new QName("p")));
        step.setPreloadedData(preloaded.getDocumentSet(), preloaded);
        return step;
    }

    private NodeProxy node(final int index) {
        return (NodeProxy) nodes.itemAt(index);
    }

    private void assertSelected(final int index, final Sequence result) throws XPathException {
        assertEquals(1, result.getItemCount());
        assertEquals(node(index).getNodeId(), ((NodeProxy) result.itemAt(0)).getNodeId());
    }
}
