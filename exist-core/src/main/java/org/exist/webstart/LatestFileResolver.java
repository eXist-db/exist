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
package org.exist.webstart;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.exist.util.FileUtils.fileName;

/**
 * This class uses regex pattern matching to find the latest version of a
 * particular jar file.
 *
 * @author Ben Schmaus (exist@benschmaus.com)
 * @version $Revision$
 * @see org.exist.webstart.LatestFileResolver#getResolvedFileName(String)
 */
public class LatestFileResolver {

    private static final Logger LOGGER = LogManager.getLogger();

    // Pattern that can be used to indicate that the
    // latest version of a particular file should be added to the classpath.
    // E.g., commons-fileupload-%latest%.jar would resolve to something like
    // commons-fileupload-1.1.jar.
    private static final Pattern LATEST_VERSION_PATTERN = Pattern.compile("(%latest%)");

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
        final Matcher matches = LATEST_VERSION_PATTERN.matcher(filename);
        if (!matches.find()) {
            return filename;
        }

        final String[] fileinfo = filename.split("%latest%");
        // Path of file up to the beginning of the %latest% token.
        final String uptoToken = fileinfo[0];

        // Directory that should contain our jar.
        final String containerDirName = uptoToken.substring(0, uptoToken.lastIndexOf(File.separatorChar));

        final Path containerDir = Paths.get(containerDirName);

        final String artifactId = Pattern.quote(uptoToken.substring(uptoToken.lastIndexOf(File.separatorChar) + 1));
        final String suffix = Pattern.quote(fileinfo[1]);
        final String patternString = '^' + artifactId + "(?:(?:[0-9]+(?:(?:\\.|_)[0-9]+)*)(?:-RC[0-9]+)?(?:-SNAPSHOT)?(?:-patched)?(?:-[0-9a-f]{7})?)" + suffix + '$';
        final Pattern pattern = Pattern.compile(patternString);
        final Matcher matcher = pattern.matcher("");

        try {
            final List<Path> jars = FileUtils.list(containerDir, p -> {
                matcher.reset(fileName(p));
                return matcher.find();
            });

            if (jars.isEmpty()) {
                LOGGER.warn("WARN: No latest version found for JAR file: '{}'", filename);

            } else {
                final String actualFileName = jars.getFirst().toAbsolutePath().toString();
                LOGGER.debug("Found match: {} for jar file pattern: {}", actualFileName, filename);
                return actualFileName;
            }

        } catch (final IOException e) {
            LOGGER.error("No jars found in {}. Reason: {}", containerDir.toAbsolutePath(), e.getMessage(), e);
        }

        return filename;
    }
}
