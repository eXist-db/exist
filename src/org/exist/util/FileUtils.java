/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 * @author alex
 */
public class FileUtils {

    private final static Logger LOG = LogManager.getLogger(FileUtils.class);

    /**
     * Deletes a path from the filesystem
     *
     * If the path is a directory its contents
     * will be recursively deleted before it itself
     * is deleted.
     *
     * Note that removal of a directory is not an atomic-operation
     * and so if an error occurs during removal, some of the directories
     * descendants may have already been removed
     *
     * @throws IOException if an error occurs whilst removing a file or directory
     */
    public static void delete(final Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            Files.deleteIfExists(path);
        } else {
            Files.walkFileTree(path, deleteDirVisitor);
        }
    }

    /**
     * Deletes a path from the filesystem
     *
     * If the path is a directory its contents
     * will be recursively deleted before it itself
     * is deleted
     *
     * This method will never throw an IOException, it
     * instead returns `false` if an error occurs
     * whilst removing a file or directory
     *
     * Note that removal of a directory is not an atomic-operation
     * and so if an error occurs during removal, some of the directories
     * descendants may have already been removed
     *
     * @return false if an error occurred, true otherwise
     */
    public static boolean deleteQuietly(final Path path) {
        try {
            if (!Files.isDirectory(path)) {
                return Files.deleteIfExists(path);
            } else {
                Files.walkFileTree(path, deleteDirVisitor);
            }
            return true;
        } catch (final IOException ioe) {
            LOG.error("Unable to delete: " + path.toAbsolutePath().toString(), ioe);
            return false;
        }
    }

    private final static SimpleFileVisitor<Path> deleteDirVisitor = new DeleteDirVisitor();

    private static class DeleteDirVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            if (exc != null) {
                throw exc;
            }

            Files.deleteIfExists(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Determine the size of a file or directory
     *
     * @return The size of the file or directory, or -1 if the file size cannot be determined
     */
    public static long sizeQuietly(final Path path) {
        try {
            if(!Files.isDirectory(path)) {
                return Files.size(path);
            } else {
                final DirSizeVisitor dirSizeVisitor = new DirSizeVisitor();
                Files.walkFileTree(path, dirSizeVisitor);
                return dirSizeVisitor.totalSize();
            }
        } catch(final IOException ioe) {
            LOG.error("Unable to determine size of: " + path.toString(), ioe);
            return -1;
        }
    }

    private static class DirSizeVisitor extends SimpleFileVisitor<Path> {

        private long size = 0;

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            size += Files.size(file);
            return FileVisitResult.CONTINUE;
        }

        public long totalSize() {
            return size;
        }
    }

    /**
     * Attempts to resolve the child
     * against the parent.
     *
     * If there is no parent, then the child
     * is resolved relative to the CWD
     *
     * @return The resolved path
     */
    public static Path resolve(final Optional<Path> parent, final String child) {
        return parent.map(p -> p.resolve(child)).orElse(Paths.get(child));
    }

    /**
     * Get just the filename part of the path
     *
     * @return The filename
     */
    public static String fileName(final Path path) {
        return path.getFileName().toString();
    }

    /**
     * A list of the entries in the directory. The listing is not recursive.
     *
     * @return The list of entries
     */
    public static List<Path> list(final Path directory) throws IOException {
        try(final Stream<Path> entries = Files.list(directory)) {
            return entries.collect(Collectors.toList());
        }
    }

    /**
     * @param path a path or uri
     * @return the directory portion of a path by stripping the last '/' and
     * anything following, unless the path has no '/', in which case '.' is returned,
     * or ends with '/', in
     * which case return the path unchanged.
     */
    public static String dirname(final String path) {
        final int islash = path.lastIndexOf('/');
        if (islash >= 0 && islash < path.length() - 1) {
            return path.substring(0, islash);
        } else if (islash >= 0) {
            return path;
        } else {
            return ".";
        }
    }

    /**
     * @param path1
     * @param path2
     * @return path1 + path2, joined by a single file separator (or /, if a slash is already present).
     */
    public static String addPaths(final String path1, final String path2) {
        if (path1.endsWith("/") || path2.endsWith(File.separator)) {
            if (path2.startsWith("/") || path2.startsWith(File.separator)) {
                return path1 + path2.substring(1);
            } else {
                return path1 + path2;
            }
        } else {
            if (path2.startsWith("/") || path2.startsWith(File.separator)) {
                return path1 + path2;
            } else {
                return path1 + File.separatorChar + path2;
            }
        }
    }

}
