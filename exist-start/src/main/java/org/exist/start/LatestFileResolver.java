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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class uses regex pattern matching to find the latest version of a
 * particular jar file.
 *
 * @author Ben Schmaus (exist@benschmaus.com)
 * @version $Revision$
 * @see LatestFileResolver#getResolvedFileName(String)
 */
public class LatestFileResolver {

    // Pattern that can be used to indicate that the
    // latest version of a particular file should be added to the classpath.
    // E.g., commons-fileupload-%latest%.jar would resolve to something like
    // commons-fileupload-1.1.jar.
    private final static Pattern latestVersionPattern = Pattern.compile("(%latest%)");

    // Set debug mode for each file resolver instance based on whether or
    // not the system was started with debugging turned on.
    private static final boolean _debug = Boolean.getBoolean("exist.start.debug");

    /**
     * If the passed file name contains a %latest% token,
     * find the latest version of that file. Otherwise, return
     * the passed file name unmodified.
     *
     * @param filename Path relative to exist home dir of
     *                 a jar file that should be added to the classpath.
     * @return Resolved filename.
     */
    public String getResolvedFileName(final String filename) {
        final Matcher matches = latestVersionPattern.matcher(filename);
        if (!matches.find()) {
            return filename;
        }
        final String[] fileinfo = filename.split("%latest%");
        // Path of file up to the beginning of the %latest% token.
        final String uptoToken = fileinfo[0];

        // Dir that should contain our jar.
        final String containerDirName = uptoToken.substring(0, uptoToken.lastIndexOf(File.separatorChar));

        final Path containerDir = Paths.get(containerDirName);

        final String artifactId = Pattern.quote(uptoToken.substring(uptoToken.lastIndexOf(File.separatorChar) + 1));
        final String suffix = Pattern.quote(fileinfo[1]);
        final String patternString = '^' + artifactId + "(?:(?:[0-9]+(?:(?:\\.|_)[0-9]+)*)(?:-RC[0-9]+)?(?:-SNAPSHOT)?(?:-patched)?(?:-[0-9a-f]{7})?)" + suffix + '$';
        final Pattern pattern = Pattern.compile(patternString);
        final Matcher matcher = pattern.matcher("");

        List<Path> jars;
        try {
            jars = Main.list(containerDir, p -> {
                matcher.reset(Main.fileName(p));
                return matcher.find();
            });
        } catch (final IOException e) {
            System.err.println("ERROR: No jars found in " + containerDir.toAbsolutePath());
            e.printStackTrace();
            jars = Collections.emptyList();
        }

        if (!jars.isEmpty()) {
            final String actualFileName = jars.getFirst().toAbsolutePath().toString();
            if (_debug) {
                System.err.println("Found match: " + actualFileName + " for jar file pattern: " + filename);
            }
            return actualFileName;
        } else {
            if (_debug) {
                System.err.println("WARN: No latest version found for JAR file: '" + filename + "'");
            }
        }
        return filename;
    }
}
