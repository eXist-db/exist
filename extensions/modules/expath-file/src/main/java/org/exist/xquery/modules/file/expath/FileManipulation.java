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
package org.exist.xquery.modules.file.expath;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

/**
 * EXPath File Module 4.0 - File/directory manipulation and listing functions.
 * <p>
 * Implements: file:copy, file:move, file:delete, file:create-dir, file:create-temp-dir,
 * file:create-temp-file, file:list, file:children, file:descendants
 */
public class FileManipulation extends BasicFunction {

    private static final FunctionParameterSequenceType PATH_PARAM =
            new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE, "The file or directory path.");

    public static final FunctionSignature[] signatures = {
            // file:copy($source, $target)
            new FunctionSignature(
                    new QName("copy", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Copies a file or directory. If the target exists it is overwritten.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("source", Type.STRING, Cardinality.EXACTLY_ONE, "Source path."),
                            new FunctionParameterSequenceType("target", Type.STRING, Cardinality.EXACTLY_ONE, "Target path.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:move($source, $target)
            new FunctionSignature(
                    new QName("move", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Moves a file or directory.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("source", Type.STRING, Cardinality.EXACTLY_ONE, "Source path."),
                            new FunctionParameterSequenceType("target", Type.STRING, Cardinality.EXACTLY_ONE, "Target path.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:delete($path)
            new FunctionSignature(
                    new QName("delete", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Deletes a file or empty directory.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:delete($path, $recursive)
            new FunctionSignature(
                    new QName("delete", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Deletes a file or directory. If $recursive is true, non-empty directories are removed recursively.",
                    new SequenceType[]{
                            PATH_PARAM,
                            new FunctionParameterSequenceType("recursive", Type.BOOLEAN, Cardinality.ZERO_OR_ONE,
                                    "If true, delete directories recursively. Default: false.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:create-dir($dir)
            new FunctionSignature(
                    new QName("create-dir", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Creates a directory, including any necessary parent directories.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("dir", Type.STRING, Cardinality.EXACTLY_ONE, "The directory path.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:create-temp-dir($prefix, $suffix)
            new FunctionSignature(
                    new QName("create-temp-dir", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Creates a temporary directory in the system default temp directory.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.ZERO_OR_ONE, "Prefix for the directory name."),
                            new FunctionParameterSequenceType("suffix", Type.STRING, Cardinality.ZERO_OR_ONE, "Suffix for the directory name.")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "The path of the created temporary directory.")
            ),
            // file:create-temp-dir($prefix, $suffix, $dir)
            new FunctionSignature(
                    new QName("create-temp-dir", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Creates a temporary directory.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.ZERO_OR_ONE, "Prefix for the directory name."),
                            new FunctionParameterSequenceType("suffix", Type.STRING, Cardinality.ZERO_OR_ONE, "Suffix for the directory name."),
                            new FunctionParameterSequenceType("dir", Type.STRING, Cardinality.ZERO_OR_ONE, "The parent directory. Default: system temp dir.")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "The path of the created temporary directory.")
            ),
            // file:create-temp-file($prefix, $suffix)
            new FunctionSignature(
                    new QName("create-temp-file", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Creates a temporary file in the system default temp directory.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.ZERO_OR_ONE, "Prefix for the file name."),
                            new FunctionParameterSequenceType("suffix", Type.STRING, Cardinality.ZERO_OR_ONE, "Suffix for the file name.")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "The path of the created temporary file.")
            ),
            // file:create-temp-file($prefix, $suffix, $dir)
            new FunctionSignature(
                    new QName("create-temp-file", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Creates a temporary file.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("prefix", Type.STRING, Cardinality.ZERO_OR_ONE, "Prefix for the file name."),
                            new FunctionParameterSequenceType("suffix", Type.STRING, Cardinality.ZERO_OR_ONE, "Suffix for the file name."),
                            new FunctionParameterSequenceType("dir", Type.STRING, Cardinality.ZERO_OR_ONE, "The parent directory. Default: system temp dir.")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "The path of the created temporary file.")
            ),
            // file:list($dir)
            new FunctionSignature(
                    new QName("list", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Lists the contents of a directory as relative paths.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("dir", Type.STRING, Cardinality.EXACTLY_ONE, "The directory path.")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The relative paths of directory contents.")
            ),
            // file:list($dir, $recursive)
            new FunctionSignature(
                    new QName("list", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Lists the contents of a directory, optionally recursively.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("dir", Type.STRING, Cardinality.EXACTLY_ONE, "The directory path."),
                            new FunctionParameterSequenceType("recursive", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "If true, list recursively. Default: false.")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The relative paths of directory contents.")
            ),
            // file:list($dir, $recursive, $pattern)
            new FunctionSignature(
                    new QName("list", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Lists the contents of a directory matching a glob pattern.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("dir", Type.STRING, Cardinality.EXACTLY_ONE, "The directory path."),
                            new FunctionParameterSequenceType("recursive", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "If true, list recursively. Default: false."),
                            new FunctionParameterSequenceType("pattern", Type.STRING, Cardinality.ZERO_OR_ONE, "A glob pattern to filter results.")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The relative paths of matching directory contents.")
            ),
            // file:children($path)
            new FunctionSignature(
                    new QName("children", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the paths of immediate children of a directory.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The absolute paths of children.")
            ),
            // file:descendants($path)
            new FunctionSignature(
                    new QName("descendants", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the paths of all descendants of a directory recursively.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The absolute paths of all descendants.")
            ),
            // file:list-roots()
            new FunctionSignature(
                    new QName("list-roots", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the root directories of the file system.",
                    new SequenceType[]{},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The root directories.")
            )
    };

    public FileManipulation(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        ExpathFileModuleHelper.checkDbaRole(context, this);

        if (isCalledAs("list-roots")) {
            return listRoots();
        }

        final String pathStr = args[0].getStringValue();
        final Path path = ExpathFileModuleHelper.getPath(pathStr, this, context);

        if (isCalledAs("copy")) {
            return copy(path, args);
        } else if (isCalledAs("move")) {
            return move(path, args);
        } else if (isCalledAs("delete")) {
            return delete(path, args);
        } else if (isCalledAs("create-dir")) {
            return createDir(path);
        } else if (isCalledAs("create-temp-dir")) {
            return createTempDir(args);
        } else if (isCalledAs("create-temp-file")) {
            return createTempFile(args);
        } else if (isCalledAs("list")) {
            return list(path, args);
        } else if (isCalledAs("children")) {
            return children(path);
        } else if (isCalledAs("descendants")) {
            return descendants(path);
        }

        throw new XPathException(this, "Unknown function: " + getSignature().getName().getLocalPart());
    }

    private Sequence copy(final Path source, final Sequence[] args) throws XPathException {
        if (!Files.exists(source)) {
            throw new XPathException(this, ExpathFileErrorCode.NOT_FOUND,
                    "Source does not exist: " + source.toAbsolutePath());
        }
        final Path target = ExpathFileModuleHelper.getPath(args[1].getStringValue(), this, context);

        // Check target parent directory exists
        final Path targetParent = target.toAbsolutePath().getParent();
        if (targetParent != null && !Files.isDirectory(targetParent)) {
            throw new XPathException(this, ExpathFileErrorCode.NO_DIR,
                    "Target parent directory does not exist: " + targetParent);
        }

        try {
            if (Files.isDirectory(source)) {
                copyDirectory(source, target);
            } else {
                // If target is an existing directory, copy into it
                final Path actualTarget = Files.isDirectory(target) ? target.resolve(source.getFileName()) : target;
                Files.copy(source, actualTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private void copyDirectory(final Path source, final Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                final Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Sequence move(final Path source, final Sequence[] args) throws XPathException {
        if (!Files.exists(source)) {
            throw new XPathException(this, ExpathFileErrorCode.NOT_FOUND,
                    "Source does not exist: " + source.toAbsolutePath());
        }
        final Path target = ExpathFileModuleHelper.getPath(args[1].getStringValue(), this, context);

        // Check target parent directory exists
        final Path targetParent = target.toAbsolutePath().getParent();
        if (targetParent != null && !Files.isDirectory(targetParent)) {
            throw new XPathException(this, ExpathFileErrorCode.NO_DIR,
                    "Target parent directory does not exist: " + targetParent);
        }

        try {
            // If target is an existing directory, move into it
            final Path actualTarget = Files.isDirectory(target) ? target.resolve(source.getFileName()) : target;
            Files.move(source, actualTarget, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence delete(final Path path, final Sequence[] args) throws XPathException {
        if (!Files.exists(path)) {
            throw new XPathException(this, ExpathFileErrorCode.NOT_FOUND,
                    "Path does not exist: " + path.toAbsolutePath());
        }
        final boolean recursive = args.length > 1 && !args[1].isEmpty()
                && args[1].itemAt(0).toJavaObject(Boolean.class);

        try {
            if (Files.isDirectory(path)) {
                if (recursive) {
                    try (final Stream<Path> walk = Files.walk(path)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(p -> {
                                    try {
                                        Files.delete(p);
                                    } catch (final IOException e) {
                                        throw new UncheckedIOException(e);
                                    }
                                });
                    }
                } else {
                    // Attempt to delete; will fail if non-empty
                    try {
                        Files.delete(path);
                    } catch (final DirectoryNotEmptyException e) {
                        throw new XPathException(this, ExpathFileErrorCode.IS_DIR,
                                "Directory is not empty (use $recursive = true()): " + path.toAbsolutePath());
                    }
                }
            } else {
                Files.delete(path);
            }
        } catch (final UncheckedIOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getCause().getMessage());
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence createDir(final Path path) throws XPathException {
        // Check if the path itself or any ancestor is an existing non-directory file
        Path check = path.toAbsolutePath().normalize();
        while (check != null) {
            if (Files.exists(check) && !Files.isDirectory(check)) {
                throw new XPathException(this, ExpathFileErrorCode.EXISTS,
                        "Path exists and is not a directory: " + check);
            }
            check = check.getParent();
        }
        try {
            Files.createDirectories(path);
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence createTempDir(final Sequence[] args) throws XPathException {
        final String prefix = args.length > 0 && !args[0].isEmpty() ? args[0].getStringValue() : "";
        final String suffix = args.length > 1 && !args[1].isEmpty() ? args[1].getStringValue() : "";
        final Path dir = args.length > 2 && !args[2].isEmpty()
                ? ExpathFileModuleHelper.getPath(args[2].getStringValue(), this, context)
                : Paths.get(System.getProperty("java.io.tmpdir"));

        if (!Files.isDirectory(dir)) {
            throw new XPathException(this, ExpathFileErrorCode.NO_DIR,
                    "Parent is not a directory: " + dir.toAbsolutePath());
        }

        try {
            // Java's createTempDirectory only supports prefix, so we append suffix manually
            final Path tempDir = Files.createTempDirectory(dir, prefix);
            if (!suffix.isEmpty()) {
                final Path renamed = tempDir.resolveSibling(tempDir.getFileName().toString() + suffix);
                Files.move(tempDir, renamed);
                return new StringValue(this, renamed.toAbsolutePath().toString() + File.separator);
            }
            return new StringValue(this, tempDir.toAbsolutePath().toString() + File.separator);
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
    }

    private Sequence createTempFile(final Sequence[] args) throws XPathException {
        final String prefix = args.length > 0 && !args[0].isEmpty() ? args[0].getStringValue() : "";
        final String suffix = args.length > 1 && !args[1].isEmpty() ? args[1].getStringValue() : "";
        final Path dir = args.length > 2 && !args[2].isEmpty()
                ? ExpathFileModuleHelper.getPath(args[2].getStringValue(), this, context)
                : Paths.get(System.getProperty("java.io.tmpdir"));

        if (!Files.isDirectory(dir)) {
            throw new XPathException(this, ExpathFileErrorCode.NO_DIR,
                    "Parent is not a directory: " + dir.toAbsolutePath());
        }

        try {
            final Path tempFile = Files.createTempFile(dir, prefix, suffix.isEmpty() ? null : suffix);
            return new StringValue(this, tempFile.toAbsolutePath().toString());
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
    }

    @SuppressWarnings("PMD.NPathComplexity")
    private Sequence list(final Path dir, final Sequence[] args) throws XPathException {
        if (!Files.exists(dir)) {
            throw new XPathException(this, ExpathFileErrorCode.NOT_FOUND,
                    "Directory does not exist: " + dir.toAbsolutePath());
        }
        if (!Files.isDirectory(dir)) {
            throw new XPathException(this, ExpathFileErrorCode.NO_DIR,
                    "Path is not a directory: " + dir.toAbsolutePath());
        }

        final boolean recursive = args.length > 1 && !args[1].isEmpty()
                && args[1].itemAt(0).toJavaObject(Boolean.class);
        final String pattern = args.length > 2 && !args[2].isEmpty()
                ? args[2].getStringValue() : null;

        final Pattern regex = pattern != null ? globToRegex(pattern) : null;

        try {
            final ValueSequence result = new ValueSequence();
            try (final Stream<Path> stream = recursive ? Files.walk(dir) : Files.list(dir)) {
                stream.filter(p -> !p.equals(dir))
                        .forEach(p -> {
                            final String relative = dir.relativize(p).toString();
                            final String entry = Files.isDirectory(p)
                                    ? relative + File.separator : relative;
                            if (regex == null || regex.matcher(entry.replace(File.separator, "/")).matches()
                                    || regex.matcher(p.getFileName().toString()).matches()) {
                                result.add(new StringValue(this, entry));
                            }
                        });
            }
            return result;
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
    }

    private Sequence children(final Path path) throws XPathException {
        if (!Files.exists(path)) {
            throw new XPathException(this, ExpathFileErrorCode.NOT_FOUND,
                    "Path does not exist: " + path.toAbsolutePath());
        }
        if (!Files.isDirectory(path)) {
            throw new XPathException(this, ExpathFileErrorCode.NO_DIR,
                    "Path is not a directory: " + path.toAbsolutePath());
        }

        try {
            final ValueSequence result = new ValueSequence();
            try (final Stream<Path> stream = Files.list(path)) {
                stream.forEach(p -> {
                    final String absPath = p.toAbsolutePath().toString();
                    final String entry = Files.isDirectory(p) ? absPath + File.separator : absPath;
                    result.add(new StringValue(this, entry));
                });
            }
            return result;
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
    }

    private Sequence descendants(final Path path) throws XPathException {
        if (!Files.exists(path)) {
            throw new XPathException(this, ExpathFileErrorCode.NOT_FOUND,
                    "Path does not exist: " + path.toAbsolutePath());
        }
        if (!Files.isDirectory(path)) {
            throw new XPathException(this, ExpathFileErrorCode.NO_DIR,
                    "Path is not a directory: " + path.toAbsolutePath());
        }

        try {
            final ValueSequence result = new ValueSequence();
            try (final Stream<Path> walk = Files.walk(path)) {
                walk.filter(p -> !p.equals(path))
                        .forEach(p -> {
                            final String absPath = p.toAbsolutePath().toString();
                            final String entry = Files.isDirectory(p) ? absPath + File.separator : absPath;
                            result.add(new StringValue(this, entry));
                        });
            }
            return result;
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
    }

    private Sequence listRoots() {
        final ValueSequence result = new ValueSequence();
        for (final File root : File.listRoots()) {
            result.add(new StringValue(this, root.getAbsolutePath()));
        }
        return result;
    }

    /**
     * Convert a simple glob pattern (with * and ?) to a Java regex Pattern.
     */
    private static Pattern globToRegex(final String glob) {
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            final char c = glob.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '[', ']', '{', '}', '\\', '^', '$', '|', '+' ->
                        regex.append('\\').append(c);
                default -> regex.append(c);
            }
        }
        return Pattern.compile(regex.toString());
    }
}
