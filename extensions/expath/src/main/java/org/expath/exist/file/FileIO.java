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

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * EXPath File Module 4.0 - Input functions.
 * <p>
 * Implements: file:read-text, file:read-text-lines, file:read-binary
 */
public class FileIO extends BasicFunction {

    private static final FunctionParameterSequenceType FILE_PARAM =
            new FunctionParameterSequenceType("file", Type.STRING, Cardinality.EXACTLY_ONE,
                    "The path to the file.");
    private static final FunctionParameterSequenceType ENCODING_PARAM =
            new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.ZERO_OR_ONE,
                    "The character encoding. Default: UTF-8.");

    public static final FunctionSignature[] signatures = {
            // file:read-text($file as xs:string) as xs:string
            new FunctionSignature(
                    new QName("read-text", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Reads the contents of a file as text.",
                    new SequenceType[]{FILE_PARAM},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the file contents as string.")
            ),
            // file:read-text($file as xs:string, $encoding as xs:string) as xs:string
            new FunctionSignature(
                    new QName("read-text", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Reads the contents of a file as text with the specified encoding.",
                    new SequenceType[]{FILE_PARAM, ENCODING_PARAM},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the file contents as string.")
            ),
            // file:read-text-lines($file as xs:string) as xs:string*
            new FunctionSignature(
                    new QName("read-text-lines", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Reads the contents of a file as a sequence of lines.",
                    new SequenceType[]{FILE_PARAM},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the lines of the file.")
            ),
            // file:read-text-lines($file as xs:string, $encoding as xs:string) as xs:string*
            new FunctionSignature(
                    new QName("read-text-lines", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Reads the contents of a file as a sequence of lines with the specified encoding.",
                    new SequenceType[]{FILE_PARAM, ENCODING_PARAM},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the lines of the file.")
            ),
            // file:read-binary($file as xs:string) as xs:base64Binary
            new FunctionSignature(
                    new QName("read-binary", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Reads the contents of a file as binary.",
                    new SequenceType[]{FILE_PARAM},
                    new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "the binary contents.")
            ),
            // file:read-binary($file as xs:string, $offset as xs:integer) as xs:base64Binary
            new FunctionSignature(
                    new QName("read-binary", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Reads a portion of a file as binary starting at the given byte offset.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("offset", Type.INTEGER, Cardinality.ZERO_OR_ONE,
                                    "The byte offset to start reading from. Default: 0.")
                    },
                    new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "the binary contents.")
            ),
            // file:read-binary($file as xs:string, $offset as xs:integer, $length as xs:integer) as xs:base64Binary
            new FunctionSignature(
                    new QName("read-binary", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Reads a portion of a file as binary with offset and length.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("offset", Type.INTEGER, Cardinality.ZERO_OR_ONE,
                                    "The byte offset to start reading from. Default: 0."),
                            new FunctionParameterSequenceType("length", Type.INTEGER, Cardinality.ZERO_OR_ONE,
                                    "The number of bytes to read.")
                    },
                    new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "the binary contents.")
            )
    };

    public FileIO(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        ExpathFileModuleHelper.checkDbaRole(context, this);

        final String pathStr = args[0].getStringValue();
        final Path path = ExpathFileModuleHelper.getPath(pathStr, this);

        if (!Files.exists(path)) {
            throw new XPathException(this, ExpathFileErrorCode.NOT_FOUND,
                    "File not found: " + path.toAbsolutePath());
        }
        if (Files.isDirectory(path)) {
            throw new XPathException(this, ExpathFileErrorCode.IS_DIR,
                    "Path is a directory: " + path.toAbsolutePath());
        }

        if (isCalledAs("read-text")) {
            return readText(path, args);
        } else if (isCalledAs("read-text-lines")) {
            return readTextLines(path, args);
        } else if (isCalledAs("read-binary")) {
            return readBinary(path, args);
        }

        throw new XPathException(this, "Unknown function: " + getSignature().getName().getLocalPart());
    }

    private Sequence readText(final Path path, final Sequence[] args) throws XPathException {
        final Charset encoding = getEncoding(args, 1);
        try {
            final String content = Files.readString(path, encoding);
            // Normalize newlines per spec: CR or CRLF -> LF
            final String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
            return new StringValue(this, normalized);
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
    }

    private Sequence readTextLines(final Path path, final Sequence[] args) throws XPathException {
        final Charset encoding = getEncoding(args, 1);
        try {
            final String content = Files.readString(path, encoding);
            // Split at newline boundaries per spec
            final String[] lines = content.split("\r\n|\r|\n", -1);
            final ValueSequence result = new ValueSequence(lines.length);
            // If file ends with newline, last split element is empty - exclude it per spec
            final int count = (lines.length > 0 && lines[lines.length - 1].isEmpty()) ? lines.length - 1 : lines.length;
            for (int i = 0; i < count; i++) {
                result.add(new StringValue(this, lines[i]));
            }
            return result;
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
    }

    private Sequence readBinary(final Path path, final Sequence[] args) throws XPathException {
        final long offset = args.length > 1 && !args[1].isEmpty() ? args[1].itemAt(0).toJavaObject(Long.class) : 0;
        final long length = args.length > 2 && !args[2].isEmpty() ? args[2].itemAt(0).toJavaObject(Long.class) : -1;

        try {
            final long fileSize = Files.size(path);
            if (offset < 0 || offset > fileSize) {
                throw new XPathException(this, ExpathFileErrorCode.OUT_OF_RANGE,
                        "Offset " + offset + " is out of range for file of size " + fileSize);
            }
            if (length < -1) {
                throw new XPathException(this, ExpathFileErrorCode.OUT_OF_RANGE,
                        "Length must not be negative: " + length);
            }
            if (length >= 0 && offset + length > fileSize) {
                throw new XPathException(this, ExpathFileErrorCode.OUT_OF_RANGE,
                        "Offset + length exceeds file size: " + (offset + length) + " > " + fileSize);
            }

            if (offset == 0 && length < 0) {
                // Read entire file
                final InputStream is = Files.newInputStream(path);
                return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), is, this);
            }

            // Partial read
            try (final RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                raf.seek(offset);
                final int readLen = length >= 0 ? (int) length : (int) (fileSize - offset);
                final byte[] data = new byte[readLen];
                raf.readFully(data);
                final InputStream bis = new java.io.ByteArrayInputStream(data);
                return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), bis, this);
            }
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
    }

    private Charset getEncoding(final Sequence[] args, final int index) throws XPathException {
        if (args.length > index && !args[index].isEmpty()) {
            return ExpathFileModuleHelper.getCharset(args[index].getStringValue(), this);
        }
        return StandardCharsets.UTF_8;
    }
}
