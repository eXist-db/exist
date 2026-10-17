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

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.easymock.EasyMock;

import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.Subject;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.Test;

import static org.easymock.EasyMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.xmlunit.matchers.EvaluateXPathMatcher.hasXPath;


import java.util.Map;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
@Execution(ExecutionMode.CONCURRENT)
class IdFunctionTest {

    private static final Map<String, String> NAMESPACES = Map.of("sm", "http://exist-db.org/xquery/securitymanager");

    /**
     * Test of eval method, of class IdFunction.
     * when real and effective users are different
     */
    @Test
    void differingRealAndEffectiveUsers() throws XPathException {
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

        final DocumentImpl resultDoc = (DocumentImpl)result.itemAt(0);

        assertThat(resultDoc, hasXPath("/sm:id/sm:real/sm:username", equalTo(realUsername)).withNamespaceContext(NAMESPACES));

        assertThat(resultDoc, hasXPath("/sm:id/sm:effective/sm:username", equalTo(effectiveUsername)).withNamespaceContext(NAMESPACES));

        verify(mckEffectiveUser, mckRealUser, mckContext);
    }

    /**
     * Test of eval method, of class IdFunction.
     * when real and effective users are the same
     */
    @Test
    void sameRealAndEffectiveUsers() throws XPathException {
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

        final DocumentImpl resultDoc = (DocumentImpl)result.itemAt(0);

        assertThat(resultDoc, hasXPath("/sm:id/sm:real/sm:username", equalTo(username)).withNamespaceContext(NAMESPACES));

        assertThat(resultDoc, hasXPath("/sm:id/sm:effective/sm:username", equalTo("")).withNamespaceContext(NAMESPACES));

        verify(mckUser, mckContext);
    }

    /**
     * Test of eval method, of class IdFunction.
     * when real and effective users are have the same username/id
     * but have different group memberships - as can happen with setGid
     * without setUid.
     */
    @Test
    void differingByGroupRealAndEffectiveUsers() throws XPathException {
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

        final DocumentImpl resultDoc = (DocumentImpl)result.itemAt(0);

        assertThat(resultDoc, hasXPath("/sm:id/sm:real/sm:username", equalTo(realUsername)).withNamespaceContext(NAMESPACES));

        assertThat(resultDoc, hasXPath("/sm:id/sm:effective/sm:username", equalTo(effectiveUsername)).withNamespaceContext(NAMESPACES));

        verify(mckEffectiveUser, mckRealUser, mckContext);
    }
}
