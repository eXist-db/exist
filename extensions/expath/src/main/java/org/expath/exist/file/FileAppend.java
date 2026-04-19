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
import java.nio.file.StandardOpenOption;
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
 * EXPath File Module 4.0 - Append functions.
 * <p>
 * Implements: file:append, file:append-binary, file:append-text, file:append-text-lines
 */
public class FileAppend extends BasicFunction {

    private static final FunctionParameterSequenceType FILE_PARAM =
            new FunctionParameterSequenceType("file", Type.STRING, Cardinality.EXACTLY_ONE, "The path to the file.");

    public static final FunctionSignature[] signatures = {
            // file:append($file, $value)
            new FunctionSignature(
                    new QName("append", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Appends a serialized sequence to a file. Creates the file if it does not exist.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The items to serialize and append.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:append($file, $value, $options)
            new FunctionSignature(
                    new QName("append", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Appends a serialized sequence to a file with serialization options.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_MORE, "The items to serialize and append."),
                            new FunctionParameterSequenceType("options", Type.ITEM, Cardinality.ZERO_OR_ONE, "Serialization parameters as map(*) or element(output:serialization-parameters).")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:append-binary($file, $value)
            new FunctionSignature(
                    new QName("append-binary", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Appends binary data to a file. Creates the file if it does not exist.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The binary data to append.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:append-text($file, $value)
            new FunctionSignature(
                    new QName("append-text", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Appends a string to a file. Creates the file if it does not exist.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The string to append.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:append-text($file, $value, $encoding)
            new FunctionSignature(
                    new QName("append-text", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Appends a string to a file with the specified encoding.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The string to append."),
                            new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.ZERO_OR_ONE, "The character encoding. Default: UTF-8.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:append-text-lines($file, $lines)
            new FunctionSignature(
                    new QName("append-text-lines", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Appends a sequence of strings as lines to a file, separated by the platform line separator.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("lines", Type.STRING, Cardinality.ZERO_OR_MORE, "The lines to append.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            ),
            // file:append-text-lines($file, $lines, $encoding)
            new FunctionSignature(
                    new QName("append-text-lines", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Appends a sequence of strings as lines to a file with the specified encoding.",
                    new SequenceType[]{
                            FILE_PARAM,
                            new FunctionParameterSequenceType("lines", Type.STRING, Cardinality.ZERO_OR_MORE, "The lines to append."),
                            new FunctionParameterSequenceType("encoding", Type.STRING, Cardinality.ZERO_OR_ONE, "The character encoding. Default: UTF-8.")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE, "empty sequence.")
            )
    };

    public FileAppend(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        ExpathFileModuleHelper.checkDbaRole(context, this);

        final String pathStr = args[0].getStringValue();
        final Path path = ExpathFileModuleHelper.getPath(pathStr, this, context);

        checkParentDir(path);

        if (Files.isDirectory(path)) {
            throw new XPathException(this, ExpathFileErrorCode.IS_DIR,
                    "Path is a directory: " + path.toAbsolutePath());
        }

        if (isCalledAs("append")) {
            return append(path, args);
        } else if (isCalledAs("append-binary")) {
            return appendBinary(path, args);
        } else if (isCalledAs("append-text")) {
            return appendText(path, args);
        } else if (isCalledAs("append-text-lines")) {
            return appendTextLines(path, args);
        }

        throw new XPathException(this, "Unknown function: " + getSignature().getName().getLocalPart());
    }

    private Sequence append(final Path path, final Sequence[] args) throws XPathException {
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
                    new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)),
                    StandardCharsets.UTF_8)) {
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

    private Sequence appendBinary(final Path path, final Sequence[] args) throws XPathException {
        final BinaryValue binaryValue = (BinaryValue) args[1].itemAt(0);
        try (final OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
             final InputStream is = binaryValue.getInputStream()) {
            is.transferTo(os);
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence appendText(final Path path, final Sequence[] args) throws XPathException {
        final String text = args[1].getStringValue();
        final Charset encoding = getEncoding(args, 2);
        try (final Writer writer = new OutputStreamWriter(
                Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND), encoding)) {
            writer.write(text);
        } catch (final IOException e) {
            throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence appendTextLines(final Path path, final Sequence[] args) throws XPathException {
        final Charset encoding = getEncoding(args, 2);
        try (final Writer writer = new OutputStreamWriter(
                Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND), encoding)) {
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
