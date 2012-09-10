/*
Copyright (c) 2012, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import org.exist.EXistException;
import org.exist.extensions.exquery.restxq.impl.adapters.SequenceAdapter;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.value.Item;
import org.exquery.http.HttpResponse;
import org.exquery.restxq.RestXqServiceException;
import org.exquery.restxq.impl.serialization.AbstractRestXqServiceSerializer;
import org.exquery.restxq.impl.serialization.SerializationProperty;
import org.exquery.serialization.annotation.MethodAnnotation.SupportedMethod;
import org.exquery.xquery.Sequence;
import org.xml.sax.SAXException;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RestXqServiceSerializerImpl extends AbstractRestXqServiceSerializer {

    final BrokerPool brokerPool;
    
    public RestXqServiceSerializerImpl(final BrokerPool brokerPool) {
        this.brokerPool = brokerPool;
    }
    
    private BrokerPool getBrokerPool() {
        return brokerPool;
    }
    
    @Override
    protected void serializeBinaryBody(final Sequence result, final HttpResponse response) throws RestXqServiceException {
        throw new UnsupportedOperationException("TODO adam needs to implement this yet!");
    }

    @Override
    protected void serializeNodeBody(final Sequence result, final HttpResponse response, final Map<SerializationProperty, String> serializationProperties) throws RestXqServiceException {
        
        DBBroker broker = null;
        SAXSerializer sax = null;
        
        final SerializerPool serializerPool = SerializerPool.getInstance();
        try {
            broker = getBrokerPool().get(brokerPool.getSubject());
            
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            sax = (SAXSerializer) serializerPool.borrowObject(SAXSerializer.class);
	        
            final Writer writer = new OutputStreamWriter(response.getOutputStream(), serializationProperties.get(SerializationProperty.ENCODING));
            final Properties outputProperties = serializationPropertiesToProperties(serializationProperties);
            sax.setOutput(writer, outputProperties);
	
            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);
	
            serializer.toSAX(((SequenceAdapter)result).getExistSequence());
	
            writer.flush();
            writer.close();
	        
        } catch(IOException ioe) {    
            throw new RestXqServiceException("Error while serializing xml: " + ioe.toString(), ioe);
        } catch(EXistException ee) {   
            throw new RestXqServiceException("Error while serializing xml: " + ee.toString(), ee);
        } catch(SAXException se) {    
            throw new RestXqServiceException("Error while serializing xml: " + se.toString(), se);
        } finally {
            if(sax != null) {
                SerializerPool.getInstance().returnObject(sax);
            }
            if(broker != null) {
                getBrokerPool().release(broker);
            }
        }
    }
    
    
    private Properties serializationPropertiesToProperties(final Map<SerializationProperty, String> serializationProperties) {
        final Properties props = new Properties();
        
        for(final Entry<SerializationProperty, String> serializationProperty : serializationProperties.entrySet()) {
            
            if(serializationProperty.getKey() == SerializationProperty.METHOD && serializationProperty.getValue().equals(SupportedMethod.html.name())) {
                //Map HTML -> HTML5 as eXist doesnt have a html serializer that isnt html5
                props.setProperty(serializationProperty.getKey().name().toLowerCase(), SupportedMethod.html5.name());
            } else {
                props.setProperty(serializationProperty.getKey().name().toLowerCase(), serializationProperty.getValue());
            }
        }
        
        return props;
    }
}