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

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.util.SerializerUtils;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

/**
 * EXPath File Module 4.0 - Write functions.
 * <p>
 * Implements: file:write, file:write-text, file:write-text-lines, file:write-binary
 */
public class FileWrite extends BasicFunction {

    private static final FunctionParameterSequenceType FILE_PARAM =
            new FunctionParameterSequenceType("file", Type.STRING, Cardinality.EXACTLY_ONE, "The path to the file.");

    public static final FunctionSignature[] signatures = {
            // file:write($file, $value)
            new FunctionSignature(
                    new QName("write", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Writes a serialized sequence to a file. Creates the file if it does not exist, overwrites it otherwise.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The items to serialize and write.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:write($file, $value, $options)
            new FunctionSignature(
                    new QName("write", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Writes a serialized sequence to a file with serialization options.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The items to serialize and write."),
                            new FunctionParameterSequenceType("options", Type.ITEM, Cardinality.ZERO_OR_ONE, "Serialization parameters as map(*) or element(output:serialization-parameters).")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:write-text($file, $value)
            new FunctionSignature(
                    new QName("write-text", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Writes a string to a file. Creates the file if it does not exist, overwrites it otherwise.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The string to write.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:write-text($file, $value, $encoding)
            new FunctionSignature(
                    new QName("write-text", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Writes a string to a file with the specified encoding.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The string to write."),
                            new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.ZERO_OR_ONE, "The character encoding. Default: UTF-8.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:write-text-lines($file, $values)
            new FunctionSignature(
                    new QName("write-text-lines", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Writes a sequence of strings as lines to a file, separated by the platform line separator.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("values", Type.STRING, Cardinality.ZERO_OR_MORE, "The lines to write.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:write-text-lines($file, $values, $encoding)
            new FunctionSignature(
                    new QName("write-text-lines", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Writes a sequence of strings as lines to a file with the specified encoding.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("values", Type.STRING, Cardinality.ZERO_OR_MORE, "The lines to write."),
                            new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.ZERO_OR_ONE, "The character encoding. Default: UTF-8.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:write-binary($file, $value)
            new FunctionSignature(
                    new QName("write-binary", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Writes binary data to a file. Creates the file if it does not exist, overwrites it otherwise.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The binary data to write.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:write-binary($file, $value, $offset)
            new FunctionSignature(
                    new QName("write-binary", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Writes binary data to a file at the given offset.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The binary data to write."),
                            new FunctionParameterSequenceType("offset", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The byte offset at which to start writing. Default: 0.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            )
    };

    public FileWrite(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        ExpathFileModuleHelper.checkDbaRole(context, this);

        final String pathStr = args[0].getStringValue();
        final Path path = ExpathFileModuleHelper.getPath(pathStr, this);

        checkParentDir(path);

        if (Files.isDirectory(path)) {
            throw new XPathException(this, ExpathFileErrorCode.IS_DIR,
                    "Path is a directory: " + path.toAbsolutePath());
        }

        if (isCalledAs("write")) {
            return write(path, args);
        } else if (isCalledAs("write-text")) {
            return writeText(path, args);
        } else if (isCalledAs("write-text-lines")) {
            return writeTextLines(path, args);
        } else if (isCalledAs("write-binary")) {
            return writeBinary(path, args);
        }

        throw new XPathException(this, "Unknown function: " + getSignature().getName().getLocalPart());
    }

    private Sequence write(final Path path, final Sequence[] args) throws XPathException {
        final Sequence value = args[1];
        final Properties outputProperties = new Properties();
        if (args.length > 2 && !args[2].isEmpty()) {
            final Item optionsItem = args[2].itemAt(0);
            if (optionsItem instanceof AbstractMapType) {
                outputProperties.putAll(SerializerUtils.getSerializationOptions(this, (AbstractMapType) optionsItem));
            } else if (optionsItem instanceof NodeValue) {
                SerializerUtils.getSerializationOptions(this, (NodeValue) optionsItem, outputProperties);
            }
        }

        final SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        try {
            final Serializer serializer = context.getBroker().borrowSerializer();
            try (final Writer writer = new OutputStreamWriter(
                    new BufferedOutputStream(Files.newOutputStream(path)), StandardCharsets.UTF_8)) {
                sax.setOutput(writer, outputProperties);
                serializer.setProperties(outputProperties);
                serializer.setSAXHandlers(sax, sax);

                for (final SequenceIterator i = value.iterate(); i.hasNext(); ) {
                    final Item item = i.nextItem();
                    if (Type.subTypeOf(item.getType(), Type.NODE)) {
                        serializer.toSAX((NodeValue) item);
                    } else {
                        writer.write(item.getStringValue());
                    }
                }
            } finally {
                context.getBroker().returnSerializer(serializer);
            }
        } catch (final IOException | SAXException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        } finally {
            SerializerPool.getInstance().returnObject(sax);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence writeText(final Path path, final Sequence[] args) throws XPathException {
        final String text = args[1].getStringValue();
        final Charset encoding = getEncoding(args, 2);
        try {
            Files.writeString(path, text, encoding);
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence writeTextLines(final Path path, final Sequence[] args) throws XPathException {
        final Charset encoding = getEncoding(args, 2);
        try (final Writer writer = new OutputStreamWriter(
                new BufferedOutputStream(Files.newOutputStream(path)), encoding)) {
            final String lineSep = System.lineSeparator();
            for (final SequenceIterator i = args[1].iterate(); i.hasNext(); ) {
                writer.write(i.nextItem().getStringValue());
                writer.write(lineSep);
            }
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence writeBinary(final Path path, final Sequence[] args) throws XPathException {
        final BinaryValue binaryValue = (BinaryValue) args[1].itemAt(0);
        final long offset = args.length > 2 && !args[2].isEmpty() ? args[2].itemAt(0).toJavaObject(Long.class) : 0;

        try {
            if (offset == 0) {
                try (final OutputStream os = Files.newOutputStream(path);
                     final InputStream is = binaryValue.getInputStream()) {
                    is.transferTo(os);
                }
            } else {
                if (offset < 0) {
                    throw new XPathException(this, ExpathFileErrorCode.OUT_OF_RANGE,
                            "Offset must not be negative: " + offset);
                }
                try (final RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw");
                     final InputStream is = binaryValue.getInputStream()) {
                    raf.seek(offset);
                    is.transferTo(new OutputStream() {
                        @Override
                        public void write(int b) throws IOException {
                            raf.write(b);
                        }
                        @Override
                        public void write(byte[] b, int off, int len) throws IOException {
                            raf.write(b, off, len);
                        }
                    });
                }
            }
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private void checkParentDir(final Path path) throws XPathException {
        final Path parent = path.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            throw new XPathException(this, ExpathFileErrorCode.NO_DIR,
                    "Parent directory does not exist: " + parent.toAbsolutePath());
        }
    }

    private Charset getEncoding(final Sequence[] args, final int index) throws XPathException {
        if (args.length > index && !args[index].isEmpty()) {
            return ExpathFileModuleHelper.getCharset(args[index].getStringValue(), this);
        }
        return StandardCharsets.UTF_8;
    }
}
