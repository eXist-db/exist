/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2016 The eXist Project
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
 */
package org.exist.xquery;

import org.exist.storage.DBBroker;
import org.exist.security.Subject;
import org.exist.xquery.value.BinaryValue;
import org.junit.Test;
import org.easymock.EasyMock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Deque;
import java.util.List;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class XQueryContextTest {

    @Test
    public void prepareForExecution_setsUserFromSession() {

        //partial mock context
        XQueryContext context = EasyMock.createMockBuilder(XQueryContext.class)
                .withConstructor()
                .addMockedMethod("getUserFromHttpSession")
                .addMockedMethod("getBroker")
                .createMock();

        DBBroker mockBroker = createMock(DBBroker.class);

        Subject mockSubject = createMock(Subject.class);

        //expectations
        expect(context.getUserFromHttpSession()).andReturn(mockSubject);
        expect(context.getBroker()).andReturn(mockBroker).times(2);
        mockBroker.pushSubject(mockSubject);

        //test
        replay(context);

        context.prepareForExecution();

        verify(context);
    }

    /**
     * Test to ensure that BinaryValueInstances are
     * correctly cleaned up by the XQueryContext
     * between reuse of the context
     */
    @Test
    public void cleanUp_BinaryValueInstances() throws NoSuchFieldException, IllegalAccessException, IOException {
        final XQueryContext context = new XQueryContext();
        final XQueryWatchDog mockWatchdog = createMock(XQueryWatchDog.class);
        context.setWatchDog(mockWatchdog);

        final BinaryValue mockBin1 = createMock(BinaryValue.class);
        final BinaryValue mockBin2 = createMock(BinaryValue.class);
        final BinaryValue mockBin3 = createMock(BinaryValue.class);
        final BinaryValue mockBin4 = createMock(BinaryValue.class);
        final BinaryValue mockBin5 = createMock(BinaryValue.class);
        final BinaryValue mockBin6 = createMock(BinaryValue.class);
        final BinaryValue mockBin7 = createMock(BinaryValue.class);

        // expectations on our mocks
        mockBin1.close();
        expectLastCall().times(1);
        mockBin2.close();
        expectLastCall().times(1);
        mockBin3.close();
        expectLastCall().times(1);
        mockBin4.close();
        expectLastCall().times(1);
        mockBin5.close();
        expectLastCall().times(1);
        mockBin6.close();
        expectLastCall().times(1);
        mockBin7.close();
        expectLastCall().times(1);
        mockWatchdog.reset();
        expectLastCall().times(3);

        // prepare our mocks for our test
        replay(mockBin1, mockBin2, mockBin3, mockBin4, mockBin5, mockBin6, mockBin7, mockWatchdog);


        /* round 1 */

        // use some binary streams
        context.registerBinaryValueInstance(mockBin1);
        context.registerBinaryValueInstance(mockBin2);
        context.registerBinaryValueInstance(mockBin3);
        assertEquals(3, countBinaryValueInstances(context));
        assertEquals(1, countCleanupTasks(context));

        // cleanup those streams
        context.runCleanupTasks();
        assertEquals(0, countBinaryValueInstances(context));

        //reset the context (for reuse(), just as XQueryPool#returnCompiledXQuery(org.exist.source.Source, CompiledXQuery) would do)
        context.reset();
        assertEquals(0, countCleanupTasks(context));


        /* round 2, let's reuse the context... */

        // use some more binary streams
        context.registerBinaryValueInstance(mockBin4);
        context.registerBinaryValueInstance(mockBin5);
        assertEquals(2, countBinaryValueInstances(context));
        assertEquals(1, countCleanupTasks(context));

        // cleanup those streams
        context.runCleanupTasks();
        assertEquals(0, countBinaryValueInstances(context));

        //reset the context (for reuse(), just as XQueryPool#returnCompiledXQuery(org.exist.source.Source, CompiledXQuery) would do)
        context.reset();
        assertEquals(0, countCleanupTasks(context));


        /* round 3, let's reuse the context a second time... */

        // again, use some more binary streams
        context.registerBinaryValueInstance(mockBin6);
        context.registerBinaryValueInstance(mockBin7);
        assertEquals(2, countBinaryValueInstances(context));
        assertEquals(1, countCleanupTasks(context));

        // cleanup those streams
        context.runCleanupTasks();
        assertEquals(0, countBinaryValueInstances(context));

        //reset the context (for reuse(), just as XQueryPool#returnCompiledXQuery(org.exist.source.Source, CompiledXQuery) would do)
        context.reset();
        assertEquals(0, countCleanupTasks(context));


        // verify the expectations of our mocks
        verify(mockBin1, mockBin2, mockBin3, mockBin4, mockBin5, mockBin6, mockBin7, mockWatchdog);
    }

    private int countBinaryValueInstances(final XQueryContext context) throws NoSuchFieldException, IllegalAccessException {
        final Field fldBinaryValueInstances = context.getClass().getDeclaredField("binaryValueInstances");
        fldBinaryValueInstances.setAccessible(true);
        final Deque<BinaryValue> binaryValueInstances = (Deque<BinaryValue>)fldBinaryValueInstances.get(context);
        return binaryValueInstances.size();
    }

    private int countCleanupTasks(final XQueryContext context) throws NoSuchFieldException, IllegalAccessException {
        final Field fldCleanupTasks = context.getClass().getDeclaredField("cleanupTasks");
        fldCleanupTasks.setAccessible(true);
        final List<XQueryContext.CleanupTask> cleanupTasks = (List<XQueryContext.CleanupTask>)fldCleanupTasks.get(context);
        return cleanupTasks.size();
    }
}