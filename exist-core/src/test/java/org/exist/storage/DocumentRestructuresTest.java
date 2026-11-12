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
package org.exist.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentRestructuresTest {

    @Test
    void aDocumentIsChangedOnlyByARestructuringAfterTheStamp() {
        final DocumentRestructures restructures = new DocumentRestructures();
        final long before = restructures.current();
        assertFalse(restructures.changedSince(7, before), "nothing was restructured yet");

        restructures.restructured(7);
        assertTrue(restructures.changedSince(7, before), "restructured after the stamp");
        assertFalse(restructures.changedSince(8, before), "another document is not affected");
        assertFalse(restructures.changedSince(7, restructures.current()), "restructured before a later stamp");

        final long between = restructures.current();
        restructures.restructured(8);
        assertTrue(restructures.changedSince(8, between));
        assertFalse(restructures.changedSince(7, between), "the first restructuring of 7 is older than the stamp");
    }

    @Test
    void aForgottenDocumentIsNotChanged() {
        final DocumentRestructures restructures = new DocumentRestructures();
        final long before = restructures.current();
        restructures.restructured(7);
        restructures.forget(7);
        assertFalse(restructures.changedSince(7, before), "a document whose id was given back has no history");
    }
}
