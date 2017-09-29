package org.exist.extensions.exquery.xdm.type.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.TreeMap;

public class MultipartEntity{
	
	private final TreeMap<String, String> headers;

	private final String content; 
	
	public MultipartEntity(final BufferedReader reader, final String boundary) throws IOException{
		this.headers = getHeadersMap(reader);
		this.content = getEntityContent(reader, boundary);
	}

	private TreeMap<String, String> getHeadersMap(final BufferedReader reader) throws IOException{
		final TreeMap<String, String> headersMap = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		String line;
        while ((line = reader.readLine()) != null) {
        	if(!line.equals("")){
        		final String[] headerSplit = line.split(": ");
        		headersMap.put(headerSplit[0], headerSplit[1]);
        	}else{
        		break;
        	}
        }
        return headersMap;
	}

	public TreeMap<String, String> getHeaders() {
		return headers;
	}
	
	public String getEntityContent(final BufferedReader reader, final String boundary) throws IOException{
		final StringBuffer strBuffer = new StringBuffer();
		String line;
        while ((line = reader.readLine()) != null) {
        	if(!line.equals("--" + boundary) && !line.equals("--" + boundary + "--")){
        		strBuffer.append(line);
        	}else{
        		break;
        	}
        }
		return strBuffer.toString();
	}

	public String getContent() {
		return content;
	}
}
