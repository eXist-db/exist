/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist-db Project
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.dom.persistent;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.io.InputStreamUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ElementValue;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.AncestorSelector;
import org.exist.xquery.ChildSelector;
import org.exist.xquery.Constants;
import org.exist.xquery.DescendantOrSelfSelector;
import org.exist.xquery.DescendantSelector;
import org.exist.xquery.NameTest;
import org.exist.xquery.NodeSelector;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Optional;

import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.exist.samples.Samples.SAMPLES;


/**
 * Test basic {@link org.exist.dom.persistent.NodeSet} operations to ensure that
 * the used algorithms are correct.
 *
 * @author <a href="mailto:adam@exist-db.org">wolf
 * @author Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class BasicNodeSetTest {

    private final static String NESTED_XML =
            "<section n='1'>" +
                    "<section n='1.1'>" +
                    "<section n='1.1.1'>" +
                    "<para n='1.1.1.1'/>" +
                    "<para n='1.1.1.2'/>" +
                    "<para n='1.1.1.3'/>" +
                    "</section>" +
                    "<section n='1.1.2'>" +
                    "<para n='1.1.2.1'/>" +
                    "</section>" +
                    "</section>" +
                    "<section n='1.2'>" +
                    "<para n='1.2.1'/>" +
                    "</section>" +
                    "</section>";

    private static Collection root = null;
    private static Sequence seqSpeech = null;
    private static DocumentSet docs = null;

    @Test
    public void childSelector() throws XPathException, EXistException {
        NodeSelector selector = new ChildSelector(seqSpeech.toNodeSet(), -1);
        NameTest test = new NameTest(Type.ELEMENT, new QName("LINE", ""));
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            NodeSet set = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(), test.getName(), selector);

            assertEquals(9492, set.getLength());
        }
    }

    @Test
    public void descendantOrSelfSelector() throws XPathException, EXistException {
        NodeSelector selector = new DescendantOrSelfSelector(seqSpeech.toNodeSet(), -1);
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEECH", ""));
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            NodeSet set = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(), test.getName(), selector);

            assertEquals(2628, set.getLength());
        }
    }

    @Test
    public void ancestorSelector() throws XPathException, EXistException {
        NodeSelector selector = new AncestorSelector(seqSpeech.toNodeSet(), -1, false, true);
        NameTest test = new NameTest(Type.ELEMENT, new QName("ACT", ""));
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            NodeSet set = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(), test.getName(), selector);

            assertEquals(15, set.getLength());
        }
    }

    @Test
    public void ancestorSelector_self() throws XPathException, EXistException {
        NodeSet ns = seqSpeech.toNodeSet();
        NodeSelector selector = new AncestorSelector(ns, -1, true, true);
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEECH", ""));
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            NodeSet set = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, seqSpeech.getDocumentSet(), test.getName(), selector);

            assertEquals(2628, set.getLength());
        }
    }

    @Test
    public void descendantSelector() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            Sequence seq = executeQuery(broker, "//SCENE", 72, null);
            NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
            NodeSelector selector = new DescendantSelector(seq.toNodeSet(), -1);
            NodeSet set = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, seq.getDocumentSet(), test.getName(), selector);

            assertEquals(2639, set.getLength());
        }
    }

    @Test
    public void selectParentChild() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            NodeSet speakers = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
            Sequence smallSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'perturbed spirit')]/ancestor::SPEECH", 1, null);

            NodeSet result = NodeSetHelper.selectParentChild(speakers, smallSet.toNodeSet(), NodeSet.DESCENDANT, -1);
            assertEquals(1, result.getLength());
            String value = serialize(broker, result.itemAt(0));
            assertEquals(value, "<SPEAKER>HAMLET</SPEAKER>");
        }
    }

    @Test
    public void selectParentChild_2() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            NodeSet speakers = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
            Sequence largeSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'love')]/ancestor::SPEECH", 187, null);

            NodeSet result = NodeSetHelper.selectParentChild(speakers, largeSet.toNodeSet(), NodeSet.DESCENDANT, -1);
            assertEquals(187, result.getLength());
        }
    }

    @Test
    public void selectAncestorDescendant() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            NodeSet speakers = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
            Sequence outerSet = executeQuery(broker, "//SCENE/TITLE[fn:contains(., 'closet')]/ancestor::SCENE", 1, null);

            NodeSet result = speakers.selectAncestorDescendant(outerSet.toNodeSet(), NodeSet.DESCENDANT, false, -1, true);
            assertEquals(56, result.getLength());
        }
    }

    @Test
    public void selectAncestorDescendant_2() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            Sequence outerSet = executeQuery(broker, "//SCENE/TITLE[fn:contains(., 'closet')]/ancestor::SCENE", 1, null);

            NodeSet result = ((AbstractNodeSet) outerSet).selectAncestorDescendant(outerSet.toNodeSet(), NodeSet.DESCENDANT, true, -1, true);
            assertEquals(1, result.getLength());
        }
    }


    @Test
    public void getParents() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            Sequence largeSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'love')]/ancestor::SPEECH", 187, null);

            NodeSet result = ((AbstractNodeSet) largeSet).getParents(-1);
            assertEquals(51, result.getLength());
        }
    }

    @Test
    public void selectAncestors() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        NameTest test = new NameTest(Type.ELEMENT, new QName("SCENE", ""));
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            NodeSet scenes = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
            Sequence largeSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'love')]/ancestor::SPEECH", 187, null);

            NodeSet result = ((AbstractNodeSet) scenes).selectAncestors(largeSet.toNodeSet(), false, -1);
            assertEquals(49, result.getLength());
        }
    }

    @Test
    public void getElementsByTagNameWildcard() throws LockException, PermissionDeniedException, EXistException {
        DocumentImpl doc = null;
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = root.getDocumentWithLock(broker, XmldbURI.create("hamlet.xml"), Lock.LockMode.READ_LOCK)) {
            final NodeList elements = lockedDoc.getDocument().getElementsByTagName(QName.WILDCARD);

            assertEquals(6636, elements.getLength());
        }
    }

    @Test
    public void getElementsByTagNameNSWildcard() throws LockException, PermissionDeniedException, EXistException {
        DocumentImpl doc = null;
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()));
            final LockedDocument lockedDoc = root.getDocumentWithLock(broker, XmldbURI.create("hamlet.xml"), Lock.LockMode.READ_LOCK)) {
            final NodeList elements = lockedDoc.getDocument().getElementsByTagNameNS(QName.WILDCARD, QName.WILDCARD);

            assertEquals(6636, elements.getLength());

        }
    }

    @Test
    public void getElementsByTagNameWildcardNS() throws LockException, PermissionDeniedException, EXistException {
        DocumentImpl doc = null;
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()));
            final LockedDocument lockedDoc = root.getDocumentWithLock(broker, XmldbURI.create("hamlet.xml"), Lock.LockMode.READ_LOCK)) {
            final NodeList elements = lockedDoc.getDocument().getElementsByTagNameNS(QName.WILDCARD, "SPEECH");

            assertEquals(1138, elements.getLength());
        }
    }

    @Test
    public void nodeProxy_getParents() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            Sequence smallSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'perturbed spirit')]/ancestor::SPEECH", 1, null);

            NodeProxy proxy = (NodeProxy) smallSet.itemAt(0);

            NodeSet result = proxy.getParents(-1);
            assertEquals(1, result.getLength());

            NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
            NodeSet speakers = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);

            result = speakers.selectParentChild(proxy, NodeSet.DESCENDANT, -1);
            assertEquals(1, result.getLength());
        }
    }

    @Test
    public void selectFollowingSiblings() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            Sequence largeSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'love')]/ancestor::SPEECH/SPEAKER", 187, null);
            NameTest test = new NameTest(Type.ELEMENT, new QName("LINE", ""));
            NodeSet lines = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);

            NodeSet result = ((AbstractNodeSet) lines).selectFollowingSiblings(largeSet.toNodeSet(), -1);
            assertEquals(1689, result.getLength());
        }
    }

    @Test
    public void selectPrecedingSiblings() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        NameTest test = new NameTest(Type.ELEMENT, new QName("SPEAKER", ""));
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            NodeSet speakers = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);
            Sequence largeSet = executeQuery(broker, "//SPEECH/LINE[fn:contains(., 'love')]/ancestor::SPEECH/LINE[1]", 187, null);

            NodeSet result = ((AbstractNodeSet) speakers).selectPrecedingSiblings(largeSet.toNodeSet(), -1);
            assertEquals(187, result.getLength());
        }
    }

    @Test
    public void extArrayNodeSet_selectParentChild_1() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            Sequence nestedSet = executeQuery(broker, "//section[@n = ('1.1', '1.1.1')]", 2, null);
            NameTest test = new NameTest(Type.ELEMENT, new QName("para", ""));
            NodeSet children = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);

            NodeSet result = children.selectParentChild(nestedSet.toNodeSet(), NodeSet.DESCENDANT);
            assertEquals(3, result.getLength());
        }
    }

    @Test
    public void extArrayNodeSet_selectParentChild_2() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            Sequence nestedSet = executeQuery(broker, "//section[@n = ('1.1', '1.1.2', '1.2')]", 3, null);
            NameTest test = new NameTest(Type.ELEMENT, new QName("para", ""));
            NodeSet children = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);

            NodeSet result = children.selectParentChild(nestedSet.toNodeSet(), NodeSet.DESCENDANT);
            assertEquals(2, result.getLength());
        }
    }

    @Test
    public void extArrayNodeSet_selectParentChild_3() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            Sequence nestedSet = executeQuery(broker, "//section[@n = ('1.1', '1.1.1', '1.2')]", 3, null);
            NameTest test = new NameTest(Type.ELEMENT, new QName("para", ""));
            NodeSet children = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);

            NodeSet result = children.selectParentChild(nestedSet.toNodeSet(), NodeSet.DESCENDANT);
            assertEquals(4, result.getLength());
        }
    }

    @Test
    public void extArrayNodeSet_selectParentChild_4() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            Sequence nestedSet = executeQuery(broker, "//para[@n = ('1.1.2.1')]", 1, null);
            NameTest test = new NameTest(Type.ELEMENT, new QName("section", ""));
            NodeSet sections = broker.getStructuralIndex().findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null);

            NodeSet result = ((NodeSet) nestedSet).selectParentChild(sections.toNodeSet(), NodeSet.DESCENDANT);
            assertEquals(1, result.getLength());
        }
    }

    @Test
    public void testOptimizations() throws XPathException, SAXException, PermissionDeniedException, EXistException, LockException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            DocumentSet docs = root.allDocs(broker, new DefaultDocumentSet(), true);

            //Testing NativeElementIndex.findChildNodesByTagName
            // parent set: 1.1.1; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
            ExtNodeSet nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1.1']", 1, null);
            NodeSet children =
                    broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT,
                            new QName("para", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
            assertEquals(3, children.getLength());

            // parent set: 1.1; child set: 1.1.1, 1.1.2
            nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
            children =
                    broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT,
                            new QName("section", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
            assertEquals(2, children.getLength());

            // parent set: 1, 1.1, 1.1.1, 1.1.2 ; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
            // problem: ancestor set contains nested nodes
            nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = ('1.1', '1.1.1', '1.1.2')]", 3, null);
            children =
                    broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT,
                            new QName("para", ""), Constants.CHILD_AXIS, docs, nestedSet, -1);
            assertEquals(4, children.getLength());

            // parent set: 1.1, 1.1.2, 1.2 ; child set: 1.1.1.1, 1.1.1.2, 1.1.1.3, 1.1.2.1, 1.2.1
            // problem: ancestor set contains nested nodes
            nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = ('1.1', '1.1.2', '1.2')]", 3, null);
            children =
                    broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""),
                            Constants.CHILD_AXIS, docs, nestedSet, -1);
            assertEquals(2, children.getLength());

            nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
            children =
                    broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""),
                            Constants.DESCENDANT_AXIS, docs, nestedSet, -1);
            assertEquals(4, children.getLength());

            nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1']", 1, null);
            children =
                    broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("para", ""),
                            Constants.DESCENDANT_AXIS, docs, nestedSet, -1);
            assertEquals(5, children.getLength());

            nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1.2']", 1, null);
            children =
                    broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ELEMENT, new QName("section", ""),
                            Constants.DESCENDANT_SELF_AXIS, docs, nestedSet, -1);
            assertEquals(1, children.getLength());

            nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1.2']", 1, null);
            children =
                    broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ATTRIBUTE, new QName("n", ""),
                            Constants.ATTRIBUTE_AXIS, docs, nestedSet, -1);
            assertEquals(1, children.getLength());

            nestedSet = (ExtNodeSet) executeQuery(broker, "//section[@n = '1.1']", 1, null);
            children =
                    broker.getStructuralIndex().findDescendantsByTagName(ElementValue.ATTRIBUTE, new QName("n", ""),
                            Constants.DESCENDANT_ATTRIBUTE_AXIS, docs, nestedSet, -1);
            assertEquals(7, children.getLength());
        }
    }

    @Test
    public void virtualNodeSet_1() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "//*/LINE", 9492, null);
        }
    }

    @Test
    public void virtualNodeSet_2() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "//*/LINE/*", 61, null);
        }
    }

    @Test
    public void virtualNodeSet_3() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "//*/LINE/text()", 9485, null);
        }
    }

    @Test
    public void virtualNodeSet_4() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "//SCENE/*/LINE", 9464, null);
        }
    }

    @Test
    public void virtualNodeSet_5() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "//SCENE/*[fn:contains(LINE, 'spirit')]", 30, null);
        }
    }

    @Test
    public void virtualNodeSet_6() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "//SCENE/*[fn:contains(LINE, 'the')]", 1313, null);
        }
    }

    @Test
    public void virtualNodeSet_7() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "//SCENE/*/LINE[fn:contains(., 'the')]", 3198, null);
        }
    }

    @Test
    public void virtualNodeSet_8() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "//SCENE[fn:contains(., 'spirit')]/ancestor::*", 16, null);
        }
    }

    @Test
    public void virtualNodeSet_9() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "for $s in //SCENE/*[fn:contains(LINE, 'the')] return fn:node-name($s)", 1313, null);
        }
    }

    @Test
    public void virtualNodeSet_10() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "//SPEECH[fn:contains(LINE, 'perturbed spirit')]/preceding-sibling::*", 65, null);
        }
    }

    @Test
    public void virtualNodeSet_11() throws XPathException, SAXException, PermissionDeniedException, EXistException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()))) {
            executeQuery(broker, "//SPEECH[fn:contains(LINE, 'perturbed spirit')]/following-sibling::*", 1, null);
        }
    }

    private static Sequence executeQuery(final DBBroker broker, final String query, final int expected, final String expectedResult) throws XPathException, SAXException, PermissionDeniedException {
        final XQuery xquery = broker.getBrokerPool().getXQueryService();
        final Sequence seq = xquery.execute(broker, query, null);
        assertEquals(expected, seq.getItemCount());

        if (expectedResult != null) {
            final Item item = seq.itemAt(0);
            final String value = serialize(broker, item);
            assertEquals(expectedResult, value);
        }
        return seq;
    }

    private static String serialize(final DBBroker broker, final Item item) throws SAXException, XPathException {
        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        final String value;
        if(Type.subTypeOf(item.getType(), Type.NODE)) {
            value = serializer.serialize((NodeValue) item);
        } else {
            value = item.getStringValue();
        }
        return value;
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeClass
    public static void setUp() throws EXistException, PermissionDeniedException, IOException, SAXException, URISyntaxException, LockException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            broker.saveCollection(transaction, root);

            // store some documents.
            for(final String sampleName : SAMPLES.getShakespeareXmlSampleNames()) {
                final String sample;
                try (final InputStream is = SAMPLES.getShakespeareSample(sampleName)) {
                    sample = InputStreamUtil.readString(is, UTF_8);
                }
                final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create(sampleName), sample);
                root.store(transaction, broker, info, sample);
            }

            final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("nested.xml"), NESTED_XML);
            root.store(transaction, broker, info, NESTED_XML);
            transact.commit(transaction);

            //for the tests
            docs = root.allDocs(broker, new DefaultDocumentSet(), true);
            seqSpeech = executeQuery(broker, "//SPEECH", 2628, null);
        }
    }

    @AfterClass
    public static void tearDown() throws PermissionDeniedException, IOException, TriggerException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {
            root = broker.getOrCreateCollection(transaction, XmldbURI.create(XmldbURI.ROOT_COLLECTION + "/test"));
            broker.removeCollection(transaction, root);

            transact.commit(transaction);
        }
    }
}
