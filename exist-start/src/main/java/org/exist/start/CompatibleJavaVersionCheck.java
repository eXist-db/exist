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
package org.exist.start;

import java.util.Optional;
import java.util.stream.Stream;

import static org.exist.start.CompatibleJavaVersionCheck.IncompatibleJavaVersion.IncompatibleJavaVersion;
import static org.exist.start.Main.ERROR_CODE_INCOMPATIBLE_JAVA_DETECTED;

public class CompatibleJavaVersionCheck {

    private static final IncompatibleJavaVersion[] INCOMPATIBLE_JAVA_VERSIONS = {
            IncompatibleJavaVersion(12),
            IncompatibleJavaVersion(13),
            IncompatibleJavaVersion(14),
            IncompatibleJavaVersion(15, 0, 2)
    };

    private static final String INCOMPATIBLE_JAVA_VERSION_NOTICE =
            "*****************************************************%n" +
            "Warning: Unreliable Java version has been detected!%n" +
            "%n" +
            "OpenJDK versions 12 through 15.0.1 suffer from a critical%n" +
            " bug in the JIT C2 compiler that will cause data loss in%n" +
            "eXist-db.%n" +
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
        checkForCompatibleJavaVersion(RUNTIME_JAVA_VERSION);
    }

    static void checkForCompatibleJavaVersion(final Optional<String> checkJavaVersion) throws StartException {
        final Optional<int[]> maybeJavaVersionComponents = extractJavaVersionComponents(checkJavaVersion);

        if (!maybeJavaVersionComponents.isPresent()) {
            // Could not determine major java version, so best to let the user proceed...
            return;
        }

        // check for incompatible java version
        final int[] javaVersionComponents = maybeJavaVersionComponents.get();
        final int majorJavaVersion = javaVersionComponents[0];
        /* @Nullable */ final Integer minorJavaVersion = javaVersionComponents.length > 1 ? javaVersionComponents[1] : null;
        /* @Nullable */ final Integer patchJavaVersion = javaVersionComponents.length > 2 ? javaVersionComponents[2] : null;

        for (final IncompatibleJavaVersion incompatibleJavaVersion : INCOMPATIBLE_JAVA_VERSIONS) {
            // compare major versions
            if (majorJavaVersion == incompatibleJavaVersion.major) {

                // major version might be incompatible

                if (incompatibleJavaVersion.lessThanMinor != null && minorJavaVersion != null) {
                    // compare minor version
                    if (minorJavaVersion >= incompatibleJavaVersion.lessThanMinor) {
                        // minor version is compatible

                        if (incompatibleJavaVersion.lessThanPatch != null && patchJavaVersion != null) {
                            // compare patch version
                            if (patchJavaVersion >= incompatibleJavaVersion.lessThanPatch) {
                                // patch version is compatible
                                continue;
                            }
                        }
                    }
                }

                // version is NOT compatible!
                throw new StartException(ERROR_CODE_INCOMPATIBLE_JAVA_DETECTED, String.format(INCOMPATIBLE_JAVA_VERSION_NOTICE, RUNTIME_JAVA_VERSION));
            }

            // version is compatible
        }
    }

    static Optional<int[]> extractJavaVersionComponents(final Optional<String> javaVersion) {
        return javaVersion
                .map(str -> str.split("\\.|_|-"))
                .filter(ary -> ary.length > 0)
                .map(ary ->
                        Stream.of(ary)
                                .filter(str -> !str.isEmpty())
                                .map(str -> { try { return Integer.parseInt(str); } catch (final NumberFormatException e) { return -1; }})
                                .filter(i -> i != -1)
                                .mapToInt(Integer::intValue)
                                .toArray()
                )
                .filter(ary -> ary.length > 0);
    }

    static class IncompatibleJavaVersion {
        final int major;
        /* @Nullable */  final Integer lessThanMinor;
        /* @Nullable */ final Integer lessThanPatch;

        private IncompatibleJavaVersion(final int major, /* @Nullable */ Integer lessThanMinor, /* @Nullable */ Integer lessThanPatch) {
            this.major = major;
            this.lessThanMinor = lessThanMinor;
            this.lessThanPatch = lessThanPatch;
        }

        public static IncompatibleJavaVersion IncompatibleJavaVersion(final int major, /* @Nullable */ Integer lessThanMinor, /* @Nullable */ Integer lessThanPatch) {
            return new IncompatibleJavaVersion(major, lessThanMinor, lessThanPatch);
        }

        public static IncompatibleJavaVersion IncompatibleJavaVersion(final int major, /* @Nullable */ Integer lessThanMinor) {
            return IncompatibleJavaVersion(major, lessThanMinor, null);
        }

        public static IncompatibleJavaVersion IncompatibleJavaVersion(final int major) {
            return IncompatibleJavaVersion(major, null, null);
        }
    }
}
