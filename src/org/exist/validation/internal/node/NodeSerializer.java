/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
package org.exist.validation.internal.node;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.storage.io.ExistIOException;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.SequenceIterator;

/**
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class NodeSerializer {
    
    private XQueryContext context;
    
    /**
     * Creates a new instance of NodeSerializer
     */
    public NodeSerializer(XQueryContext context) {
        this.context=context;
    }
    
//    public Properties parseSerializationOptions(SequenceIterator siSerializeParams) throws XPathException
//    {
//    	//parse serialization options
//        Properties outputProperties = new Properties();
//        outputProperties.setProperty(OutputKeys.INDENT, "yes");
//        outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
//        while(siSerializeParams.hasNext())
//        {
//            String opt[] = Option.parseKeyValuePair(siSerializeParams.nextItem().getStringValue());
//            outputProperties.setProperty(opt[0], opt[1]);
//        }
//
//        return outputProperties;
//    }
    
    public void serialize(SequenceIterator siNode, Properties outputProperties, OutputStream os) throws IOException {
        // serialize the node set
        SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        try {
            String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
            Writer writer = new OutputStreamWriter(os, encoding);
            sax.setOutput(writer, outputProperties);
            Serializer serializer = context.getBroker().getSerializer();
            serializer.reset();
            serializer.setProperties(outputProperties);
            serializer.setReceiver(sax);
            
            sax.startDocument();
            
            while(siNode.hasNext()) {
                NodeValue next = (NodeValue)siNode.nextItem();
                serializer.toSAX(next);
            }
            
            sax.endDocument();
            writer.close();
            
        } catch(Exception e) {
            throw new ExistIOException("A problem ocurred while serializing the node set: " + e.getMessage(), e);
        
        } finally {
            SerializerPool.getInstance().returnObject(sax);
        }
    }
    
    
}
