
package org.exist.start;
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
import java.util.Optional;

import static org.exist.start.Main.ERROR_CODE_INCOMPATIBLE_JAVA_DETECTED;

public class CompatibleJavaVersionCheck {

    private static final int[] INCOMPATIBLE_MAJOR_JAVA_VERSIONS = { 12, 13, 14, 15 };

    private static final String INCOMPATIBLE_JAVA_VERSION_NOTICE =
            "*****************************************************%n" +
            "Warning: Unreliable Java version has been detected!%n" +
            "%n" +
            "OpenJDK versions 12-15 suffer from a critical bug in the%n" +
            "JIT C2 compiler that will cause data loss in eXist-db.%n" +
            "%n" +
            "The problem has been reported to the OpenJDK community.%n" +
            "%n" +
            "For more information, see:%n" +
            "\t* https://bugs.openjdk.java.net/browse/JDK-8253191%n" +
            "\t* https://github.com/eXist-db/exist/issues/3375%n" +
            "%n" +
            "The detected version of Java on your system is: %s.%n" +
            "%n" +
            "To prevent potential data loss, eXist-db will not be started.%n" +
            "To start eXist-db, we recommend using Java 8 or 11.%n" +
            "*****************************************************";

    private static final Optional<String> RUNTIME_JAVA_VERSION = Optional.ofNullable(System.getProperty("java.version"));

    /**
     * Checks that the runtime version of Java is compatible
     * with eXist-db.
     *
     * @throws StartException if the runtime version of Java is incompatible with eXist-db.
     */
    public static void checkForCompatibleJavaVersion() throws StartException {
        final Optional<Integer> maybeRuntimeMajorJavaVersion = RUNTIME_JAVA_VERSION
                .map(str -> str.split("\\."))
                .filter(ary -> ary.length > 0)
                .map(ary -> ary[0])
                .filter(str -> !str.isEmpty())
                .map(str -> { try { return Integer.parseInt(str); } catch (final NumberFormatException e) { return -1; }})
                .filter(i -> i != -1);

        if (!maybeRuntimeMajorJavaVersion.isPresent()) {
            // Could not determine major java version of runtime, so best to let the user proceed...
            return;
        }

        // check for incompatible major java version
        final int runtimeMajorJavaVersion = maybeRuntimeMajorJavaVersion.get();
        for (int i = 0; i < INCOMPATIBLE_MAJOR_JAVA_VERSIONS.length; i++) {
            if (runtimeMajorJavaVersion == INCOMPATIBLE_MAJOR_JAVA_VERSIONS[i]) {
                throw new StartException(ERROR_CODE_INCOMPATIBLE_JAVA_DETECTED, String.format(INCOMPATIBLE_JAVA_VERSION_NOTICE, RUNTIME_JAVA_VERSION));
            }
        }
    }
}
