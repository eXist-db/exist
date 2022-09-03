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

package org.exist.util;

import com.ibm.icu.text.Collator;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.exist.util.Collations.HTML_ASCII_CASE_INSENSITIVE_COLLATION_URI;
import static org.junit.Assert.assertTrue;

public class CollationsTest {

    @Test
    public void htmlAscii_contains() throws XPathException {
        final Collator collator = Collations.getCollationFromURI(HTML_ASCII_CASE_INSENSITIVE_COLLATION_URI, (Expression)null);

        assertTrue(Collations.contains(collator, "iNPut", "pu"));
        assertTrue(Collations.contains(collator, "iNPut", "PU"));
        assertTrue(Collations.contains(collator,"h&#244;tel", "h&#244;t"));
        assertFalse(Collations.contains(collator, "h&#244;tel", "H&#212;T"));
    }
}
