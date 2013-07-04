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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.extensions.exquery.restxq.impl.adapters.SequenceAdapter;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.value.BinaryValue;
import org.exquery.http.HttpResponse;
import org.exquery.restxq.RestXqServiceException;
import org.exquery.restxq.impl.serialization.AbstractRestXqServiceSerializer;
import org.exquery.restxq.impl.serialization.SerializationProperty;
import org.exquery.serialization.annotation.MethodAnnotation.SupportedMethod;
import org.exquery.xquery.Sequence;
import org.exquery.xquery.Type;
import org.exquery.xquery.TypedValue;
import org.xml.sax.SAXException;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RestXqServiceSerializerImpl extends AbstractRestXqServiceSerializer {

    private final static Logger LOG = Logger.getLogger(RestXqServiceSerializerImpl.class);
    
    final BrokerPool brokerPool;
    
    public RestXqServiceSerializerImpl(final BrokerPool brokerPool) {
        this.brokerPool = brokerPool;
    }
    
    private BrokerPool getBrokerPool() {
        return brokerPool;
    }
    
    @Override
    protected void serializeBinaryBody(final Sequence result, final HttpResponse response) throws RestXqServiceException {
        final Iterator<TypedValue> itResult = result.iterator();
        while(itResult.hasNext()) {
            final TypedValue typedValue = itResult.next();
            if(typedValue.getType() == Type.BASE64_BINARY || typedValue.getType() == Type.HEX_BINARY) {
                
                final BinaryValue binaryValue = (BinaryValue)typedValue.getValue();
                OutputStream os = null; 
                try {
                    os = response.getOutputStream();
                    binaryValue.streamBinaryTo(os);
                } catch(final IOException ioe) {
                    throw new RestXqServiceException("Error while serializing binary: " + ioe.toString(), ioe);
                } finally {
                    if(os != null) {
                        try { 
                            os.close();
                        } catch (final IOException ioe) {
                            LOG.warn(ioe);
                        }
                    }
                }
                
                return; //TODO support more than one binary result -- multipart?
            } else {
                throw new RestXqServiceException("Expected binary value, but found: " + typedValue.getType().name());
            }
        }
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
            } else if(serializationProperty.getKey() == SerializationProperty.OMIT_XML_DECLARATION) {
                
                //TODO why are not all keys transformed from '_' to '-'? I have a feeling we did something special for MEDIA_TYPE???
                props.setProperty(serializationProperty.getKey().name().toLowerCase().replace('_', '-'), serializationProperty.getValue());
            } else {
                props.setProperty(serializationProperty.getKey().name().toLowerCase(), serializationProperty.getValue());
            }
        }
        
        return props;
    }
}