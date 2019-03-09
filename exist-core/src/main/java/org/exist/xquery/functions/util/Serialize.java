/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.fn.FunSerialize;
import org.exist.xquery.util.SerializerUtils;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Serialize extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(Serialize.class);

    private static final FunctionParameterSequenceType paramParameters = new FunctionParameterSequenceType(
            "parameters",
            Type.ITEM,
            Cardinality.ZERO_OR_MORE,
            "The serialization parameters: either a sequence of key=value pairs or an " +
                    "output:serialization-parameters element as defined by the standard " +
                    "fn:serialize function.");

    private static final FunctionParameterSequenceType paramNodeSet = new FunctionParameterSequenceType(
            "node-set",
            Type.NODE,
            Cardinality.ZERO_OR_MORE,
            "The node set to serialize");

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("serialize", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Writes the node set passed in parameter $node-set into a file on the file system. The " +
                            "full path to the file is specified in parameter $file. $parameters contains a " +
                            "sequence of zero or more serialization parameters specified as key=value pairs. The " +
                            "serialization options are the same as those recognized by \"declare option exist:serialize\". " +
                            "The function does NOT automatically inherit the serialization options of the XQuery it is " +
                            "called from. False is returned if the " +
                            "specified file can not be created or is not writable, true on success. The empty " +
                            "sequence is returned if the argument sequence is empty.",
                    new SequenceType[]{
                            paramNodeSet,
                            new FunctionParameterSequenceType("file", Type.STRING, Cardinality.EXACTLY_ONE, "The output file path"),
                            paramParameters
                    },
                    new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE),
                    "Use the file:serialize() function in the file extension module instead!"
            ),
            new FunctionSignature(
                    new QName("serialize", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Returns the Serialized node set passed in parameter $node-set. $parameters contains a " +
                            "sequence of zero or more serialization parameters specified as key=value pairs. The " +
                            "serialization options are the same as those recognized by \"declare option exist:serialize\". " +
                            "The function does NOT automatically inherit the serialization options of the XQuery it is " +
                            "called from.",
                    new SequenceType[]{
                            paramNodeSet,
                            paramParameters
                    },
                    new FunctionParameterSequenceType("result", Type.STRING, Cardinality.ZERO_OR_ONE, "the string containing the serialized node set."),
                    FunSerialize.signatures[1]
            )
    };


    public Serialize(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }


        Properties outputProperties = null;

        if (args.length == 3) {
            // TODO: Remove this conditional in eXist 2.0 since the function has been deprecated.
            /** serialize to disk **/

            // check the file output path
            final String path = args[1].itemAt(0).getStringValue();
            final Path file = Paths.get(path).normalize();
            if (Files.isDirectory(file)) {
                logger.debug("Output file is a directory: " + file.toAbsolutePath().toString());
                return BooleanValue.FALSE;
            }
            if (Files.exists(file) && !Files.isWritable(file)) {
                logger.debug("Cannot write to file " + file.toAbsolutePath().toString());
                return BooleanValue.FALSE;
            }

            //parse serialization options from third argument to function
            outputProperties = parseSerializationOptions(args[2]);

            //setup output stream for file
            try(final OutputStream os = Files.newOutputStream(file)) {
                //do the serialization
                serialize(args[0].iterate(), outputProperties, os);
            } catch (final IOException e) {
                throw new XPathException(this, "A problem occurred while serializing the node set: " + e.getMessage(), e);
            }

            return BooleanValue.TRUE;
        } else {
            /** serialize to string **/

            //parse serialization options from second argument to function
            outputProperties = parseSerializationOptions(args[1]);

            try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream()) {
                //do the serialization
                serialize(args[0].iterate(), outputProperties, os);


                final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, UTF_8.name());
                return new StringValue(os.toString(encoding));
            } catch (final IOException  e) {
                throw new XPathException(this, "A problem occurred while serializing the node set: " + e.getMessage(), e);
            }
        }

    }

    private Properties parseSerializationOptions(Sequence sSerializeParams) throws XPathException {
        //parse serialization options
        final Properties outputProperties = new Properties();
        if (sSerializeParams.hasOne() && Type.subTypeOf(sSerializeParams.getItemType(), Type.NODE)) {
            SerializerUtils.getSerializationOptions(this, (NodeValue) sSerializeParams.itemAt(0), outputProperties);

        } else {
            outputProperties.setProperty(OutputKeys.INDENT, "yes");
            outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            final SequenceIterator siSerializeParams = sSerializeParams.iterate();
            while (siSerializeParams.hasNext()) {
                final String serializeParam = siSerializeParams.nextItem().getStringValue();
                for (final Tuple2<String, String> option : parseOption(serializeParam)) {
                    outputProperties.setProperty(option._1, option._2);
                }
            }
        }
        return outputProperties;
    }

    private void serialize(SequenceIterator siNode, Properties outputProperties, OutputStream os) throws XPathException {
        // serialize the node set
        final SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        outputProperties.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
        try (final Writer writer = new OutputStreamWriter(os, encoding)) {
            sax.setOutput(writer, outputProperties);
            final Serializer serializer = context.getBroker().getSerializer();
            serializer.reset();
            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);

            sax.startDocument();

            while (siNode.hasNext()) {
                final NodeValue next = (NodeValue) siNode.nextItem();
                serializer.toSAX(next);
            }

            sax.endDocument();
            writer.close();
        } catch (final SAXException | IOException e) {
            throw new XPathException(this, "A problem occurred while serializing the node set: " + e.getMessage(), e);
        } finally {
            SerializerPool.getInstance().returnObject(sax);
        }
    }

    protected static List<Tuple2<String, String>> parseOption(final String options) {
        final List<Tuple2<String, String>> keyValues = new ArrayList<>();

        final String[] tokens = options.split(" ");
        for (int i = 0; i < tokens.length; i++) {
            final String token = tokens[i];
            final int sep = token.indexOf('=');
            if (sep > -1) {
                final String key = token.substring(0, sep);
                final String value = token.substring(sep + 1);
                keyValues.add(Tuple(key, value));
            } else {
                final Tuple2<String, String> prev = keyValues.remove(keyValues.size() - 1);
                keyValues.add(Tuple(prev._1, prev._2 + ' ' + token));
            }
        }

        return keyValues;
    }
}
