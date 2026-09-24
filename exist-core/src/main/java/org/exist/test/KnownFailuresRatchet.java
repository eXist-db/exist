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
package org.exist.test;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The ratchet the conformance tests enforce over a list of known failures: what fails must be
 * exactly what is listed. A failure that is not listed is a regression. A listed entry that no
 * longer fails must be taken off the list, so that it becomes regression-guarded.
 */
public final class KnownFailuresRatchet {

    private KnownFailuresRatchet() {
    }

    /**
     * Compares the failures observed with the known ones.
     *
     * @param failing the entries that failed
     * @param known the entries listed as known failures
     * @param regressedHeading how the message introduces regressions, after their count,
     *     e.g. {@code "name(s) regressed:"}
     * @param unit what one entry is, for the message, e.g. {@code "name(s)"}
     * @param knownListName the name of the list of known failures, for the message
     * @param describe renders a set of entries for the message
     * @return the failure message, or empty if the failures are exactly the known ones
     */
    public static Optional<String> check(final Set<String> failing, final Set<String> known,
            final String regressedHeading, final String unit, final String knownListName,
            final Function<Set<String>, String> describe) {
        final Set<String> regressions = new LinkedHashSet<>(failing);
        regressions.removeAll(known);
        final Set<String> nowFixed = new LinkedHashSet<>(known);
        nowFixed.removeAll(failing);

        final StringBuilder msg = new StringBuilder();
        if (!regressions.isEmpty()) {
            msg.append(regressions.size()).append(' ').append(regressedHeading)
                    .append(describe.apply(regressions)).append('\n');
        }
        if (!nowFixed.isEmpty()) {
            msg.append(nowFixed.size()).append(' ').append(unit)
                    .append(" now round-trip but are still listed as known failures.\n")
                    .append("Remove them from ").append(knownListName).append(" so they become regression-guarded:")
                    .append(describe.apply(nowFixed)).append('\n');
        }
        return msg.isEmpty() ? Optional.empty() : Optional.of(msg.toString());
    }

    /** Renders entries one per line, indented, as the failure messages list them. */
    public static String lines(final Set<String> entries) {
        return "\n    " + String.join("\n    ", entries);
    }
}
