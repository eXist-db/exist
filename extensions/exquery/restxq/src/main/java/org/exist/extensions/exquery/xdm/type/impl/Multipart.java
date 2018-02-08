package org.exist.extensions.exquery.xdm.type.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exquery.restxq.RestXqServiceException;

public class Multipart {
	
	private final String boundary;
	
	private final String rootId;
	
	private final String encoding;
	
	private final List<MultipartEntity> entities;
	
	private final MemTreeBuilder builder;
	
	private static final String NAME_SPACE_URI = "http://expath.org/ns/http-client";
	private static final String ELEM_PREFIX = "http";
	
	private static final String CT_BOUNDARY_REGEX = "boundary=['\"]{0,1}(.*?)('|\"|;|\\s|$)";
	private static final String CT_START_PARAM_REGEX = "start=['\"]{0,1}(.*?)('|\"|;|\\s|$)";
	
	public Multipart(final String contentTypeHeader, final InputStream is, final String encoding) throws RestXqServiceException, IOException{
		this.boundary = getMultiPartBoundary(contentTypeHeader);
		this.rootId = getRootPartId(contentTypeHeader);
		this.encoding = encoding;
		
		this.builder = new MemTreeBuilder();
		this.builder.startDocument();
		this.builder.startElement(new QName("multipart", NAME_SPACE_URI, ELEM_PREFIX) , null);
		
		this.entities = new ArrayList<MultipartEntity>();
		
		final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		// move to the first boundary delimiter
		String line;
		while((line = reader.readLine()) != null){
			if(line.equals("--" + this.boundary)){
				break;
			}
		}
		// get multipart entities
        while (reader.ready()) {
        	this.entities.add(new MultipartEntity(reader, this.boundary));
        }
        
        moveRootEntityToHead();
        
        for(final MultipartEntity entity : this.entities){
        	buildEntityHeaderElements(entity.getHeaders(), this.builder);
    		buildEntityBodyElement(entity, this.builder);
    	}
        
		this.builder.endElement();
		this.builder.endDocument();
		
	}
	
	public String getBoundary() {
		return this.boundary;
	}

	public String getRootId() {
		return this.rootId;
	}

	public String getEncoding() {
		return this.encoding;
	}
	
	public List<MultipartEntity> getEntities(){
		return this.entities;
	}
	
	public DocumentImpl getDocument(){
		return this.builder.getDocument();
	}
	
	/**
	 * Builds a http:header element for each entity header in a given builder.
	 * This method ignores Content-Type and Content-Transfer-Encoding headers,
	 * cause there information are present in entity http:body element.
	 * 
	 * @param headers entity headers map
	 * @param builder {@link MemTreeBuilder}
	 */
	private void buildEntityHeaderElements(final TreeMap<String, String> headers, final MemTreeBuilder builder){
		for(final Entry<String, String> entry: headers.entrySet()){
			final String headerName = entry.getKey();
			if(headerName.equalsIgnoreCase("Content-Type") || headerName.equalsIgnoreCase("Content-Transfer-Encoding")){
				continue;
			}
			builder.startElement(new QName("header", NAME_SPACE_URI, ELEM_PREFIX), null);
			builder.addAttribute(new QName("name", null), headerName);
			builder.addAttribute(new QName("value", null), entry.getValue());
			builder.endElement();
		}
	}
	
	/**
	 * Builds a http:body element in a given builder.
	 * The Content-Type is present as media-type element attribute and the body content is decoded as base64
	 * if the Content-Transfer-Encoding is not base64.
	 * 
	 * @param entity {@link MultipartEntity}
	 * @param builder {@link MemTreeBuilder}
	 * @throws RestXqServiceException will be thrown if an entity does not have a Content-Type header
	 */
	private void buildEntityBodyElement(final MultipartEntity entity, final MemTreeBuilder builder) throws RestXqServiceException{
		final String entityContentType = entity.getHeaders().get("Content-Type");
		final String transferEncoding = entity.getHeaders().get("Content-Transfer-Encoding");
		
		if(entityContentType == null || entityContentType.isEmpty()){
			throw new RestXqServiceException("No body part Content-Type could be found");
		}
		
		final QName body = new QName("body", NAME_SPACE_URI, ELEM_PREFIX);
		builder.startElement(body, null);
		builder.addAttribute(new QName("media-type", null), entityContentType);
		
		final String content;
		if(transferEncoding != null && transferEncoding.equalsIgnoreCase("base64")){
			content = entity.getContent();
		}else{
			content = Base64.getEncoder().encodeToString(entity.getContent().getBytes());
		}
		
		builder.characters(content);
		builder.endElement();
	}
	
	/**
	 * Removes root entity if found to and insert it to head of the entities list by shifting next elements to right.
	 * 
	 * @throws RestXqServiceException will be thrown if start parameter is present in header and no root entity could be found 
	 */
	private void moveRootEntityToHead() throws RestXqServiceException{
		boolean isRootFound = false;
        if(this.rootId != null && !this.rootId.isEmpty()){
        	for(int i = 0; i < this.entities.size(); i++){
        		final MultipartEntity entity = this.entities.get(i);
        		final String contentId = entity.getHeaders().get("content-id");
        		if(this.rootId.equals(contentId)){
        			this.entities.remove(i);
        			this.entities.add(0, entity);
        			isRootFound = true;
        			break;
        		}
        	}
        	if(!isRootFound){
            	throw new RestXqServiceException("MUTLIPART ERROR: Start parameter is present but does not refer to any part");
            }
        }
	}

	/**
     * Get Multipart Boundary from request content-type header
     */
    private String getMultiPartBoundary(final String contentType) throws RestXqServiceException{
    	final String boundaryRegExp = CT_BOUNDARY_REGEX;
    	final Pattern ptn = Pattern.compile(boundaryRegExp);
		final Matcher mtc = ptn.matcher(contentType);
		String boundary = null;
		if(mtc.find()){
			boundary = mtc.group(1);
		}
		if(boundary == null || boundary.isEmpty()){
			throw new RestXqServiceException("No multipart boundary could be found");
		}
		return boundary;
    }
    
    /**
     * Get root part ID
     * If start parameter not present in Content-Type then
     * the first body part is root part
     * @see <a href="https://tools.ietf.org/html/rfc2387#section-3.2">RFC 2387</a>
     */
    private String getRootPartId(final String contentType){
    	final String rootIdRegExp = CT_START_PARAM_REGEX;
		final Pattern ptn = Pattern.compile(rootIdRegExp);
		final Matcher mtc = ptn.matcher(contentType);
		if(mtc.find()){
			return mtc.group(1);
		}else{
			return null;
		}
    }
}
