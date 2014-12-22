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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Option;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.SerializerUtils;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

public class SerializeToFile extends BasicFunction 
{
	private final static Logger logger = Logger.getLogger(SerializeToFile.class);

	private final static String FN_SERIALIZE_LN = "serialize";
    private final static String FN_SERIALIZE_BINARY_LN = "serialize-binary";

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( FN_SERIALIZE_LN, FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Writes the node set into a file on the file system. $parameters contains a " +
			"sequence of zero or more serialization parameters specified as key=value pairs. The " +
			"serialization options are the same as those recognized by \"declare option exist:serialize\". " +
			"The function does NOT automatically inherit the serialization options of the XQuery it is " +
			"called from.  This method is only available to the DBA role.",
			new SequenceType[] { 
				new FunctionParameterSequenceType( "node-set", Type.NODE, 
                        Cardinality.ZERO_OR_MORE, "The contents to write to the file system." ),
				new FunctionParameterSequenceType( "path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The full path or URI to the file" ),
				new FunctionParameterSequenceType( "parameters", Type.ITEM,
                        Cardinality.ZERO_OR_MORE, "The serialization parameters: either a sequence of key=value pairs or an output:serialization-parameters " +
                            "element as defined by the standard fn:serialize function." )
            },
			new FunctionReturnSequenceType( Type.BOOLEAN, 
                    Cardinality.ZERO_OR_ONE, "true on success - false if the specified file can not be "
                    + "created or is not writable.  The empty sequence is returned if the argument sequence is empty." )
                ),
        
		new FunctionSignature(
			new QName( FN_SERIALIZE_LN, FileModule.NAMESPACE_URI, FileModule.PREFIX ),
			"Writes the node set into a file on the file system, optionally appending to it. " +
			"$parameters contains a sequence of zero or more serialization parameters specified as " +
			"key=value pairs. The serialization options are the same as those recognized by " +
			"\"declare option exist:serialize\". " +
			"The function does NOT automatically inherit the serialization options of the XQuery it is " +
			"called from.  This method is only available to the DBA role.",
			new SequenceType[] { 
				new FunctionParameterSequenceType( "node-set", Type.NODE, 
                        Cardinality.ZERO_OR_MORE, "The contents to write to the file system." ),
				new FunctionParameterSequenceType( "path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The full path or URI to the file" ),
				new FunctionParameterSequenceType( "parameters", Type.ITEM,
                        Cardinality.ZERO_OR_MORE, "The serialization parameters: either a sequence of key=value pairs or an output:serialization-parameters " +
                        "element as defined by the standard fn:serialize function." ),
				new FunctionParameterSequenceType( "append", Type.BOOLEAN, 
                        Cardinality.EXACTLY_ONE, "Should content be appended?")
                        },
			new FunctionReturnSequenceType( Type.BOOLEAN, 
                    Cardinality.ZERO_OR_ONE, "true on success - false if the specified file can "
                    + "not be created or is not writable.  The empty sequence is returned if the argument sequence is empty." )
                ),
        
        new FunctionSignature(
			new QName(FN_SERIALIZE_BINARY_LN, FileModule.NAMESPACE_URI, FileModule.PREFIX),
			"Writes binary data into a file on the file system.  This method is only available to the DBA role.",
			new SequenceType[]{
				new FunctionParameterSequenceType("binarydata", Type.BASE64_BINARY, 
                        Cardinality.EXACTLY_ONE, "The contents to write to the file system."),
				new FunctionParameterSequenceType("path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The full path or URI to the file")
                        },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true on success - false if the specified file can not be created or is not writable")
                ),
        
        new FunctionSignature(
			new QName(FN_SERIALIZE_BINARY_LN, FileModule.NAMESPACE_URI, FileModule.PREFIX),
			"Writes binary data into a file on the file system, optionally appending the content.  This method is only available to the DBA role.",
			new SequenceType[]{
				new FunctionParameterSequenceType("binarydata", Type.BASE64_BINARY, 
                        Cardinality.EXACTLY_ONE, "The contents to write to the file system."),
				new FunctionParameterSequenceType("path", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The full path or URI to the file"),
				new FunctionParameterSequenceType("append", Type.BOOLEAN, 
                        Cardinality.EXACTLY_ONE, "Should content be appended?")
                        },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, 
                    "true on success - false if the specified file can not be created or is not writable")
                )
        };
	
	
	public SerializeToFile(final XQueryContext context, final FunctionSignature signature) {
            super(context, signature);
	}
	
        @Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException
	{
	
            if(args[0].isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }

            if(!context.getSubject().hasDbaRole()) {
                final XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
                logger.error("Invalid user", xPathException);
                throw xPathException;
            }


            //check the file output path
            final String inputPath = args[1].getStringValue();
            final File file = FileModuleHelper.getFile(inputPath);

            if(file.isDirectory()) {
                logger.debug("Cannot serialize file. Output file is a directory: " + file.getAbsolutePath());
                return BooleanValue.FALSE;
            }

            if(file.exists() && !file.canWrite()) {
                logger.debug("Cannot serialize file. Cannot write to file " + file.getAbsolutePath() );
                return BooleanValue.FALSE;
            }

            if(isCalledAs(FN_SERIALIZE_LN)) {
                //parse serialization options from third argument to function
                final Properties outputProperties = parseXMLSerializationOptions( args[2] );
                final boolean doAppend = (args.length > 3) && "true".equals(args[3].itemAt(0).getStringValue());

                //do the serialization
                serializeXML(args[0].iterate(), outputProperties, file, doAppend);
                
            } else if(isCalledAs(FN_SERIALIZE_BINARY_LN)) {
                final boolean doAppend = (args.length > 2) && "true".equals(args[2].itemAt(0).getStringValue());
                serializeBinary((BinaryValue)args[0].itemAt(0), file, doAppend);
                
            } else {
                throw new XPathException(this, "Unknown function name");
            }

            return BooleanValue.TRUE;
	}
	
	
	private Properties parseXMLSerializationOptions(final Sequence sSerializeParams) throws XPathException {
        //parse serialization options
        final Properties outputProperties = new Properties();

        outputProperties.setProperty( OutputKeys.INDENT, "yes" );
        outputProperties.setProperty( OutputKeys.OMIT_XML_DECLARATION, "yes" );

        if (sSerializeParams.hasOne() && Type.subTypeOf(sSerializeParams.getItemType(), Type.NODE)) {
            SerializerUtils.getSerializationOptions(this, (NodeValue) sSerializeParams.itemAt(0), outputProperties);
        } else {
            SequenceIterator siSerializeParams = sSerializeParams.iterate();
            while(siSerializeParams.hasNext()) {
                final String serializeParam = siSerializeParams.nextItem().getStringValue();
                final String opt[] = Option.parseKeyValuePair(serializeParam);
                if(opt != null && opt.length == 2) {
                    outputProperties.setProperty( opt[0], opt[1] );
                }
            }
        }
        return outputProperties;
	}
	
	
	private void serializeXML(final SequenceIterator siNode, final Properties outputProperties, final File file, final boolean doAppend) throws XPathException {
            // serialize the node set
            SAXSerializer sax = null;
            Writer writer = null;
            try {
                sax = (SAXSerializer)SerializerPool.getInstance().borrowObject(SAXSerializer.class);
                final OutputStream os = new FileOutputStream(file, doAppend);
                final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
                writer = new OutputStreamWriter(os, encoding);

                sax.setOutput(writer, outputProperties);
                final Serializer serializer = context.getBroker().getSerializer();
                serializer.reset();
                serializer.setProperties(outputProperties);
                serializer.setReceiver(sax);

                sax.startDocument();

                while(siNode.hasNext()) {
                    final NodeValue next = (NodeValue)siNode.nextItem();
                    serializer.toSAX(next);	
                }

                sax.endDocument();
                
            } catch(final SAXException e) {
                throw new XPathException(this, "Cannot serialize file. A problem occurred while serializing the node set: " + e.getMessage(), e);
                
            } catch(final IOException e) {
                throw new XPathException(this, "Cannot serialize file. A problem occurred while serializing the node set: " + e.getMessage(), e);
                
            } finally {
                if(writer != null) {
                    try {
                        writer.close();
                    } catch(final IOException ioe) {
                        logger.warn("Cannot serialize file '" + file.getAbsolutePath() + " ': " + ioe.getMessage(), ioe);
                    }
                }
                
                if(sax != null) {
                    SerializerPool.getInstance().returnObject(sax);
                }
            }
	}

    private void serializeBinary(final BinaryValue binary, final File file, final boolean doAppend) throws XPathException
    {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file, doAppend);

            binary.streamBinaryTo(os);

        } catch(final FileNotFoundException fnfe) {
            throw new XPathException(this, "Cannot serialize file. A problem occurred while serializing the binary data: " + fnfe.getMessage(), fnfe);
        } catch(final IOException ioe) {
            throw new XPathException(this, "Cannot serialize file. A problem occurred while serializing the binary data: " + ioe.getMessage(), ioe);
        } finally {
            if(os != null) {
                try {
                    os.close();
                } catch(final IOException ioe) {
                    logger.warn("Cannot serialize file '" + file.getAbsolutePath() + " ': " + ioe.getMessage(), ioe);
                }
            }
        }
    }
}
