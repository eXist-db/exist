/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2014 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.securitymanager;

import org.easymock.classextension.EasyMock;
import static org.easymock.classextension.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createMockBuilder;
import org.exist.security.Subject;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import static org.junit.Assert.assertEquals;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class IdFunctionTest {
    
    /**
     * Test of eval method, of class IdFunction.
     */
    @Test
    @Ignore //need to update to newer easy mock 3.2?
    public void differingRealAndEffectiveUsers() throws XPathException {
       //final XQueryContext mckContext = EasyMock.createNiceMock(XQueryContext.class);
       final XQueryContext mckContext = createMockBuilder(XQueryContext.class)
               .addMockedMethod("getRealUser")
               .addMockedMethod("getEffectiveUser")
               .createMock();
       
       final Subject mckRealUser = EasyMock.createMock(Subject.class);
       
       expect(mckContext.getRealUser()).andReturn(mckRealUser);
       expect(mckRealUser.getName()).andReturn("real");
       expect(mckRealUser.getGroups()).andReturn(new String[] { "realGroup1", "realGroup2" });
       expect(mckRealUser.getId()).andReturn(1);
       
       final Subject mckEffectiveUser = EasyMock.createMock(Subject.class);
       
       expect(mckContext.getEffectiveUser()).andReturn(mckEffectiveUser);
       expect(mckEffectiveUser.getId()).andReturn(2);
       expect(mckEffectiveUser.getName()).andReturn("effective");
       expect(mckEffectiveUser.getGroups()).andReturn(new String[] { "effectiveGroup1", "effectiveGroup2" });
       
       final IdFunction idFunctions = new IdFunction(mckContext, IdFunction.FNS_ID);
       final Sequence result = idFunctions.eval(new Sequence[] { Sequence.EMPTY_SEQUENCE }, null);
       
       assertEquals(1, result.getItemCount());
       
       //TODO further assertions
    }
}
