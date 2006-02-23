/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Pragma;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

public class Serialize extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("serialize", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Writes the node set passed in parameter $a into a file on the file system. The " +
            "full path to the file is specified in parameter $b. $c contains a " +
            "sequence of zero or more serialization parameters specified as key=value pairs. The " +
            "serialization parameters are the same as those recognized by \"declare option exist:serialize\". " +
            "The function returns false if the " +
            "specified file can not be created or is not writable, true on success. If the passed " +
            "node set is empty, the empty sequence is returned.",
            new SequenceType[] { 
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)
            },
            new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE));
    
    public Serialize(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        if (args[0].getLength() == 0)
            return Sequence.EMPTY_SEQUENCE;
        
        // check the file output path
        String path = args[1].itemAt(0).getStringValue();
        File file = new File(path);
        if (file.isDirectory()) {
            LOG.debug("Output file is a directory: " + file.getAbsolutePath());
            return BooleanValue.FALSE;
        }
        if (file.exists() && !file.canWrite()) {
            LOG.debug("Cannot write to file " + file.getAbsolutePath());
            return BooleanValue.FALSE;
        }
        
        // parse serialization options
        Properties outputProperties = new Properties();
        outputProperties.setProperty(OutputKeys.INDENT, "yes");
        outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        for (SequenceIterator i = args[2].iterate(); i.hasNext(); ) { 
            String opt[] = Pragma.parseKeyValuePair(i.nextItem().getStringValue());
            outputProperties.setProperty(opt[0], opt[1]);
        }
        
        // serialize the node set
        SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        try {
            String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), encoding);
            sax.setOutput(writer, outputProperties);
            Serializer serializer = context.getBroker().getSerializer();
            serializer.reset();
            serializer.setProperties(outputProperties);
            serializer.setReceiver(sax);

            sax.startDocument();
            
            for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                NodeValue next = (NodeValue) i.nextItem();
                serializer.toSAX(next);
            }
            
            sax.endDocument();
            writer.close();
        } catch (SAXException e) {
            throw new XPathException(getASTNode(), "A problem ocurred while serializing the node set: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XPathException(getASTNode(), "A problem ocurred while serializing the node set: " + e.getMessage(), e);
        } finally {
            SerializerPool.getInstance().returnObject(sax);
        }
        return BooleanValue.TRUE;
    }
}
