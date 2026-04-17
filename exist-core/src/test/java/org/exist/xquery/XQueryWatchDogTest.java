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

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.easymock.EasyMock;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link XQueryWatchDog}.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/2529">#2529</a>
 */
public class XQueryWatchDogTest {

    private XQueryWatchDog createWatchDog() {
        final XQueryContext context = EasyMock.createMockBuilder(XQueryContext.class)
                .withConstructor()
                .createMock();
        return new XQueryWatchDog(context);
    }

    private long getTimeout(final XQueryWatchDog watchDog) throws Exception {
        final Field f = XQueryWatchDog.class.getDeclaredField("timeout");
        f.setAccessible(true);
        return (long) f.get(watchDog);
    }

    private Option timeoutOption(final String value) throws XPathException {
        return new Option(new QName("timeout", Namespaces.EXIST_NS, "exist"), value);
    }

    @Test
    public void setTimeoutFromOptionNegativeOneDisablesTimeout() throws Exception {
        final XQueryWatchDog watchDog = createWatchDog();
        watchDog.setTimeoutFromOption(timeoutOption("-1"));
        assertEquals(Long.MAX_VALUE, getTimeout(watchDog));
    }

    @Test
    public void setTimeoutFromOptionZeroDisablesTimeout() throws Exception {
        final XQueryWatchDog watchDog = createWatchDog();
        watchDog.setTimeoutFromOption(timeoutOption("0"));
        assertEquals(Long.MAX_VALUE, getTimeout(watchDog));
    }

    @Test
    public void setTimeoutFromOptionPositiveValueSetsTimeout() throws Exception {
        final XQueryWatchDog watchDog = createWatchDog();
        watchDog.setTimeoutFromOption(timeoutOption("30000"));
        assertEquals(30000L, getTimeout(watchDog));
    }

    @Test
    public void setTimeoutFromOptionNegativeValueDisablesTimeout() throws Exception {
        final XQueryWatchDog watchDog = createWatchDog();
        watchDog.setTimeoutFromOption(timeoutOption("-500"));
        assertEquals(Long.MAX_VALUE, getTimeout(watchDog));
    }

    @Test(expected = XPathException.class)
    public void setTimeoutFromOptionNonNumericThrowsException() throws Exception {
        final XQueryWatchDog watchDog = createWatchDog();
        watchDog.setTimeoutFromOption(timeoutOption("abc"));
    }

    @Test(expected = XPathException.class)
    public void setTimeoutFromOptionMultipleValuesThrowsException() throws Exception {
        final XQueryWatchDog watchDog = createWatchDog();
        watchDog.setTimeoutFromOption(timeoutOption("100 200"));
    }

    @Test
    public void proceedDoesNotThrowWhenTimeoutDisabledViaOption() throws Exception {
        final XQueryWatchDog watchDog = createWatchDog();
        watchDog.setTimeoutFromOption(timeoutOption("-1"));
        // Should not throw TerminatedException even though startTime is in the past
        watchDog.proceed(null);
    }
}
