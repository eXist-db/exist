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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DirectoryScanner {


    private final static String extractBaseDir(final String pattern) {
        int p = 0;
        char ch;
        for (int i = 0; i < pattern.length(); i++) {
            ch = pattern.charAt(i);
            if (ch == File.separatorChar || ch == ':') {
                p = i;
                continue;
            } else if (ch == '*' || ch == '?') {
                if (p > 0) {
                    return pattern.substring(0, p + 1);
                }
            }
        }
        return null;
    }

    public final static List<Path> scanDir(String pattern) throws IOException {
        //TODO : why this test ? File should make it ! -pb
        pattern = pattern.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        String baseDir = extractBaseDir(pattern);
        if (baseDir == null) {
            // Dizzzz ##### Why this dependancy?
            baseDir = System.getProperty("user.dir");
            pattern = baseDir + File.separator + pattern;
        }

        final Path base = Paths.get(baseDir).normalize();
        return scanDir(base, pattern.substring(baseDir.length()));
    }

    public final static List<Path> scanDir(final Path baseDir, String pattern) throws IOException {
        ///TODO : why this test ? File should make it ! -pb
        pattern = pattern.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        final List<Path> list = new ArrayList<>();
        scanDir(list, baseDir, "", pattern);
        return list;
    }

    private final static void scanDir(final List<Path> list, final Path dir, final String vpath, final String pattern) throws IOException {
        final List<Path> files = FileUtils.list(dir);
        for (final Path file : files) {
            final String name = vpath + FileUtils.fileName(file);
            if (Files.isDirectory(file) && matchStart(pattern, name)) {
                scanDir(list, file, name + File.separator, pattern);
            } else if (match(pattern, name)) {
                list.add(file);
            }
        }
    }

    public final static boolean match(String pattern, String name) {
        return SelectorUtils.matchPath(pattern, name);
    }

    public final static boolean matchStart(String pattern, String name) {
        return SelectorUtils.matchPatternStart(pattern, name);
    }

    public static void main(final String args[]) throws IOException {
        List<Path> files = scanDir("/home/*/xml/**/*.xml");
        for (Path file : files) {
            System.out.println(file.toAbsolutePath().toString());
        }

        files = scanDir("/does-not-exist/*.xml");
        for (final Path file : files) {
            System.out.println(file.toAbsolutePath().toString());
        }
    }
}

