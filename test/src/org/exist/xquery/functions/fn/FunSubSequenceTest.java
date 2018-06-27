/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.functions.fn;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

@RunWith(ParallelRunner.class)
public class FunSubSequenceTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true);

    @Test
    public void all_arity2() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 1)");
        assertEquals("(1,2,3,4,5)", asSequenceStr(result));
    }

    @Test
    public void all_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 1, 5)");
        assertEquals("(1,2,3,4,5)", asSequenceStr(result));
    }

    @Test
    public void firstItem_arity2() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1), 1)");
        assertEquals("(1)", asSequenceStr(result));
    }

    @Test
    public void firstItem_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 1, 1)");
        assertEquals("(1)", asSequenceStr(result));
    }

    @Test
    public void midItem_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 2, 1)");
        assertEquals("(2)", asSequenceStr(result));
    }

    @Test
    public void midItems_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 3, 2)");
        assertEquals("(3,4)", asSequenceStr(result));
    }

    @Test
    public void lastItem_arity2() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 5)");
        assertEquals("(5)", asSequenceStr(result));
    }

    @Test
    public void lastItem_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 5, 1)");
        assertEquals("(5)", asSequenceStr(result));
    }

    @Test
    public void allButFirst_arity2() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 2)");
        assertEquals("(2,3,4,5)", asSequenceStr(result));
    }

    @Test
    public void allButFirst_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 2, 4)");
        assertEquals("(2,3,4,5)", asSequenceStr(result));
    }

    @Test
    public void allButLast_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 1, 4)");
        assertEquals("(1,2,3,4)", asSequenceStr(result));
    }

    @Test
    public void outOfRange_arity2() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 7)");
        assertEquals("()", asSequenceStr(result));
    }

    @Test
    public void outOfRange_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 7, 4)");
        assertEquals("()", asSequenceStr(result));
    }

    @Test
    public void zeroStartingLoc_arity2() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 0)");
        assertEquals("(1,2,3,4,5)", asSequenceStr(result));
    }

    @Test
    public void zeroStartingLocToMid_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 0, 3)");
        assertEquals("(1,2)", asSequenceStr(result));
    }

    @Test
    public void zeroStartingLocToEnd_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), 0, 5)");
        assertEquals("(1,2,3,4)", asSequenceStr(result));
    }

    @Test
    public void negativeStartingLoc_arity2() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), -2)");
        assertEquals("(1,2,3,4,5)", asSequenceStr(result));
    }

    @Test
    public void negativeStartingLoc_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 5), -2, 5)");
        assertEquals("(1,2)", asSequenceStr(result));
    }

    @Test
    public void smallPartOfLargeRange_arity2() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 3000000000), 2999999995)");
        assertEquals("(2999999995,2999999996,2999999997,2999999998,2999999999,3000000000)", asSequenceStr(result));
    }

    @Test
    public void smallPartOfLargeRange_arity3() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery("fn:subsequence((1 to 3000000000), 2999999995, 5)");
        assertEquals("(2999999995,2999999996,2999999997,2999999998,2999999999)", asSequenceStr(result));
    }

    private static String asSequenceStr(final ResourceSet result) throws XMLDBException {
        final StringBuilder builder = new StringBuilder();
        builder.append('(');
        for (int i = 0; i < result.getSize(); i++) {
            builder.append(result.getResource(i).getContent().toString());
            if (i + 1 < result.getSize()) {
                builder.append(',');
            }
        }
        builder.append(')');
        return builder.toString();
    }
}
