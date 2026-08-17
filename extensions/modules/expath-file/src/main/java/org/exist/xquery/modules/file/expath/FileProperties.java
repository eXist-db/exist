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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.stream.Stream;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * EXPath File Module 4.0 - File Properties functions.
 * <p>
 * Implements: file:exists, file:is-dir, file:is-file, file:is-absolute, file:last-modified, file:size
 */
public class FileProperties extends BasicFunction {

    private static final FunctionParameterSequenceType PATH_PARAM =
            new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE,
                    "The file path.");

    public static final FunctionSignature[] signatures = {
            // file:exists($path as xs:string) as xs:boolean
            new FunctionSignature(
                    new QName("exists", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Tests whether a path exists.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                            "true if the path exists.")
            ),
            // file:is-dir($path as xs:string) as xs:boolean
            new FunctionSignature(
                    new QName("is-dir", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Tests whether a path points to a directory.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                            "true if the path is a directory.")
            ),
            // file:is-file($path as xs:string) as xs:boolean
            new FunctionSignature(
                    new QName("is-file", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Tests whether a path points to a regular file.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                            "true if the path is a regular file.")
            ),
            // file:is-absolute($path as xs:string) as xs:boolean
            new FunctionSignature(
                    new QName("is-absolute", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Tests whether a path is absolute.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                            "true if the path is absolute.")
            ),
            // file:last-modified($path as xs:string) as xs:dateTime
            new FunctionSignature(
                    new QName("last-modified", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the last modification time of a file or directory.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE,
                            "the last modification time.")
            ),
            // file:size($path as xs:string) as xs:integer
            new FunctionSignature(
                    new QName("size", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the byte size of a file, or 0 for a directory.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE,
                            "the file size in bytes.")
            ),
            // file:size($path as xs:string, $recursive as xs:boolean) as xs:integer
            new FunctionSignature(
                    new QName("size", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the byte size of a file, or for a directory the recursive size if $recursive is true.",
                    new SequenceType[]{
                            PATH_PARAM,
                            new FunctionParameterSequenceType("recursive", Type.BOOLEAN, Cardinality.ZERO_OR_ONE,
                                    "If true and path is a directory, compute recursive size.")
                    },
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE,
                            "the file or directory size in bytes.")
            )
    };

    public FileProperties(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        ExpathFileModuleHelper.checkDbaRole(context, this);

        final String pathStr = args[0].getStringValue();
        final Path path = ExpathFileModuleHelper.getPath(pathStr, this, context);

        if (isCalledAs("exists")) {
            return BooleanValue.valueOf(Files.exists(path));
        } else if (isCalledAs("is-dir")) {
            return BooleanValue.valueOf(Files.isDirectory(path));
        } else if (isCalledAs("is-file")) {
            return BooleanValue.valueOf(Files.isRegularFile(path));
        } else if (isCalledAs("is-absolute")) {
            return BooleanValue.valueOf(path.isAbsolute());
        } else if (isCalledAs("last-modified")) {
            if (!Files.exists(path)) {
                throw new XPathException(this, ExpathFileErrorCode.NOT_FOUND,
                        "Path does not exist: " + path.toAbsolutePath());
            }
            try {
                final FileTime ft = Files.getLastModifiedTime(path);
                return new DateTimeValue(this, new Date(ft.toMillis()));
            } catch (final IOException e) {
                throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
            }
        } else if (isCalledAs("size")) {
            if (!Files.exists(path)) {
                throw new XPathException(this, ExpathFileErrorCode.NOT_FOUND,
                        "Path does not exist: " + path.toAbsolutePath());
            }
            try {
                if (Files.isDirectory(path)) {
                    final boolean recursive = args.length > 1 && !args[1].isEmpty()
                            && args[1].itemAt(0).toJavaObject(Boolean.class);
                    if (recursive) {
                        try (final Stream<Path> walk = Files.walk(path)) {
                            final long total = walk
                                    .filter(Files::isRegularFile)
                                    .mapToLong(p -> {
                                        try {
                                            return Files.size(p);
                                        } catch (final IOException e) {
                                            return 0L;
                                        }
                                    })
                                    .sum();
                            return new IntegerValue(this, total);
                        }
                    }
                    return new IntegerValue(this, 0);
                }
                return new IntegerValue(this, Files.size(path));
            } catch (final IOException e) {
                throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
            }
        }

        throw new XPathException(this, "Unknown function: " + getSignature().getName().getLocalPart());
    }
}
