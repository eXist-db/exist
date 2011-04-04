/*
 *  eXist EXPath
 *  Copyright (C) 2011 Adam Retter <adam@existsolutions.com>
 *  www.existsolutions.com
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
package org.expath.httpclient.model.exist;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;
import javax.xml.transform.OutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.SequenceIterator;
import org.expath.httpclient.HttpClientException;
import org.expath.httpclient.model.Sequence;
import org.xml.sax.SAXException;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public class EXistSequence implements Sequence {
 
    private final org.exist.xquery.value.Sequence sequence;
    private SequenceIterator sequenceIterator = SequenceIterator.EMPTY_ITERATOR;
    private final XQueryContext context;
    
    public EXistSequence(org.exist.xquery.value.Sequence sequence, XQueryContext context) throws XPathException {
        this.sequence = sequence;
        if(sequence != null) {
            this.sequenceIterator = sequence.iterate();
        }
        this.context = context;
    }
    
    //@Override
    public boolean isEmpty() throws HttpClientException {
        return sequence.isEmpty();
    }

    //@Override
    public Sequence next() throws HttpClientException {
        try {
            return new EXistSequence((NodeValue)sequenceIterator.nextItem(), context);
        } catch (XPathException xpe) {
            throw new HttpClientException(xpe.getMessage(), xpe);
        }
    }

    //@Override
    public void serialize(OutputStream out, Properties params) throws HttpClientException {
        
        SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        params.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        
        try {
            SequenceIterator itSequence = SequenceIterator.EMPTY_ITERATOR;
            if(sequence != null) {
                itSequence = sequence.iterate();
            }
            
            String encoding = params.getProperty(OutputKeys.ENCODING, "UTF-8");
            Writer writer = new OutputStreamWriter(out, encoding);
            sax.setOutput(writer, params);
            Serializer serializer = context.getBroker().getSerializer();
            serializer.reset();
            serializer.setProperties(params);
            serializer.setSAXHandlers(sax, sax);

            sax.startDocument();
            
            while(itSequence.hasNext()) {
               NodeValue next = (NodeValue)itSequence.nextItem();
               serializer.toSAX(next);	
            }
            
            sax.endDocument();
            writer.close();
        } catch(SAXException saxe) {
            throw new HttpClientException("A problem occurred while serializing the node set: " + saxe.getMessage(), saxe);
        } catch(IOException ioe) {
            throw new HttpClientException("A problem occurred while serializing the node set: " + ioe.getMessage(), ioe);
        } catch(XPathException xpe) {
            throw new HttpClientException("A problem occurred while serializing the node set: " + xpe.getMessage(), xpe);
        } finally {
            SerializerPool.getInstance().returnObject(sax);
        }
    }
}