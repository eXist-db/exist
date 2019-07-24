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
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.FileUtils;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist File Module Extension DirectoryList
 * 
 * Enumerate a list of files and directories, including their size and modification time, found in
 * a specified directory
 *
 * @author <a href="mailto:dannes@exist-db.org">Dannes Wessels</a>
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @author <a href="mailto:ljo@exist-db.org">Leif-Jöran Olsson</a>
 * @serial 2010-05-12
 * @version 1.2
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class Directory extends BasicFunction {

    private final static Logger logger = LogManager.getLogger(Directory.class);

    final static String NAMESPACE_URI = FileModule.NAMESPACE_URI;
    final static String PREFIX = FileModule.PREFIX;
    
    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("list", NAMESPACE_URI, PREFIX),
            "List all files and directories under the specified directory. "
                + "This method is only available to the DBA role.",
            new SequenceType[]{
                new FunctionParameterSequenceType("path", 
                        Type.ITEM, Cardinality.EXACTLY_ONE, 
                        "The directory path or URI in the file system."),
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, 
                "a node describing file and directory names and meta data."))
    };

    public Directory(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        
        if (!context.getSubject().hasDbaRole()) {
            XPathException xPathException = new XPathException(this, "Permission denied, calling user '"
                    + context.getSubject().getName() + "' must be a DBA to call this function.");
            logger.error("Invalid user", xPathException);
            throw xPathException;
        }

        
        final String inputPath = args[0].getStringValue();
        final Path directoryPath = FileModuleHelper.getFile(inputPath);
		
        if (logger.isDebugEnabled()) {
            logger.debug("Listing matching files in directory: " + directoryPath.toAbsolutePath().toString());
        }

        if(!Files.isDirectory(directoryPath)) {
            throw new XPathException(this, "'" + inputPath + "' does not point to a valid directory.");
        }
        
        // Get list of files, null if baseDir does not point to a directory
        try(final Stream<Path> scannedFiles = Files.list(directoryPath)) {

            final MemTreeBuilder builder = context.getDocumentBuilder();

            builder.startDocument();
            builder.startElement(new QName("list", null, null), null);

            scannedFiles.forEach(entry -> {
                if (logger.isDebugEnabled()) {
                    logger.debug("Found: " + entry.toAbsolutePath().toString());
                }

                String entryType = "unknown";
                if (Files.isRegularFile(entry)) {
                    entryType = "file";
                } else if (Files.isDirectory(entry)) {
                    entryType = "directory";
                }

                builder.startElement(new QName(entryType, NAMESPACE_URI, PREFIX), null);

                builder.addAttribute(new QName("name", null, null), FileUtils.fileName(entry));

                try {
                    if (Files.isRegularFile(entry)) {
                        final Long sizeLong = Files.size(entry);
                        String sizeString = Long.toString(sizeLong);
                        String humanSize = getHumanSize(sizeLong, sizeString);

                        builder.addAttribute(new QName("size", null, null), sizeString);
                        builder.addAttribute(new QName("human-size", null, null), humanSize);
                    }

                    builder.addAttribute(new QName("modified", null, null),
                            new DateTimeValue(new Date(Files.getLastModifiedTime(entry).toMillis())).getStringValue());

                    builder.addAttribute(new QName("hidden", null, null),
                            new BooleanValue(Files.isHidden(entry)).getStringValue());

                    builder.addAttribute(new QName("canRead", null, null),
                            new BooleanValue(Files.isReadable(entry)).getStringValue());

                    builder.addAttribute(new QName("canWrite", null, null),
                            new BooleanValue(Files.isWritable(entry)).getStringValue());
                } catch (final IOException | XPathException ioe) {
                    LOG.warn(ioe);
                }
                builder.endElement();

            });

            builder.endElement();

            return (NodeValue) builder.getDocument().getDocumentElement();
        } catch(final IOException ioe) {
            throw new XPathException(this, ioe);
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
