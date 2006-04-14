/*
 * eXist Open Source Native XML Database Copyright (C) 2001-06 Wolfgang M.
 * Meier meier@ifs.tu-darmstadt.de http://exist.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id$
 */

package org.exist.start;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class uses regex pattern matching to find the latest version of a
 * particular jar file. 
 * 
 * @see LatestFileResolver#getResolvedFileName(String)
 * 
 */
public class LatestFileResolver {

    // Pattern that can be used to indicate that the
    // latest version of a particular file should be added to the classpath.
    // E.g., commons-fileupload-%latest%.jar would resolve to something like
    // commons-fileupload-1.1.jar.
    private final static Pattern latestVersionPattern = Pattern.compile(
        "(%latest%)"
    );

    // Set debug mode for each file resolver instance based on whether or
    // not the system was started with debugging turned on.
    private static boolean _debug = Boolean.getBoolean("exist.start.debug");
            
    /**
     * If the passed file name contains a %latest% token,
     * find the latest version of that file. Otherwise, return
     * the passed file name unmodified.
     * 
     * @param filename Path relative to exist home dir of
     * a jar file that should be added to the classpath.
     */
    public String getResolvedFileName(String filename) {
        Matcher matches = latestVersionPattern.matcher(filename);
        if (!matches.find()) {
            return filename;
        }
        String[] fileinfo = filename.split("%latest%");
        // Path of file up to the beginning of the %latest% token.
        String uptoToken = fileinfo[0];

        // Dir that should contain our jar.
        String containerDirName = uptoToken.substring(
            0, uptoToken.lastIndexOf(File.separatorChar)
        );

        File containerDir = new File(containerDirName);

        // 0-9 . - and _ are valid chars that can occur where the %latest% token
        // was (maybe allow letters too?).
        String patternString = uptoToken.substring(
            uptoToken.lastIndexOf(File.separatorChar) + 1
        ) + "([\\d\\.\\-_]+)" + fileinfo[1];
        final Pattern pattern = Pattern.compile(patternString);

        File[] jars = containerDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                Matcher matches = pattern.matcher(name);
                return matches.find();
            }
        });
        if (jars.length > 0) {
            String actualFileName = jars[0].getAbsolutePath();
            if (_debug) {
                System.err.println(
                    "Found match: " + actualFileName
                    + " for jar file pattern: " + filename
                );
            }
            return actualFileName;
        } else {
            if (_debug) {
                System.err.println(
                    "WARN: No latest version found for JAR file: '"
                    + filename + "'"
                );
            }
        }
        return filename;
    }    
}
