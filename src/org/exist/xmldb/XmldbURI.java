/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; er version.
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
 *  $Id:
 */
package org.exist.xmldb;

import java.lang.ClassCastException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Pattern;

/** A utility class for xmldb URis.
 * Since, java.net.URI is <strong>final</strong> this class acts as a wrapper.
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class XmldbURI {
	
	//Should be provided by org.xmldb.api package !!! 
	public static final String XMLDB_URI_PREFIX = "xmldb:";
	
	private URI wrappedURI;	
	private String instanceName;
	private String host;
	private int port = -1; 
	private String context;  	
	private String collectionName;
	private String apiName;

	/**
	 * @param xmldbURI Contructs an XmldbURI by parsing the given string. 
	 * Note that we construct URIs starting with XmldbURI.XMLDB_URI_PREFIX.
	 * Do not forget that org.xmldb.api.DatabaseManager <strong>trims</strong> this prefix.
	 * @throws URISyntaxException If the given string is not a valid xmldb URI.
	 */
	public XmldbURI(String xmldbURI) throws URISyntaxException {
    	try {
    		wrappedURI = new URI(xmldbURI);    		
    		parseURI();
    	} catch (URISyntaxException e) {
        	wrappedURI = null;        	
        	throw e; 
    	}
	}
	
	private void parseURI() throws URISyntaxException {
		int index = -1;
		int lastIndex = -1;	
		//TODO : use relative URIs pathes here ?
		String path = null;	
		URI truncatedURI;
		//Reinitialise members
		this.instanceName = null;
		this.host = null;
		this.port = -1;
		this.apiName = null;		
		if (wrappedURI.getScheme() != null) {			
			if (!wrappedURI.toString().startsWith(XMLDB_URI_PREFIX))
				throw new URISyntaxException(wrappedURI.toString(), "xmldb URI scheme does not start with " + XMLDB_URI_PREFIX);
			try {
				truncatedURI = new URI(wrappedURI.toString().substring(XMLDB_URI_PREFIX.length()));
			} catch (URISyntaxException e) {
				//Put the "right" URI in the message ;-)
				throw new URISyntaxException(wrappedURI.toString(), e.getMessage());				
			}
			instanceName = truncatedURI.getScheme();
			if (instanceName == null)   
				//Put the "right" URI in the message ;-)
				throw new URISyntaxException(wrappedURI.toString().toString(), "xmldb URI scheme has no instance name");
			host = truncatedURI.getHost();
			port = truncatedURI.getPort();	
			path = truncatedURI.getPath();
	    	if (truncatedURI.getQuery() != null)
	    		//Put the "right" URI in the message ;-)
	    		throw new URISyntaxException(wrappedURI.toString(), "xmldb URI should not provide a query part");
	    	if (truncatedURI.getFragment() != null)
	    		//Put the "right" URI in the message ;-)    		
	    		throw new URISyntaxException(wrappedURI.toString(), "xmldb URI should not provide a fragment part");				
		}			
		if (path != null) {
			if (host != null) {  
	    		//TODO : use named constants  
	        	index = path.lastIndexOf("/xmlrpc");        	         	
	        	if (index > lastIndex) {
	        		apiName = "xmlrpc";        		
	        		collectionName = path.substring(index + "/xmlrpc".length());
	        		context = path.substring(0, index) + "/xmlrpc";
	        		lastIndex = index;
	        	}         	
	        	//TODO : use named constants  
	        	index = path.lastIndexOf("/webdav");        	         	
	        	if (index > lastIndex) {
	        		apiName = "webdav";        		
	        		collectionName = path.substring(index + "/webdav".length());
	        		context = path.substring(0, index) + "/webdav";
	        		lastIndex = index;
	        	}    		
	        	//Default : a local URI...
	        	if (apiName == null) {	    			
	        		apiName = "rest-style";  
	        		collectionName =  path; 	
	    			//TODO : determine the context out of a clean root collection policy.
	    			context = null;	        		        		
	        	}
    		}    	
	        else 
	        {	        	
	        	if (port > -1)
	        		//Put the "right" URI in the message ;-)
	        		throw new URISyntaxException(wrappedURI.toString(), "Local xmldb URI should not provide a port");
	        	apiName = "direct access";  
	        	context = null;
	        	collectionName = path; 	 
	        }
	    	//Trim trailing slash if necessary    	
	    	if (collectionName != null && collectionName.length() > 1 && collectionName.endsWith("/"))    		
	    		collectionName = collectionName.substring(0, collectionName.length() - 1);              	
	    	//TODO : check that collectionName starts with DBBroker.ROOT_COLLECTION ?	
		}
	}

	private void computeURI() throws URISyntaxException {		
		StringBuffer buf = new StringBuffer();
		if (instanceName != null)	
			buf.append(XMLDB_URI_PREFIX).append(instanceName).append("://");
		if (host != null)	
			buf.append(host);				
		if (port > -1)
			buf.append(":" + port);		
		if (context != null)
			buf.append(context);
		//TODO : eventually use a prepend.root.collection system property 		
		if (collectionName != null)
			buf.append(collectionName);
		try {
			wrappedURI = new URI(buf.toString());			
			parseURI();	
    	} catch (URISyntaxException e) {
        	wrappedURI = null;        	
        	throw e; 
    	}			
	}	
	
	public void setInstanceName(String instanceName) throws URISyntaxException {		 
		String oldInstanceName = this.instanceName;
		try {
			this.instanceName = instanceName;
			computeURI();
		} catch (URISyntaxException e) {
			this.instanceName = oldInstanceName;
			throw e;
		}			
	}
	
	public void setHost(String host) throws URISyntaxException {
		String oldHost = this.host;
		try {
			this.host = host;
			computeURI();
		} catch (URISyntaxException e) {
			this.host = oldHost;
			throw e;
		}
	}
	
	public void setPort(int port) throws URISyntaxException {
		//TODO : check range ?
		int oldPort = this.port;
		try {
			this.port = port;
			computeURI();
		} catch (URISyntaxException e) {
			this.port = oldPort;
			throw e;
		}
	}
	
	public void setContext(String context) throws URISyntaxException {
		String oldContext = context;
		try {
			this.context = context;
			computeURI();
		} catch (URISyntaxException e) {
			this.context = oldContext;
			throw e;
		}
	}
	
	public void setCollectionName(String collectionName) throws URISyntaxException {
		String oldCollectionName = collectionName;
		try {
			this.collectionName = collectionName;
			computeURI();
		} catch (URISyntaxException e) {
			this.collectionName = oldCollectionName;
			throw e;
		}
	}
	
	public URI getURI() { 		
		return wrappedURI; 
	}
	
	public String getInstanceName() {		
		return instanceName; 
	}
	
	public String getHost() { 		
		return host; 
	}
	public int getPort() {		
		return port; 
	}
	public String getCollectionName() {		
		return collectionName; 
	}
	
	public String getApiName() {		
		return apiName; 
	}
	
	public String getContext() {		
		return context; 
	}
	
	public int compareTo(Object ob) throws ClassCastException {
		if (!(ob instanceof XmldbURI))
			throw new ClassCastException("The provided Object is not an XmldbURI");		
		return wrappedURI.compareTo(((XmldbURI)ob).getURI());
	}
	
	public static XmldbURI create(String str) {		
		try {
			return new XmldbURI(str);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
	}
	
	public boolean equals(Object ob) {
		if (!(ob instanceof XmldbURI))
			return false;
		return wrappedURI.equals(((XmldbURI)ob).getURI());
	}	
	
	public boolean isAbsolute() {	
		return wrappedURI.isAbsolute();
	}
	
	public boolean isOpaque() {			
		return wrappedURI.isOpaque();
	}
	
	public XmldbURI normalize() {			
		String context = this.getContext();
		URI uri = URI.create((context == null) ? "" : context);		
		try {
			XmldbURI xmldbURI = new XmldbURI(this.toString());
			xmldbURI.setContext((uri.normalize()).toString());
			return xmldbURI;
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e.getMessage());
		}		

	}	
	
	public XmldbURI relativize(XmldbURI uri) {
		if (uri == null)
			throw new NullPointerException("The provided URI is null");	
//		TODO : everything but contexts must be equal !
		String context1 = this.getContext();
		String context2 = uri.getContext();
		URI uri1 = URI.create((context1 == null) ? "" : context1);
		URI uri2 = URI.create((context2 == null) ? "" : context2);		
		return create((uri1.relativize(uri2)).toString());		
	}
	
	public XmldbURI resolve(String str) throws NullPointerException, IllegalArgumentException {	
		if (str == null)
			throw new NullPointerException("The provided String is null");	
		try {
			XmldbURI uri = new XmldbURI(str);
			return resolve(uri);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
	}	
	
	public XmldbURI resolve(XmldbURI uri) throws NullPointerException {	
		if (uri == null)
			throw new NullPointerException("The provided URI is null");	
		//TODO : refactor
		return create(wrappedURI.resolve(uri.getURI()).toString());
	}
	
	public String toASCIIString() {	
		//TODO : trim trailing slash if necessary
		return wrappedURI.toASCIIString();
	}
	
	public URL toURL() throws IllegalArgumentException, MalformedURLException {			
		return wrappedURI.toURL();
	}
	
	public String toString() {	
		//TODO : trim trailing slash if necessary
		return wrappedURI.toString();
	}
	
//	TODO : prefefined URIs as static classes...

}
