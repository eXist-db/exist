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
package org.exist.xquery.functions.fn.transform;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.AnyURIValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

public class FunTransformTest {

    @Test
    void versionNumbers() throws Transform.PendingException {

        Options.XSLTVersion version1 = new Options.XSLTVersion(1, 0);
        Options.XSLTVersion version2 = new Options.XSLTVersion(2, 0);
        Options.XSLTVersion version3 = new Options.XSLTVersion(3, 0);
        Options.XSLTVersion version31 = new Options.XSLTVersion(3, 1);
        assertNotEquals(version1, version2);
        assertNotEquals(version1, version3);
        assertNotEquals(version2, version3);
        assertNotEquals(version3, version31);
        assertEquals(version3, Options.XSLTVersion.fromDecimal(new BigDecimal("3.0")));
        assertNotEquals(version3, Options.XSLTVersion.fromDecimal(new BigDecimal("3.1")));
        assertEquals(version31, Options.XSLTVersion.fromDecimal(new BigDecimal("3.1")));
        assertEquals(Options.XSLTVersion.fromDecimal(new BigDecimal("3.1")), Options.XSLTVersion.fromDecimal(new BigDecimal("3.1")));
    }

    @Test void badVersionNumber() throws Transform.PendingException {

        assertThrows(Transform.PendingException.class, () -> {
            Options.XSLTVersion version311 = Options.XSLTVersion.fromDecimal(new BigDecimal("3.11"));
        });
    }

    @Test public void resolution() throws XPathException, URISyntaxException {
        var base = new AnyURIValue("xmldb:exist:///db/apps/fn_transform/tei-toc2.xsl");
        var relative = new AnyURIValue("functions1.xsl");
        assertEquals(new AnyURIValue("xmldb:exist:/db/apps/fn_transform/functions1.xsl"),
            URIResolution.resolveURI(relative, base));

        var base1_5 = new AnyURIValue("xmldb:exist:///db/apps/fn_transform/tei-toc2.xsl");
        var relative1_5 = new AnyURIValue("/functions1.xsl");
        assertEquals(new AnyURIValue("xmldb:exist:/functions1.xsl"),
            URIResolution.resolveURI(relative1_5, base1_5));

        var base1_10 = new AnyURIValue("xmldb:exist:///db/apps/fn_transform/tei-toc2.xsl");
        var relative1_10 = new AnyURIValue("/fn_transform/functions1.xsl");
        assertEquals(new AnyURIValue("xmldb:exist:/fn_transform/functions1.xsl"),
            URIResolution.resolveURI(relative1_10, base1_10));

        var base2 = new AnyURIValue("xmldb:exist:/db/apps/fn_transform/tei-toc2.xsl");
        assertEquals(new AnyURIValue("xmldb:exist:/db/apps/fn_transform/functions1.xsl"),
            URIResolution.resolveURI(relative, base2));

        var base3 = new AnyURIValue("https://127.0.0.1:8088/db/apps/fn_transform/tei-toc2.xsl");
        var relative3 = new AnyURIValue("functions1.xsl");
        assertEquals(new AnyURIValue("https://127.0.0.1:8088/db/apps/fn_transform/functions1.xsl"),
            URIResolution.resolveURI(relative3, base3));

        var base3_5 = new AnyURIValue("https://127.0.0.1:8088/db/apps/fn_transform/");
        var relative3_5 = new AnyURIValue("functions1.xsl");
        assertEquals(new AnyURIValue("https://127.0.0.1:8088/db/apps/fn_transform/functions1.xsl"),
            URIResolution.resolveURI(relative3_5, base3_5));

        var base3_10 = new AnyURIValue("https://127.0.0.1:8088/db/apps/fn_transform/");
        var relative3_10 = new AnyURIValue("/functions1.xsl");
        assertEquals(new AnyURIValue("https://127.0.0.1:8088/functions1.xsl"),
            URIResolution.resolveURI(relative3_10, base3_10));

        var base4 = new AnyURIValue("xmldb:exist:///db/apps/fn_transform/tei-toc2.xsl");
        var relative4 = new AnyURIValue("xmldb:exist:///a/b/c/functions1.xsl");
        assertEquals(new AnyURIValue("xmldb:exist:///a/b/c/functions1.xsl"),
            URIResolution.resolveURI(relative4, base4));

        var base5 = new AnyURIValue("xmldb:exist:///db/apps/fn_transform/tei-toc2.xsl");
        var relative5 = new AnyURIValue("https://127.0.0.1:8088/a/b/c/functions1.xsl");
        assertEquals(new AnyURIValue("https://127.0.0.1:8088/a/b/c/functions1.xsl"),
            URIResolution.resolveURI(relative5, base5));
    }
}
