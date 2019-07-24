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

import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.function.FunctionE;
import org.exist.util.crypto.digest.DigestOutputStream;
import org.exist.util.crypto.digest.StreamableDigest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Predicate;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 * @author alex
 */
public class FileUtils {

    private final static Logger LOG = LogManager.getLogger(FileUtils.class);

    /**
     * Convert an array of {@link java.io.File}
     * to an array of {@link java.nio.file.Path}
     *
     * @param files An array of {@link java.io.File}
     *
     * @return If files is null or empty, then
     *  an empty array, otherwise an array of corresponding
     *  {@link java.nio.file.Path}
     */
    public static Path[] asPaths(final File[] files) {
        return asPathsList(files).toArray(new Path[files.length]);
    }

    /**
     * Convert an array of {@link java.io.File}
     * to a List of {@link java.nio.file.Path}
     *
     * @param files An array of {@link java.io.File}
     *
     * @return If files is null or empty, then
     *  an empty list, otherwise a list of corresponding
     *  {@link java.nio.file.Path}
     */
    public static List<Path> asPathsList(final File[] files) {
        return Optional.ofNullable(files)
                .map(fs -> Arrays.stream(fs).map(File::toPath).collect(Collectors.toList()))
                .orElse(Collections.EMPTY_LIST);
    }

    /**
     * Copies a path within the filesystem
     *
     * If the path is a directory its contents
     * will be recursively copied.
     *
     * Note that copying of a directory is not an atomic-operation
     * and so if an error occurs during copying, some of the directories
     * descendants may have already been copied.
     *
     * @param source the source file or directory
     * @param destination the destination file or directory
     *
     * @throws IOException if an error occurs whilst copying a file or directory
     */
    public static void copy(final Path source, final Path destination) throws IOException {
        if (!Files.isDirectory(source)) {
            Files.copy(source, destination);
        } else {
            if (Files.exists(destination) && !Files.isDirectory(destination)) {
                throw new IOException("Cannot copy a directory to a file");
            }
            Files.walkFileTree(source, copyDirVisitor(source, destination));
        }
    }

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
     * @param path the path to delete.
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
     * @param path the path to delete
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

    private final static SimpleFileVisitor<Path> copyDirVisitor(final Path source, final Path destination) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IOException("source must be a directory");
        }
        if (!Files.isDirectory(destination)) {
            throw new IOException("destination must be a directory");
        }
        return new CopyDirVisitor(source, destination);
    }

    private static class CopyDirVisitor extends SimpleFileVisitor<Path> {
        private final Path source;
        private final Path destination;

        public CopyDirVisitor(final Path source, final Path destination) {
            this.source = source;
            this.destination = destination;
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
            final Path relSourceDir = source.relativize(dir);
            final Path targetDir = destination.resolve(relSourceDir);
            Files.createDirectories(targetDir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            final Path relSourceFile = source.relativize(file);
            final Path targetFile = destination.resolve(relSourceFile);
            Files.copy(file, targetFile);
            return FileVisitResult.CONTINUE;
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
     * Determine the size of a collection of files and/or directories.
     *
     * @param paths the paths to determine the size of
     *
     * @return The size of the files and directories, or -1 if the file size cannot be determined
     */
    public static long sizeQuietly(final Collection<Path> paths) {
        return paths.stream().map(FileUtils::sizeQuietly)
                .filter(size -> size != -1)
                .reduce((a, b) -> a + b)
                .orElse(-1l);
    }

    /**
     * Determine the size of a file or directory.
     *
     * @param path the path to determine the size of
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

    /**
     * Make a measurement of the FileStore for a particular path
     *
     * @param path The path for which the FileStore should be measured
     * @param measurer A function which provided with a FileStore makes a measurement
     *
     * @return The measured value or -1 if an error occurred whilst accessing the FileStore
     */
    public static long measureFileStore(final Path path, final FunctionE<FileStore, Long, IOException> measurer) {
        try {
            return measurer.apply(Files.getFileStore(path));
        } catch(final IOException ioe) {
            LOG.error(ioe);
            return -1l;
        }
    }

    /**
     * Determine the last modified time of a file or directory.
     *
     * @param path get the last modified time of the path.
     *
     * @return Either an IOException or the last modified time of the file or directory
     */
    public static Either<IOException, FileTime> lastModifiedQuietly(final Path path) {
        try {
            return Either.Right(Files.getLastModifiedTime(path));
        } catch (final IOException e) {
            return Either.Left(e);
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
     * Makes directories on the filesystem the filesystem
     *
     * This method will never throw an IOException, it
     * instead returns `false` if an error occurs
     * whilst creating a file or directory
     *
     * Note that creation of a directory is not an atomic-operation
     * and so if an error occurs during creation, some of the directories
     * descendants may have already been created
     *
     * @param dir the path to create
     *
     * @return false if an error occurred, true otherwise
     */
    public static boolean mkdirsQuietly(final Path dir) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            return true;
        } catch (final IOException ioe) {
            LOG.error("Unable to mkdirs: " + dir.toAbsolutePath().toString(), ioe);
            return false;
        }
    }

    /**
     * Attempts to resolve the child
     * against the parent.
     *
     * If there is no parent, then the child
     * is resolved relative to the CWD
     *
     * @param parent the parent to resolve the {@code child} from
     * @param child the child to resolve from the parent
     *
     * @return The resolved path
     */
    public static Path resolve(final Optional<Path> parent, final String child) {
        return parent.map(p -> p.resolve(child)).orElse(Paths.get(child));
    }

    /**
     * Get just the filename part of the path
     *
     * @param path the path to get the filename from
     *
     * @return The filename
     */
    public static String fileName(final Path path) {
        return path.getFileName().toString();
    }

    /**
     * A list of the entries in the directory. The listing is not recursive.
     *
     * @param directory The directory to list the entries for
     *
     * @return The list of entries
     *
     * @throws IOException if an IO error occurs
     */
    public static List<Path> list(final Path directory) throws IOException {
        try(final Stream<Path> entries = Files.list(directory)) {
            return entries.collect(Collectors.toList());
        }
    }

    /**
     * A list of the entries in the directory. The listing is not recursive.
     *
     * @param directory The directory to list the entries for
     * @param filter A filter to be applied to the list
     * @return The list of entries
     *
     * @throws IOException if an IO error occurs
     */
    public static List<Path> list(final Path directory, final Predicate<Path> filter) throws IOException {
        try(final Stream<Path> entries = Files.list(directory).filter(filter)) {
            return entries.collect(Collectors.toList());
        }
    }

    /**
     * Get the directory name from the path.
     *
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
     * Concenate two paths with a path separator.
     *
     * @param path1 the first path.
     * @param path2 the second path.
     *
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

    /**
     * Copy a file whilst generating a digest of the bytes that are being written.
     *
     * Destination file will be overwritten.
     *
     * @param src the source file.
     * @param dst the destination file
     * @param streamableDigest the digest
     *
     * @throws IOException if an IO error occurs
     */
    public static void copyWithDigest(final Path src, final Path dst, final StreamableDigest streamableDigest) throws IOException {
        copyWithDigest(src, dst, streamableDigest, WRITE, TRUNCATE_EXISTING);
    }

    /**
     * Copy a file whilst generating a digest of the bytes that are being written.
     *
     * @param src the source file.
     * @param dst the destination file
     * @param streamableDigest the digest
     * @param dstOptions options for writing the destination file
     *
     * @throws IOException if an IO error occurs
     */
    public static void copyWithDigest(final Path src, final Path dst, final StreamableDigest streamableDigest,
            final OpenOption... dstOptions) throws IOException {
        try (final InputStream is = Files.newInputStream(src, READ)) {
            copyWithDigest(is, dst, streamableDigest);
        }
    }

    /**
     * Copy data to a file whilst generating a digest of the bytes that are being written.
     *
     * Destination file will be overwritten.
     *
     * @param is the source data.
     * @param dst the destination file
     * @param streamableDigest the digest
     *
     * @throws IOException if an IO error occurs
     */
    public static void copyWithDigest(final InputStream is, final Path dst, final StreamableDigest streamableDigest) throws IOException {
        copyWithDigest(is, dst, streamableDigest, WRITE, TRUNCATE_EXISTING);
    }

    /**
     * Copy data to a file whilst generating a digest of the bytes that are being written.
     *
     * @param is the source data.
     * @param dst the destination file
     * @param streamableDigest the digest
     * @param dstOptions options for writing the destination file
     *
     * @throws IOException if an IO error occurs
     */
    public static void copyWithDigest(final InputStream is, final Path dst, final StreamableDigest streamableDigest, final OpenOption... dstOptions) throws IOException {
        try (final OutputStream os = new DigestOutputStream(Files.newOutputStream(dst, dstOptions), streamableDigest)) {

            final byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                os.write(buf, 0, read);
            }
        }
    }

    /**
     * Calculates the digest for a file.
     *
     * @param path the file to calculate the digest for.
     * @param streamableDigest the digest.
     *
     * @throws IOException if an IO error occurs
     */
    public static void digest(final Path path, final StreamableDigest streamableDigest) throws IOException {
        try (final InputStream is = Files.newInputStream(path, READ)) {
            final byte[] buf = new byte[8192];

            int read;
            while ((read = is.read(buf)) != -1) {
                streamableDigest.update(buf, 0, read);
            }
        }
    }
    
    /**
     * Provides a filter for entries in a directory.
     *
     * @param prefix the prefix used for filtering.
     * @param suffix the suffix used for filtering.
     * 
     * @return The filter.
     */
	public static Predicate<Path> getPrefixSuffixFilter(final String prefix, final String suffix) {
		return path -> {
			String entryName = path.getFileName().toString();
			
			return entryName.startsWith(prefix) && entryName.endsWith(suffix);
		};
	}    
}
