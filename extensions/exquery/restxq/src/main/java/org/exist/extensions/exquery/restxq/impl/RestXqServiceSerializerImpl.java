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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.exist.EXistException;
import org.exist.dom.memtree.ElementImpl;
import org.exist.extensions.exquery.restxq.impl.adapters.SequenceAdapter;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.BinaryValue;
import org.exquery.http.HttpResponse;
import org.exquery.restxq.RestXqServiceException;
import org.exquery.restxq.impl.serialization.AbstractRestXqServiceSerializer;
import org.exquery.restxq.impl.serialization.SerializationProperty;
import org.exquery.serialization.annotation.MethodAnnotation.SupportedMethod;
import org.exquery.xquery.Sequence;
import org.exquery.xquery.Type;
import org.exquery.xquery.TypedValue;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
class RestXqServiceSerializerImpl extends AbstractRestXqServiceSerializer {
    private final BrokerPool brokerPool;
    
    public RestXqServiceSerializerImpl(final BrokerPool brokerPool) {
        this.brokerPool = brokerPool;
    }
    
    private BrokerPool getBrokerPool() {
        return brokerPool;
    }
    
    @Override
    protected void serializeBinaryBody(final Sequence result, final HttpResponse response) throws RestXqServiceException {
        for (final TypedValue typedValue : (Iterable<TypedValue>) result) {
            if (typedValue.getType() == Type.BASE64_BINARY || typedValue.getType() == Type.HEX_BINARY) {
                final BinaryValue binaryValue = (BinaryValue) typedValue.getValue();
                try (final OutputStream os = response.getOutputStream()) {
                    binaryValue.streamBinaryTo(os);
                } catch (final IOException ioe) {
                    throw new RestXqServiceException("Error while serializing binary: " + ioe.toString(), ioe);
                }

                return; //TODO support more than one binary result -- multipart?
            } else {
                throw new RestXqServiceException("Expected binary value, but found: " + typedValue.getType().name());
            }
        }
    }
    
    @Override
    protected void serializeNodeBody(final Sequence result, final HttpResponse response, final Map<SerializationProperty, String> serializationProperties) throws RestXqServiceException {
    	if(!isMultipartRelatedMT(serializationProperties)){
    		try(final DBBroker broker = getBrokerPool().getBroker();
                    final Writer writer = new OutputStreamWriter(response.getOutputStream(), serializationProperties.get(SerializationProperty.ENCODING))) {
                final Properties outputProperties = serializationPropertiesToProperties(serializationProperties);
                final XQuerySerializer xqSerializer = new XQuerySerializer(broker, outputProperties, writer);
                xqSerializer.serialize(((SequenceAdapter)result).getExistSequence());
                writer.flush();
            } catch(IOException | XPathException | SAXException | EXistException ioe) {
                throw new RestXqServiceException("Error while serializing xml: " + ioe.toString(), ioe);
            }
    	}else{
    		// multipart/related serialization
    		final SequenceAdapter sequence = (SequenceAdapter)result;
    		final org.exist.xquery.value.Sequence existSeq = sequence.getExistSequence();
    		
    		final Node multipart = getMultipartElem(existSeq);
    		validatePartsHeaders(multipart);
    		final String boundary = getMultipartBoundary(multipart);
			final String rootId = getMultipartRootId(multipart);
			
			// set http content-type header by root id and boundary
			response.setContentType(buildMultipartresponseCTHeader(boundary, rootId, serializationProperties.get(SerializationProperty.ENCODING)));
			
			final String httpMultiparts;
			// parse parts to string and write to output stream
			try(final Writer writer = new OutputStreamWriter(response.getOutputStream())){
				httpMultiparts = parseMultipart(multipart, boundary);
				writer.write(httpMultiparts);
				writer.flush();
			} catch (IOException | EXistException | SAXException e) {
				throw new RestXqServiceException("Error while serializing xml: " + e.toString(), e);
			}
    	}
    }
    
    private Properties serializationPropertiesToProperties(final Map<SerializationProperty, String> serializationProperties) {
        final Properties props = new Properties();
        
        for(final Entry<SerializationProperty, String> serializationProperty : serializationProperties.entrySet()) {
            
            if(serializationProperty.getKey() == SerializationProperty.METHOD && serializationProperty.getValue().equals(SupportedMethod.html.name())) {
                //Map HTML -> HTML5 as eXist doesn't have a html serializer that isn't html5
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
    
    /**
     * Verifies if the MediaType property is a multipart/releated media type.
     * 
     * @param serializationProperties
     * @return boolean
     */
    private boolean isMultipartRelatedMT(final Map<SerializationProperty, String> serializationProperties){
    	if(serializationProperties != null && !serializationProperties.isEmpty()){
    		final String mediaType = serializationProperties.get(SerializationProperty.MEDIA_TYPE); 
    		if(mediaType != null && mediaType.equals("multipart/related")){
    			return true;
    		}
    	}
    	return false;
    }
    
    /**
     * validate the multipart element response and returns a multipart node.
     * 
     * @param existSeq {@link org.exist.xquery.value.Sequence}
     * @return {@link Node}
     * @throws RestXqServiceException
     */
    private Node getMultipartElem(final org.exist.xquery.value.Sequence existSeq) throws RestXqServiceException{
    	final Node multipart;
    	if(existSeq.hasOne()){
			final Node rootNode = (Node)existSeq.itemAt(0);
			if(rootNode.getNodeType() == Node.DOCUMENT_NODE){
				multipart = rootNode.getFirstChild();
			}else if(rootNode.getNodeType() == Node.ELEMENT_NODE){
				multipart = rootNode;
			}else{
				throw new RestXqServiceException("Incompatible multipart response node type");
			}
		}else{
			throw new RestXqServiceException("Multipart response must be only one multipart node");
		}
		
		if(!multipart.getLocalName().equalsIgnoreCase("multipart")){
			throw new RestXqServiceException("Multipart response must have a multipart root node");
		}else if(!multipart.getNamespaceURI().equals("http://expath.org/ns/http-client")){
			throw new RestXqServiceException("Invalid namespace for multipart");
		}
		return multipart;
    }
    
    /**
     * Verifies that part headers include attributes name and value,
     * and checks if content-type header if present for each part.
     * 
     * @param multipart {@link Node}
     * @return boolean
     * @throws RestXqServiceException
     */
    private boolean validatePartsHeaders(final Node multipart) throws RestXqServiceException{
    	final NodeList childNodes = multipart.getChildNodes();
    	
    	boolean ctHeaderFound = false;
    	for(int i = 0; i < childNodes.getLength(); i++){
    		final Node node = childNodes.item(i);
    		
    		if(node.getLocalName().equals("header")){
    			final String headerName;
 				final String headerValue;
    			
 				try{
 					headerName = node.getAttributes().getNamedItem("name").getNodeValue();
 					headerValue = node.getAttributes().getNamedItem("value").getNodeValue();
 				}catch(NullPointerException e){
 					throw new RestXqServiceException("Header musst include name and value attributes");
 				}
    			
    			if(headerName.equalsIgnoreCase("content-type") && !headerValue.isEmpty()){
    				ctHeaderFound = true;
    			}
    			
    	    	// throw error if body element is found before a content-type header could not be found
    		}else if(node.getLocalName().equals("body")){
    			if(!ctHeaderFound){
    				throw new RestXqServiceException("A multipart entity must have a Content-Type header");
    			}else{
    				ctHeaderFound = false;
    			}
    		}
    	}
    	return false;
    }
    
    /**
     * Get multipart Boundary
     * 
     * @param multipart {@link Node}
     * @return String {@link String}
     * @throws RestXqServiceException 
     */
    private String getMultipartBoundary(final Node multipart) throws RestXqServiceException{
    	String boundary = null;
    	if(multipart.hasAttributes()){
    		final Node boundaryAttr = multipart.getAttributes().getNamedItem("boundary");
    		if(boundaryAttr != null){
    			boundary = boundaryAttr.getNodeValue();
    		}else{
    			throw new RestXqServiceException("Multipart boundary could not be found");
    		}
    	}else{
			throw new RestXqServiceException("Multipart boundary could not be found");
		}
		return boundary;
    }
    
    /**
     * get root part id, where the first content-id header is the root id.
     * If content-id header is not presented then return an empty root ID.
     * 
     * @param multipart {@link Node}
     * @return {@link String}
     */
    private String getMultipartRootId(final Node multipart){
    	String rootId = "";
		final NodeList nodeList = multipart.getChildNodes();
    	for(int i = 0; i < nodeList.getLength(); i++){
			final Node node = nodeList.item(i);
			// header verification has been met in validatePartsHeaders
			final Node nameAttr = node.getAttributes().getNamedItem("name");
			if(nameAttr != null && nameAttr.getNodeValue().equalsIgnoreCase("content-id")){
				rootId = node.getAttributes().getNamedItem("value").getNodeValue();
				break;
			}
		}
    	return rootId;
    }
    
    private String buildMultipartresponseCTHeader(final String boundary, final String rootId, final String encoding){
    	final String startParam;
    	if(rootId != null && !rootId.isEmpty()){
    		startParam = "start=\"" + rootId + "\"";
    	}else{
    		startParam = "";
    	}
    	
    	final String charsetParam;
    	if(encoding != null && !encoding.isEmpty()){
    		charsetParam = "charset=" + encoding;
    	}else{
    		charsetParam = "";
    	}
    	
    	final String boundaryParam = "boundary=\"" + boundary + "\"";
    	
    	return "multipart/related; " + startParam + "; " + boundaryParam + "; " + charsetParam;
    }
    
    private String parseMultipart(final Node multipart, final String boundary) throws EXistException, SAXException, RestXqServiceException{
 		final NodeList childNodes = multipart.getChildNodes();
 		final StringBuilder strBuilder = new StringBuilder();
 		strBuilder.append("--").append(boundary);
 		
 		for(int i = 0; i < childNodes.getLength(); i++){
 			final Node node = childNodes.item(i);
 			strBuilder.append("\n");
 			
 			if(node.getLocalName().equals("header")){
 				final String headerName = node.getAttributes().getNamedItem("name").getNodeValue();
 				final String headerValue = node.getAttributes().getNamedItem("value").getNodeValue();
 				strBuilder.append(headerName).append(": ").append(headerValue);	
 			}else{
 				strBuilder.append("\n");
 				
 				final Serializer ser = getBrokerPool().getBroker().getSerializer();
 				// if part content is XML then serialize
 				final NodeList partNodes = node.getChildNodes();
 				for(int j = 0; j < partNodes.getLength(); j++ ){
 					final Node pNode = partNodes.item(j);
 					switch(pNode.getNodeType()){
 					case Node.ELEMENT_NODE:
 						strBuilder.append(ser.serialize((ElementImpl)pNode));
 						break;
 					case Node.TEXT_NODE:
 						strBuilder.append(pNode.getNodeValue());
 						break;
					default:
 						throw new RestXqServiceException(pNode.getNodeType() + " node type is not supported");
 					}
 				}
 				strBuilder.append("\n");
 				strBuilder.append("--").append(boundary);
 			}
 		}
 		
 		// append ending to the last part
 		strBuilder.append("--");
 		
 		return strBuilder.toString();
     }
}
