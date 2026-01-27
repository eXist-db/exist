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
package org.exist.xquery.functions.securitymanager;

import com.googlecode.junittoolbox.ParallelRunner;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.easymock.EasyMock;

import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.Subject;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class IdFunctionTest {

    /**
     * Test of eval method, of class IdFunction.
     * when real and effective users are different
     */
    @Test
    public void differingRealAndEffectiveUsers() throws XPathException, XpathException {
        final XQueryContext mckContext = createMockBuilder(XQueryContext.class)
                .addMockedMethod("pushDocumentContext")
                .addMockedMethod("getDocumentBuilder", new Class[0])
                .addMockedMethod("popDocumentContext")
                .addMockedMethod("getRealUser")
                .addMockedMethod("getEffectiveUser")
                .createMock();

        final Subject mckRealUser = EasyMock.createMock(Subject.class);
        final String realUsername = "real";
        mckContext.pushDocumentContext();
        expectLastCall().once();
        expect(mckContext.getDocumentBuilder()).andReturn(new MemTreeBuilder());
        mckContext.popDocumentContext();
        expectLastCall().once();
        expect(mckContext.getRealUser()).andReturn(mckRealUser);
        expect(mckRealUser.getName()).andReturn(realUsername);
        expect(mckRealUser.getGroups()).andReturn(new String[]{"realGroup1", "realGroup2"});
        expect(mckRealUser.getId()).andReturn(1);

        final Subject mckEffectiveUser = EasyMock.createMock(Subject.class);
        final String effectiveUsername = "effective";
        expect(mckContext.getEffectiveUser()).andReturn(mckEffectiveUser);
        expect(mckEffectiveUser.getId()).andReturn(2);
        expect(mckEffectiveUser.getName()).andReturn(effectiveUsername);
        expect(mckEffectiveUser.getGroups()).andReturn(new String[]{"effectiveGroup1", "effectiveGroup2"});

        replay(mckEffectiveUser, mckRealUser, mckContext);

        final IdFunction idFunctions = new IdFunction(mckContext, IdFunction.FNS_ID);
        final Sequence result = idFunctions.eval(new Sequence[]{Sequence.EMPTY_SEQUENCE}, null);

        assertEquals(1, result.getItemCount());

        final XpathEngine xpathEngine = XMLUnit.newXpathEngine();
        final Map<String, String> namespaces = new HashMap<>();
        namespaces.put("sm", "http://exist-db.org/xquery/securitymanager");
        xpathEngine.setNamespaceContext(new SimpleNamespaceContext(namespaces));

        final DocumentImpl resultDoc = (DocumentImpl)result.itemAt(0);

        final String actualRealUsername = xpathEngine.evaluate("/sm:id/sm:real/sm:username", resultDoc);
        assertEquals(realUsername, actualRealUsername);

        final String actualEffectiveUsername = xpathEngine.evaluate("/sm:id/sm:effective/sm:username", resultDoc);
        assertEquals(effectiveUsername, actualEffectiveUsername);

        verify(mckEffectiveUser, mckRealUser, mckContext);
    }

    /**
     * Test of eval method, of class IdFunction.
     * when real and effective users are the same
     */
    @Test
    public void sameRealAndEffectiveUsers() throws XPathException, XpathException {
        final XQueryContext mckContext = createMockBuilder(XQueryContext.class)
                .addMockedMethod("pushDocumentContext")
                .addMockedMethod("getDocumentBuilder", new Class[0])
                .addMockedMethod("popDocumentContext")
                .addMockedMethod("getRealUser")
                .addMockedMethod("getEffectiveUser")
                .createMock();

        final Subject mckUser = EasyMock.createMock(Subject.class);
        final String username = "user1";
        mckContext.pushDocumentContext();
        expectLastCall().once();
        expect(mckContext.getDocumentBuilder()).andReturn(new MemTreeBuilder());
        mckContext.popDocumentContext();
        expectLastCall().once();
        expect(mckContext.getRealUser()).andReturn(mckUser);
        expect(mckUser.getName()).andReturn(username);
        expect(mckUser.getGroups()).andReturn(new String[]{"group1", "group2"});
        expect(mckUser.getId()).andReturn(1);

        expect(mckContext.getEffectiveUser()).andReturn(mckUser);
        expect(mckUser.getId()).andReturn(1);

        expect(mckUser.getGroupIds()).andReturn(new int[] {101, 102}).times(2);

        replay(mckUser, mckContext);

        final IdFunction idFunctions = new IdFunction(mckContext, IdFunction.FNS_ID);
        final Sequence result = idFunctions.eval(new Sequence[]{Sequence.EMPTY_SEQUENCE}, null);

        assertEquals(1, result.getItemCount());

        final XpathEngine xpathEngine = XMLUnit.newXpathEngine();
        final Map<String, String> namespaces = new HashMap<>();
        namespaces.put("sm", "http://exist-db.org/xquery/securitymanager");
        xpathEngine.setNamespaceContext(new SimpleNamespaceContext(namespaces));

        final DocumentImpl resultDoc = (DocumentImpl)result.itemAt(0);

        final String actualRealUsername = xpathEngine.evaluate("/sm:id/sm:real/sm:username", resultDoc);
        assertEquals(username, actualRealUsername);

        final String actualEffectiveUsername = xpathEngine.evaluate("/sm:id/sm:effective/sm:username", resultDoc);
        assertEquals("", actualEffectiveUsername);

        verify(mckUser, mckContext);
    }

    /**
     * Test of eval method, of class IdFunction.
     * when real and effective users are have the same username/id
     * but have different group memberships - as can happen with setGid
     * without setUid.
     */
    @Test
    public void differingByGroupRealAndEffectiveUsers() throws XPathException, XpathException {
        final XQueryContext mckContext = createMockBuilder(XQueryContext.class)
                .addMockedMethod("pushDocumentContext")
                .addMockedMethod("getDocumentBuilder", new Class[0])
                .addMockedMethod("popDocumentContext")
                .addMockedMethod("getRealUser")
                .addMockedMethod("getEffectiveUser")
                .createMock();

        final Subject mckRealUser = EasyMock.createMock(Subject.class);
        final String realUsername = "user1";
        mckContext.pushDocumentContext();
        expectLastCall().once();
        expect(mckContext.getDocumentBuilder()).andReturn(new MemTreeBuilder());
        mckContext.popDocumentContext();
        expectLastCall().once();
        expect(mckContext.getRealUser()).andReturn(mckRealUser);
        expect(mckRealUser.getName()).andReturn(realUsername);
        expect(mckRealUser.getGroups()).andReturn(new String[]{"realGroup1"});
        expect(mckRealUser.getId()).andReturn(101);
        expect(mckRealUser.getGroupIds()).andReturn(new int[] {101});

        final Subject mckEffectiveUser = EasyMock.createMock(Subject.class);
        final String effectiveUsername = "user1";
        expect(mckContext.getEffectiveUser()).andReturn(mckEffectiveUser);
        expect(mckEffectiveUser.getId()).andReturn(101);
        expect(mckEffectiveUser.getName()).andReturn(effectiveUsername);
        expect(mckEffectiveUser.getGroups()).andReturn(new String[]{"realGroup1", "effectiveGroup1"});
        expect(mckEffectiveUser.getGroupIds()).andReturn(new int[] {101, 102});

        replay(mckEffectiveUser, mckRealUser, mckContext);

        final IdFunction idFunctions = new IdFunction(mckContext, IdFunction.FNS_ID);
        final Sequence result = idFunctions.eval(new Sequence[]{Sequence.EMPTY_SEQUENCE}, null);

        assertEquals(1, result.getItemCount());

        final XpathEngine xpathEngine = XMLUnit.newXpathEngine();
        final Map<String, String> namespaces = new HashMap<>();
        namespaces.put("sm", "http://exist-db.org/xquery/securitymanager");
        xpathEngine.setNamespaceContext(new SimpleNamespaceContext(namespaces));

        final DocumentImpl resultDoc = (DocumentImpl)result.itemAt(0);

        final String actualRealUsername = xpathEngine.evaluate("/sm:id/sm:real/sm:username", resultDoc);
        assertEquals(realUsername, actualRealUsername);

        final String actualEffectiveUsername = xpathEngine.evaluate("/sm:id/sm:effective/sm:username", resultDoc);
        assertEquals(effectiveUsername, actualEffectiveUsername);

        verify(mckEffectiveUser, mckRealUser, mckContext);
    }
}
