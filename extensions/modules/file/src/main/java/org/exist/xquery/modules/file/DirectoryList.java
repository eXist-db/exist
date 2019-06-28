/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.modules.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.DirectoryScanner;
import org.exist.util.FileUtils;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist File Module Extension DirectoryList
 *
 * Enumerate a list of files, including their size and modification time, found
 * in a specified directory, using a pattern
 *
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author ljo
 * @serial 2009-08-09
 * @version 1.2
 *
 * @see
 * org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 * org.exist.xquery.FunctionSignature)
 */
public class DirectoryList extends BasicFunction {

    private final static Logger logger = LogManager.getLogger(DirectoryList.class);

    final static String NAMESPACE_URI = FileModule.NAMESPACE_URI;
    final static String PREFIX = FileModule.PREFIX;

    public final static FunctionSignature[] signatures
            = {
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
                                    Cardinality.EXACTLY_ONE, "The file name pattern")
                        },
                        new FunctionReturnSequenceType(Type.NODE,
                                Cardinality.ZERO_OR_ONE, "a node fragment that shows all matching "
                                + "filenames, including their file size and modification time, and "
                                + "the subdirectory they were found in")
                )
            };

    /**
     * DirectoryList Constructor
     *
     * @param context	The Context of the calling XQuery
     */
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
        final Path baseDir = FileModuleHelper.getFile(inputPath);

        final Sequence patterns = args[1];

        if (logger.isDebugEnabled()) {
            logger.debug("Listing matching files in directory: " + baseDir);
        }

        final MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement(new QName("list", NAMESPACE_URI, PREFIX), null);
        builder.addAttribute(new QName("directory", null, null), baseDir.toString());
        try {
            for (final SequenceIterator i = patterns.iterate(); i.hasNext(); ) {
                final String pattern = i.nextItem().getStringValue();
                final List<Path> scannedFiles = DirectoryScanner.scanDir(baseDir, pattern);

                if (logger.isDebugEnabled()) {
                    logger.debug("Found: " + scannedFiles.size());
                }

                for (final Path file : scannedFiles) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Found: " + file.toAbsolutePath());
                    }

                    String relPath = file.toString().substring(baseDir.toString().length() + 1);

                    int lastSeparatorPosition = relPath.lastIndexOf(java.io.File.separatorChar);

                    String relDir = null;
                    if (lastSeparatorPosition >= 0) {
                        relDir = relPath.substring(0, lastSeparatorPosition);
                        relDir = relDir.replace(java.io.File.separatorChar, '/');
                    }

                    builder.startElement(new QName("file", NAMESPACE_URI, PREFIX), null);

                    builder.addAttribute(new QName("name", null, null), FileUtils.fileName(file));

                    Long sizeLong = FileUtils.sizeQuietly(file);
                    String sizeString = Long.toString(sizeLong);
                    String humanSize = getHumanSize(sizeLong, sizeString);

                    builder.addAttribute(new QName("size", null, null), sizeString);
                    builder.addAttribute(new QName("human-size", null, null), humanSize);
                    builder.addAttribute(new QName("modified", null, null), new DateTimeValue(new Date(Files.getLastModifiedTime(file).toMillis())).getStringValue());

                    if (relDir != null && relDir.length() > 0) {
                        builder.addAttribute(new QName("subdir", null, null), relDir);
                    }

                    builder.endElement();

                }
            }

            builder.endElement();

            return (NodeValue) builder.getDocument().getDocumentElement();
        } catch (final IOException e) {
            throw new XPathException(this, e.getMessage());
        }
    }

    private String getHumanSize(final Long sizeLong, final String sizeString) {
        String humanSize = "n/a";
        int sizeDigits = sizeString.length();

        if (sizeDigits < 4) {
            humanSize = Long.toString(Math.abs(sizeLong));

        } else if (sizeDigits >= 4 && sizeDigits <= 6) {
            if (sizeLong < 1024) {
                // We don't want 0KB för e.g. 1006 Bytes.
                humanSize = Long.toString(Math.abs(sizeLong));
            } else {
                humanSize = Math.abs(sizeLong / 1024) + "KB";
            }

        } else if (sizeDigits >= 7 && sizeDigits <= 9) {
            if (sizeLong < 1048576) {
                humanSize = Math.abs(sizeLong / 1024) + "KB";
            } else {
                humanSize = Math.abs(sizeLong / (1024 * 1024)) + "MB";
            }

        } else if (sizeDigits > 9) {
            if (sizeLong < 1073741824) {
                humanSize = Math.abs((sizeLong / (1024 * 1024))) + "MB";
            } else {
                humanSize = Math.abs((sizeLong / (1024 * 1024 * 1024))) + "GB";
            }
        }
        return humanSize;
    }

}
