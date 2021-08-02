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

package org.exist.test.runner;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;

import static org.exist.test.runner.AbstractTestRunner.checkDescription;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the global methods of the {@link AbstractTestRunner}.
 */
class AbstractTestRunnerTest {
    @Test
     void testCheckDescriptionNull() {
        final IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () ->  checkDescription(this, null));
        assertEquals(this + " description is null", iae.getMessage());
    }

    @Test
     void testCheckDescriptionEmpty() {
        final IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () ->  checkDescription(this, ""));
        assertEquals(this + " description is empty", iae.getMessage());
    }

    @Test
     void testCheckDescriptionStartsWithBrace() {
        final IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () ->  checkDescription(this, "(bla)"));
        assertEquals(this + " description '(bla)' starts with '('", iae.getMessage());
    }

    @Test
     void testCheckDescription() {
        assertEquals("testMethod", checkDescription(this, "testMethod"));
    }

    @Test
    void testRun() {
        final AbstractTestRunner runner = new AbstractTestRunner(null, false) {
            @Override
            public void run(final RunNotifier notifier) {
                // no action
            }

            @Override
            public Description getDescription() {
                return Description.createTestDescription(getClass(), "testRun");
            }
        };

        final RunNotifier notifier = new RunNotifier();
        assertDoesNotThrow(() -> runner.run(notifier));
    }
}
