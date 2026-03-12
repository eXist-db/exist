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
package org.expath.exist.file;

import java.util.List;
import java.util.Map;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * EXPath File Module 4.0 implementation for eXist-db.
 *
 * @see <a href="https://qt4cg.org/specifications/expath-file-40/Overview.html">EXPath File Module 4.0</a>
 */
public class ExpathFileModule extends AbstractInternalModule {

    public static final String NAMESPACE_URI = "http://expath.org/ns/file";
    public static final String PREFIX = "file";
    public static final String INCLUSION_DATE = "2025-05-01";
    public static final String RELEASED_IN_VERSION = "7.0.0";

    private static final FunctionDef[] functions = {
            // FileProperties: exists, is-dir, is-file, is-absolute, last-modified, size(1), size(2)
            new FunctionDef(FileProperties.signatures[0], FileProperties.class),
            new FunctionDef(FileProperties.signatures[1], FileProperties.class),
            new FunctionDef(FileProperties.signatures[2], FileProperties.class),
            new FunctionDef(FileProperties.signatures[3], FileProperties.class),
            new FunctionDef(FileProperties.signatures[4], FileProperties.class),
            new FunctionDef(FileProperties.signatures[5], FileProperties.class),
            new FunctionDef(FileProperties.signatures[6], FileProperties.class),

            // FileIO: read-text(1), read-text(2), read-text-lines(1), read-text-lines(2),
            //         read-text(3-fallback), read-text-lines(3-fallback),
            //         read-binary(1), read-binary(2), read-binary(3)
            new FunctionDef(FileIO.signatures[0], FileIO.class),
            new FunctionDef(FileIO.signatures[1], FileIO.class),
            new FunctionDef(FileIO.signatures[2], FileIO.class),
            new FunctionDef(FileIO.signatures[3], FileIO.class),
            new FunctionDef(FileIO.signatures[4], FileIO.class),
            new FunctionDef(FileIO.signatures[5], FileIO.class),
            new FunctionDef(FileIO.signatures[6], FileIO.class),
            new FunctionDef(FileIO.signatures[7], FileIO.class),
            new FunctionDef(FileIO.signatures[8], FileIO.class),

            // FileWrite: write(2), write(3), write-text(2), write-text(3),
            //            write-text-lines(2), write-text-lines(3), write-binary(2), write-binary(3)
            new FunctionDef(FileWrite.signatures[0], FileWrite.class),
            new FunctionDef(FileWrite.signatures[1], FileWrite.class),
            new FunctionDef(FileWrite.signatures[2], FileWrite.class),
            new FunctionDef(FileWrite.signatures[3], FileWrite.class),
            new FunctionDef(FileWrite.signatures[4], FileWrite.class),
            new FunctionDef(FileWrite.signatures[5], FileWrite.class),
            new FunctionDef(FileWrite.signatures[6], FileWrite.class),
            new FunctionDef(FileWrite.signatures[7], FileWrite.class),

            // FileAppend: append(2), append(3), append-binary, append-text(2), append-text(3),
            //             append-text-lines(2), append-text-lines(3)
            new FunctionDef(FileAppend.signatures[0], FileAppend.class),
            new FunctionDef(FileAppend.signatures[1], FileAppend.class),
            new FunctionDef(FileAppend.signatures[2], FileAppend.class),
            new FunctionDef(FileAppend.signatures[3], FileAppend.class),
            new FunctionDef(FileAppend.signatures[4], FileAppend.class),
            new FunctionDef(FileAppend.signatures[5], FileAppend.class),
            new FunctionDef(FileAppend.signatures[6], FileAppend.class),

            // FileManipulation: copy, move, delete(1), delete(2), create-dir,
            //                   create-temp-dir(2), create-temp-dir(3),
            //                   create-temp-file(2), create-temp-file(3),
            //                   list(1), list(2), list(3),
            //                   children, descendants, list-roots
            new FunctionDef(FileManipulation.signatures[0], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[1], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[2], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[3], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[4], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[5], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[6], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[7], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[8], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[9], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[10], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[11], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[12], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[13], FileManipulation.class),
            new FunctionDef(FileManipulation.signatures[14], FileManipulation.class),

            // FilePaths: name, parent, path-to-native, path-to-uri, resolve-path(1), resolve-path(2)
            new FunctionDef(FilePaths.signatures[0], FilePaths.class),
            new FunctionDef(FilePaths.signatures[1], FilePaths.class),
            new FunctionDef(FilePaths.signatures[2], FilePaths.class),
            new FunctionDef(FilePaths.signatures[3], FilePaths.class),
            new FunctionDef(FilePaths.signatures[4], FilePaths.class),
            new FunctionDef(FilePaths.signatures[5], FilePaths.class),

            // FileSystemProperties: dir-separator, line-separator, path-separator,
            //                       temp-dir, base-dir, current-dir
            new FunctionDef(FileSystemProperties.signatures[0], FileSystemProperties.class),
            new FunctionDef(FileSystemProperties.signatures[1], FileSystemProperties.class),
            new FunctionDef(FileSystemProperties.signatures[2], FileSystemProperties.class),
            new FunctionDef(FileSystemProperties.signatures[3], FileSystemProperties.class),
            new FunctionDef(FileSystemProperties.signatures[4], FileSystemProperties.class),
            new FunctionDef(FileSystemProperties.signatures[5], FileSystemProperties.class)
    };

    public ExpathFileModule(final Map<String, List<? extends Object>> parameters) {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "EXPath File Module 4.0 - http://expath.org/ns/file";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}
