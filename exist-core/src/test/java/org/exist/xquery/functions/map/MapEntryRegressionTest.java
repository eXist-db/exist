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
package org.exist.xquery.functions.map;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * Regression tests for the map:entry bugs documented in
 * <a href="https://github.com/eXist-db/exist/issues/6340">issue #6340</a>:
 * NPE on missing-key lookup against a single-entry map produced by map:entry.
 *
 * <p>The NaN-key cases (XQTS map-entry-005 / map-entry-006) are also covered,
 * mirroring the XQTS expectations: per XPath F&amp;O 3.1 maps can hold NaN keys,
 * and looking up NaN finds the entry (op:same-key treats NaN as equal to NaN).</p>
 */
public class MapEntryRegressionTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server =
            new ExistXmldbEmbeddedServer(true, true, true);

    /** XQTS map-entry-003: looking up an absent key returned Java null and NPE'd in fn:empty. */
    @Test
    public void missingKeyLookupSequenceValueReturnsEmpty() throws Exception {
        assertEquals("true", run("""
                let $result := map:entry("foo", ("x", "y", "z"))
                return empty($result("bar"))
                """));
    }

    /** XQTS map-entry-004: same NPE shape, untyped-atomic key + map value. */
    @Test
    public void missingKeyLookupUntypedAtomicKeyMapValueReturnsEmpty() throws Exception {
        assertEquals("true", run("""
                let $result := map:entry(xs:untypedAtomic("foo"), map{})
                return empty($result("bar"))
                """));
    }

    /**
     * XQTS map-entry-005: NaN can be stored in a map, and looking up by NaN
     * (op:same-key treats NaN as equal to NaN) retrieves the entry.
     */
    @Test
    public void nanKeyXsDoubleStoredAndLookedUp() throws Exception {
        assertEquals("true", run("""
                let $result := map:entry(number('NaN'), 'NaN')
                return map:size($result) eq 1 and exists($result(number('NaN')))
                """));
    }

    /** XQTS map-entry-006: same as 005 but with xs:float NaN. */
    @Test
    public void nanKeyXsFloatStoredAndLookedUp() throws Exception {
        assertEquals("true", run("""
                let $result := map:entry(xs:float('NaN'), 'NaN')
                return map:size($result) eq 1 and exists($result(number('NaN')))
                """));
    }

    /** Cross-type NaN: xs:float('NaN') and xs:double('NaN') are op:same-key. */
    @Test
    public void nanKeyCrossTypeLookup() throws Exception {
        assertEquals("true", run("""
                let $result := map:entry(xs:float('NaN'), 'fNaN')
                return $result(xs:double('NaN')) eq 'fNaN'
                """));
    }

    /** map:get on a SingleKeyMapType with absent key returns (), not null. */
    @Test
    public void mapGetMissingKeySingleKeyMapReturnsEmpty() throws Exception {
        assertEquals("true", run("""
                let $result := map:entry("foo", "bar")
                return empty(map:get($result, "baz"))
                """));
    }

    private String run(final String query) throws Exception {
        final XQueryService xqs = server.getRoot().getService(XQueryService.class);
        final ResourceSet result = xqs.query("xquery version \"3.1\";\n" + query);
        return (String) result.getResource(0).getContent();
    }
}
