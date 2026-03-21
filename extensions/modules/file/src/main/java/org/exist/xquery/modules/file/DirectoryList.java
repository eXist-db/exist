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
package org.exist.xquery.modules.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.tools.ant.DirectoryScanner;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.FileUtils;
import org.exist.xquery.*;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import static org.exist.xquery.modules.file.FileErrorCode.DIRECTORY_NOT_FOUND;

/**
 * eXist File Module Extension DirectoryList
 * <p>
 * Enumerate a list of files, including their size and modification time, found
 * in a specified directory, using a pattern
 *
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author ljo
 * @version 1.2
 * @serial 2009-08-09
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 * org.exist.xquery.FunctionSignature)
 */
public class DirectoryList extends BasicFunction {

    static final String NAMESPACE_URI = FileModule.NAMESPACE_URI;
    static final String PREFIX = FileModule.PREFIX;
    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("directory-list", NAMESPACE_URI, PREFIX),
                    "List all files, including their file size and modification time, "
                            + "found in or below a directory, $directory. Files are located in the server's "
                            + "file system, using filename patterns, $pattern.  File pattern matching is based "
                            + "on code from Apache's Ant, thus following the same conventions. For example:\n\n"
                            + "'*.xml' matches any file ending with .xml in the current directory,\n- '**/*.xml' matches files "
                            + "in any directory below the specified directory.  This method is only available to the DBA role.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("path", Type.ITEM,
                                    Cardinality.EXACTLY_ONE, "The base directory path or URI in the file system where the files are located."),
                            new FunctionParameterSequenceType("pattern", Type.STRING,
                                    Cardinality.ZERO_OR_MORE, "The file name pattern")
                    },
                    new FunctionReturnSequenceType(Type.NODE,
                            Cardinality.ZERO_OR_ONE, "a node fragment that shows all matching "
                            + "filenames, including their file size and modification time, and "
                            + "the subdirectory they were found in")
            )
    };
    static final QName FILE_ELEMENT = new QName("file", NAMESPACE_URI, PREFIX);
    static final QName LIST_ELEMENT = new QName("list", NAMESPACE_URI, PREFIX);

    static final QName DIRECTORY_ATTRIBUTE = new QName("directory", null, null);
    static final QName NAME_ATTRIBUTE = new QName("name", null, null);
    static final QName SIZE_ATTRIBUTE = new QName("size", null, null);
    static final QName HUMAN_SIZE_ATTRIBUTE = new QName("human-size", null, null);
    static final QName MODIFIED_ATTRIBUTE = new QName("modified", null, null);
    static final QName SUBDIR_ATTRIBUTE = new QName("subdir", null, null);
    private static final Logger logger = LogManager.getLogger(DirectoryList.class);

    public DirectoryList(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (!context.getSubject().hasDbaRole()) {
            XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
            logger.error("Invalid user", xPathException);
            throw xPathException;
        }

        final String inputPath = args[0].getStringValue();
        final Path baseDir = FileModuleHelper.getFile(inputPath, this);

        final Sequence patterns = args[1];

        if (logger.isDebugEnabled()) {
            logger.debug("Listing matching files in directory: {}", baseDir);
        }

        context.pushDocumentContext();
        final MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement(LIST_ELEMENT, null);
        builder.addAttribute(DIRECTORY_ATTRIBUTE, baseDir.toString());
        try {
            final int patternsLen = patterns.getItemCount();
            final String[] includes = new String[patternsLen];
            for (int i = 0; i < patternsLen; i++) {
                includes[i] = patterns.itemAt(0).getStringValue();
            }

            final DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setIncludes(includes);
            directoryScanner.setBasedir(baseDir.toFile());
            directoryScanner.setCaseSensitive(true);
            directoryScanner.scan();

            for (final String includedFile : directoryScanner.getIncludedFiles()) {
                final Path file = baseDir.resolve(includedFile);

                if (logger.isDebugEnabled()) {
                    logger.debug("Found: {}", file.toAbsolutePath());
                }

                final String relPath = file.toString().substring(baseDir.toString().length() + 1);

                builder.startElement(FILE_ELEMENT, null);
                builder.addAttribute(NAME_ATTRIBUTE, FileUtils.fileName(file));

                final long sizeLong = FileUtils.sizeQuietly(file);
                builder.addAttribute(SIZE_ATTRIBUTE, Long.toString(sizeLong));
                builder.addAttribute(HUMAN_SIZE_ATTRIBUTE, getHumanSize(sizeLong));

                builder.addAttribute(MODIFIED_ATTRIBUTE,
                        new DateTimeValue(this,
                                new Date(Files.getLastModifiedTime(file).toMillis())).getStringValue());

                final int lastSeparatorPosition = relPath.lastIndexOf(java.io.File.separatorChar);
                if (lastSeparatorPosition >= 0) {
                    final String relDir = relPath.substring(0, lastSeparatorPosition);
                    if (!relDir.isEmpty()) {
                        builder.addAttribute(SUBDIR_ATTRIBUTE,
                                relDir.replace(java.io.File.separatorChar, '/'));
                    }
                }

                builder.endElement();
            }

            builder.endElement();

            return (NodeValue) builder.getDocument().getDocumentElement();
        } catch (final IOException | IllegalStateException e) {
            throw new XPathException(this, DIRECTORY_NOT_FOUND, e.getMessage());
        } finally {
            context.popDocumentContext();
        }
    }

    private String getHumanSize(final Long sizeLong) {
        if (sizeLong < 1024) {
            return Math.abs(sizeLong) + "B";
        }
        if (sizeLong < 1048576) {
            return Math.abs(sizeLong / 1024) + "KB";
        }
        if (sizeLong < 1073741824) {
            return Math.abs((sizeLong / (1024 * 1024))) + "MB";
        }
        return Math.abs((sizeLong / (1024 * 1024 * 1024))) + "GB";
    }

}
