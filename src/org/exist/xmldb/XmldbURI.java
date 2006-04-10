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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.xquery.util.URIUtils;

/** A utility class for xmldb URis.
 * Since, java.net.URI is <strong>final</strong> this class acts as a wrapper.
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class XmldbURI {
    
    protected final static Logger LOG = Logger.getLogger(DBBroker.class);
	
    public static final int NO_PORT = -1;
	//Should be provided by org.xmldb.api package !!! 
	public static final String XMLDB_URI_PREFIX = "xmldb:";
	
	private URI wrappedURI;	
	private String instanceName;  
    private String userInfo;
	private String host;
	private int port = NO_PORT; 
	private String context;      
	private String escapedCollectionPath;
	private String apiName;
	
	//TODO : deprecate when we split at root collection
	public final static String API_XMLRPC = "xmlrpc";
	public final static String API_WEBDAV = "webdav";
	public final static String API_REST = "rest-style";
	public final static String API_LOCAL = "local";	

	/** Contructs an XmldbURI from given string.
	 * Note that we construct URIs starting with XmldbURI.XMLDB_URI_PREFIX.
	 * Do not forget that org.xmldb.api.DatabaseManager <strong>trims</strong> this prefix. 
	 * @param xmldbURI A string 
	 * @throws URISyntaxException If the given string is not a valid xmldb URI.
	 */
	public XmldbURI(String xmldbURI) throws URISyntaxException {
    	try {    		 		
    		parseURI(xmldbURI);
    	} catch (URISyntaxException e) {
        	wrappedURI = null;        	
        	throw e;     	
    	}
	}
	
	/** Contructs an XmldbURI from the given string, handling the necessary escapings.
	 * @param accessURI
	 * @param collection An unescaped collection path.
	 * @throws URISyntaxException
	 */
	public XmldbURI(String accessURI, String collectionPath) throws URISyntaxException {
    	try {
            String escaped = URIUtils.iriToURI(collectionPath);   		
 			parseURI(accessURI + escaped);
    	} catch (UnsupportedEncodingException e) {
        	wrappedURI = null;        	
        	throw new URISyntaxException(accessURI + collectionPath, e.getMessage());  	
    	}
	}
	
	/** Feeds private members
	 * @throws URISyntaxException
	 */
	private void parseURI(String xmldbURI) throws URISyntaxException {	
		String path = null;	
        URI truncatedURI;        
        wrappedURI = new URI(xmldbURI);   
		//Reinitialise members
		this.instanceName = null;
		this.host = null;
		this.port = NO_PORT;
		this.apiName = null;
		if (wrappedURI.getScheme() == null) { 			
			path = wrappedURI.getPath();			
		}
		else
		{			
			if (!wrappedURI.toString().startsWith(XMLDB_URI_PREFIX))
				throw new URISyntaxException(wrappedURI.toString(), "xmldb URI scheme does not start with " + XMLDB_URI_PREFIX);
			try {
                truncatedURI = new URI(wrappedURI.toString().substring(XMLDB_URI_PREFIX.length()));
			} catch (URISyntaxException e) {
				//Put the "right" URI in the message ;-)
				throw new URISyntaxException(wrappedURI.toString(), e.getMessage());				
			}
	    	if (truncatedURI.getQuery() != null)
	    		//Put the "right" URI in the message ;-)
	    		throw new URISyntaxException(wrappedURI.toString(), "xmldb URI should not provide a query part");
	    	if (truncatedURI.getFragment() != null)
	    		//Put the "right" URI in the message ;-)    		
	    		throw new URISyntaxException(wrappedURI.toString(), "xmldb URI should not provide a fragment part");
	    	//Is an encoded scheme ever possible ?
	    	instanceName = truncatedURI.getScheme();
			if (instanceName == null)   
				//Put the "right" URI in the message ;-)
				throw new URISyntaxException(wrappedURI.toString().toString(), "xmldb URI scheme has no instance name");			
            userInfo = truncatedURI.getUserInfo();
            //Very tricky :
            if (truncatedURI.getHost() == null && truncatedURI.getAuthority() != null) {
                userInfo = truncatedURI.getAuthority();
                if (userInfo.endsWith("@"))
                    userInfo = userInfo.substring(0, userInfo.length() - 1);
            }
            host = truncatedURI.getHost();
			port = truncatedURI.getPort();
			path = truncatedURI.getRawPath();
            //Eventually rewrite wrappedURI *without* user info
            if (userInfo != null) {
                StringBuffer recomputed = new StringBuffer(XMLDB_URI_PREFIX);                
                recomputed.append(truncatedURI.getScheme());
                recomputed.append("://");
                recomputed.append(truncatedURI.getHost());
                if (truncatedURI.getPort() != -1)
                    recomputed.append(":").append(truncatedURI.getPort());                
                recomputed.append(truncatedURI.getPath());
                wrappedURI = new URI(recomputed.toString());                
            } 
		}
		splitPath(path);
	}
	
	/** Given a java.net.URI.getPath(), <strong>tries</strong> to dispatch the host's context
	 * from the collection path as smartly as possible. 
	 * One would probably prefer a split policy based on the presence of a well-known root collection.
	 * @param path The java.net.URI.getPath() provided.
	 * @throws URISyntaxException
	 */
	private void splitPath(String path) throws URISyntaxException {
		int index = -1;
		int lastIndex = -1;	
		//Reinitialise members
		this.context = null;
		this.escapedCollectionPath = null;
		if (path != null) {				
			if (host != null) {  
                //Try to extract the protocol from the provided URI. 
                //TODO : get rid of this and use a more robust approach (dedicated constructor ?) -pb
	    		//TODO : use named constants  
	        	index = path.lastIndexOf("/xmlrpc");        	         	
	        	if (index > lastIndex) {
	        		apiName = API_XMLRPC;        		
	        		escapedCollectionPath = path.substring(index + "/xmlrpc".length());
	        		context = path.substring(0, index) + "/xmlrpc";
	        		lastIndex = index;
	        	}         	
	        	//TODO : use named constants  
	        	index = path.lastIndexOf("/webdav");        	         	
	        	if (index > lastIndex) {
	        		apiName = API_WEBDAV;        		
	        		escapedCollectionPath = path.substring(index + "/webdav".length());
	        		context = path.substring(0, index) + "/webdav";
	        		lastIndex = index;
	        	}    		
	        	//Default : REST-style...
	        	if (apiName == null) {	    			
	        		apiName = API_REST;  
	        		escapedCollectionPath =  path; 	
	    			//TODO : determine the context out of a clean root collection policy.
	    			context = null;	        		        		
	        	}
    		}    	
	        else 
	        {	        	
	        	if (port != NO_PORT)
	        		//Put the "right" URI in the message ;-)
	        		throw new URISyntaxException(wrappedURI.toString(), "Local xmldb URI should not provide a port");
	        	apiName = API_LOCAL;  
	        	context = null;
	        	escapedCollectionPath = path; 	 
	        }
	    	//Trim trailing slash if necessary    	
	    	if (escapedCollectionPath != null && escapedCollectionPath.length() > 1 && escapedCollectionPath.endsWith("/"))    		
	    		escapedCollectionPath = escapedCollectionPath.substring(0, escapedCollectionPath.length() - 1);              	
	    	//TODO : check that collectionPath starts with DBBroker.ROOT_COLLECTION ?					
		}		
		
	}

	/** To be called each time a private member that interacts with the wrapped URI is modified.
	 * @throws URISyntaxException
	 */
	private void recomputeURI() throws URISyntaxException {		
		StringBuffer buf = new StringBuffer();
		if (instanceName != null)	
			buf.append(XMLDB_URI_PREFIX).append(instanceName).append("://");
        //No userInfo
		if (host != null)	
			buf.append(host);				
		if (port != NO_PORT)
			buf.append(":" + port);		
		if (context != null)
			buf.append(context);
		//TODO : eventually use a prepend.root.collection system property 		
		if (escapedCollectionPath != null)
			buf.append(escapedCollectionPath);
		try {
			wrappedURI = new URI(buf.toString());			
    	} catch (URISyntaxException e) {
        	wrappedURI = null;        	
        	throw e; 
    	}			
	}
	
	/** To be called before a context operation with another XmldbURI.
	 * @param uri
	 * @throws IllegalArgumentException
	 */
	private void checkCompatibilityForContextOperation(XmldbURI uri) throws IllegalArgumentException {
		if (this.getInstanceName() != null && uri.getInstanceName() != null
				&& !this.getInstanceName().equals(uri.getInstanceName()))
			throw new IllegalArgumentException(this.getInstanceName() + " instance differs from " + uri.getInstanceName());
		//case insensitive comparison
		if (this.getHost() != null && uri.getHost() != null
				&& !this.getHost().equalsIgnoreCase(uri.getHost()))
			throw new IllegalArgumentException(this.getHost() + " host differs from " + uri.getHost());
		if (this.getPort() != NO_PORT && uri.getPort() != NO_PORT && this.getPort() != uri.getPort())
			throw new IllegalArgumentException(this.getPort() + " port differs from " + uri.getPort());
		if (this.getCollectionPath() != null && uri.getCollectionPath() != null
				&& !this.getCollectionPath().equals(uri.getCollectionPath()))
			throw new IllegalArgumentException(this.getCollectionPath() + " collection differs from " + uri.getCollectionPath());		
	}

	/** To be called before a collection path operation with another XmldbURI.
	 * @param uri
	 * @throws IllegalArgumentException
	 */
	private void checkCompatibilityForCollectionOperation(XmldbURI uri) throws IllegalArgumentException {
		if (this.getInstanceName() != null && uri.getInstanceName() != null
				&& !this.getInstanceName().equals(uri.getInstanceName()))
			throw new IllegalArgumentException(this.getInstanceName() + " instance differs from " + uri.getInstanceName());
		//case insensitive comparison
		if (this.getHost() != null && uri.getHost() != null
				&& !this.getHost().equalsIgnoreCase(uri.getHost()))
			throw new IllegalArgumentException(this.getHost() + " host differs from " + uri.getHost());
		if (this.getPort() != NO_PORT && uri.getPort() != NO_PORT && this.getPort() != uri.getPort())
			throw new IllegalArgumentException(this.getPort() + " port differs from " + uri.getPort());
		if (this.getContext() != null && uri.getContext() != null
				&& !this.getContext().equals(uri.getContext()))
			throw new IllegalArgumentException(this.getContext() + " context differs from " + uri.getContext());		
	}
	
	public void setInstanceName(String instanceName) throws URISyntaxException {		 
		String oldInstanceName = this.instanceName;
		try {
			this.instanceName = instanceName;
			recomputeURI();
		} catch (URISyntaxException e) {
			this.instanceName = oldInstanceName;
			throw e;
		}			
	}
	
    public void setUserInfo(String userInfo) throws URISyntaxException {
        String oldUserInfo = this.userInfo;
        try {
            this.userInfo = userInfo;
            //unnecessary
            recomputeURI();
        } catch (URISyntaxException e) {
            this.userInfo = oldUserInfo;
            throw e;
        }
    }
    
    public void setHost(String host) throws URISyntaxException {
        String oldHost = this.host;
        try {
            this.host = host;
            recomputeURI();
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
			recomputeURI();
		} catch (URISyntaxException e) {
			this.port = oldPort;
			throw e;
		}
	}
	
	public void setContext(String context) throws URISyntaxException {
		String oldContext = this.context;
		try {
			//trims any trailing slash 
	    	if (context != null && context.endsWith("/")) {   		
	    		//include root slash if we have a host
	    		if (this.getHost() != null)
	    			context = context.substring(0, context.length() - 1); 
	    	}
			this.context = "".equals(context) ? null : context;
			recomputeURI();
		} catch (URISyntaxException e) {
			this.context = oldContext;
			throw e;
		}
	}
	
	public void setContext(URI context) throws URISyntaxException {
		String str = context.toString();
		setContext(str);		
	}	
	
	public void setCollectionPath(String collectionPath) throws URISyntaxException {
		String oldCollectionPath = collectionPath;
		try {
			if (collectionPath == null)
				this.escapedCollectionPath = null;
			else {
                String escaped = URIUtils.escapeHtmlURI(collectionPath); 				
				this.escapedCollectionPath = escaped;
			}
			recomputeURI();
		} catch (URISyntaxException e) {
			this.escapedCollectionPath = oldCollectionPath;
			throw e;
    	} catch (UnsupportedEncodingException e) {
        	wrappedURI = null;        	
        	throw new URISyntaxException(this.toString(), e.getMessage());  	
    	}
	}
	
	public void setCollectionPath(URI collectionPath) throws URISyntaxException {
		String str = context.toString();
		setCollectionPath(str);			
	}
	
	public URI getURI() { 			
		return wrappedURI; 
	}
	
	public String getInstanceName() {		
		return instanceName; 
	}
    
    public String getUserInfo() {       
        return userInfo; 
    }    
	
    public String getHost() {       
        return host; 
    }
    
	public int getPort() {		
		return port; 
	}
	/**
	 * Method to return the collection path with reserved characters 
	 * percent encoded
	 * 
	 * @return Returns the encoded collection path
	 */
	public String getRawCollectionPath() {
		return escapedCollectionPath;
	}
	public String getCollectionPath() {
		if (escapedCollectionPath == null)
			return null;
		try {
			return URLDecoder.decode(escapedCollectionPath, "UTF-8"); 
		} catch (UnsupportedEncodingException e) {
			//Should never happen
			throw new IllegalArgumentException(escapedCollectionPath + " can not be properly escaped");
		}
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
	
	public static XmldbURI create(String accessURI, String collectionPath) {		
		try {
			return new XmldbURI(accessURI, collectionPath);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
	}	

	/** Ugly workaround for non-URI compliant pathes
	 * @param pseudoURI What is supposed to be a URI
	 * @return an supposedly correctly escaped URI <strong>string representation</string>
     * @deprecated By definition, using this method is strongly discouraged
	 */
	public static String recoverPseudoURIs(String pseudoURI) throws URISyntaxException {		
		Pattern p = Pattern.compile("/");
		String[] parts = p.split(pseudoURI);
		StringBuffer newURIString = new StringBuffer(parts[0]);
		for (int i = 1 ; i <parts.length; i ++) { 
            newURIString.append("/");
			if (!"".equals(parts[i])) {
	    		try {
                    //Try to instantiate the parst as a URI
	    			new URI(newURIString + parts[i]); 
                    newURIString.append(parts[i]);
	    		} catch (URISyntaxException e) {		    			
                    LOG.info("Trying to escape : ''" + parts[i] + "' in '" + pseudoURI + "' !");                   		
	    			try {                        
	    				newURIString.append(URIUtils.encodeForURI(parts[i]));
	    			} catch (UnsupportedEncodingException ee) {	    				
		    			LOG.warn("Can't do anything with : ''" + parts[i] + "' in '" + pseudoURI + "' !");    	
	    				throw new URISyntaxException(pseudoURI, e.getMessage());
	    			}
	    		}
			}			
		}
		return newURIString.toString();
	}
	
	public boolean equals(Object ob) {
		if (!(ob instanceof XmldbURI))
			return false;
		return wrappedURI.equals(((XmldbURI)ob).getURI());
	}	
	
	public boolean isAbsolute() {	
		return wrappedURI.isAbsolute();
	}
	
	public boolean isContextAbsolute() {
		String context = this.getContext();
		if (context == null)
			return true;
		return context.startsWith("/");
	}
	
	public XmldbURI normalizeContext() {			
		String context = this.getContext();
		if (context == null)
			return this;
		URI uri = URI.create(context);		
		try {
			XmldbURI xmldbURI = new XmldbURI(this.toString());
			xmldbURI.setContext((uri.normalize()).toString());
			return xmldbURI;
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
	}	
	
	public URI relativizeContext(URI uri) {
		if (uri == null)
			throw new NullPointerException("The provided URI is null");			
		String context = this.getContext();
		if (context == null)
			throw new NullPointerException("The current context is null");		
		URI contextURI;
		//Adds a final slash if necessary
		if (!context.endsWith("/")) {
            LOG.info("Added a final '/' to '" + context + "'"); 
			contextURI = URI.create(context + "/");
        } else
			contextURI = URI.create(context);		
		return contextURI.relativize(uri);	
	}
	
	public URI resolveContext(String str) throws NullPointerException, IllegalArgumentException {	
		if (str == null)
			throw new NullPointerException("The provided URI is null");		
		String context = this.getContext();
		if (context == null)
			throw new NullPointerException("The current context is null");
		URI contextURI;
		//Adds a final slash if necessary
		if (!context.endsWith("/")) {
            LOG.info("Added a final '/' to '" + context + "'");  
			contextURI = URI.create(context + "/");
        } else
			contextURI = URI.create(context);		
		return contextURI.resolve(str);	
	}
	
	public URI resolveContext(URI uri) throws NullPointerException {	
		if (uri == null)
			throw new NullPointerException("The provided URI is null");		
		String context = this.getContext();
		if (context == null)
			throw new NullPointerException("The current context is null");	
		URI contextURI;
		//Adds a final slash if necessary
		if (!context.endsWith("/")) {
            LOG.info("Added a final '/' to '" + context + "'"); 
			contextURI = URI.create(context + "/");
        } else
			contextURI = URI.create(context);		
		return contextURI.resolve(uri);	
	}
	
	public boolean isCollectionPathAbsolute() {
		String collectionPath = this.escapedCollectionPath;
		if (collectionPath == null)
			return true;
		return collectionPath.startsWith("/");
	}
	
	public XmldbURI normalizeCollectionPath() {			
		String collectionPath = this.escapedCollectionPath;
		if (collectionPath == null)
			return this;
		URI collectionPathURI = URI.create(collectionPath);	
		try {
			XmldbURI xmldbURI = new XmldbURI(this.toString());
			xmldbURI.setCollectionPath(collectionPathURI.normalize().toString());
			return xmldbURI;
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e.getMessage());
		}
	}	

	public URI relativizeCollectionPath(URI uri) {
		if (uri == null)
			throw new NullPointerException("The provided URI is null");			
		String collectionPath = this.escapedCollectionPath;
		if (collectionPath == null)
			throw new NullPointerException("The current collection path is null");		
		URI collectionPathURI;
		//Adds a final slash if necessary
		if (!collectionPath.endsWith("/")) {
            LOG.info("Added a final '/' to '" + collectionPath + "'"); 
			collectionPathURI = URI.create(collectionPath + "/");
        } else
			collectionPathURI = URI.create(collectionPath);
		return collectionPathURI.relativize(uri);	
	}
	
	public URI resolveCollectionPath(String str) throws NullPointerException, IllegalArgumentException {	
		if (str == null)
			throw new NullPointerException("The provided URI is null");		
		String collectionPath = this.escapedCollectionPath;
		if (collectionPath == null)
			throw new NullPointerException("The current collection path is null");	
		URI collectionPathURI;
		//Adds a final slash if necessary
		if (!collectionPath.endsWith("/")) {
            LOG.info("Added a final '/' to '" + collectionPath + "'"); 
			collectionPathURI = URI.create(collectionPath + "/");
        } else
			collectionPathURI = URI.create(collectionPath);
		return collectionPathURI.resolve(str);	
	}
	
	public URI resolveCollectionPath(URI uri) throws NullPointerException {	
		if (uri == null)
			throw new NullPointerException("The provided URI is null");		
		String collectionPath = this.escapedCollectionPath;
		if (collectionPath == null)
			throw new NullPointerException("The current collection path is null");
		URI collectionPathURI;
		//Adds a final slash if necessary
		if (!collectionPath.endsWith("/")) {
            LOG.info("Added a final '/' to '" + collectionPath + "'"); 
			collectionPathURI = URI.create(collectionPath + "/");
        } else
			collectionPathURI = URI.create(collectionPath);
		return collectionPathURI.resolve(uri);	
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
    
    public static String[] getPathComponents(String collectionPath) {       
        Pattern p = Pattern.compile("/");
        String [] split = p.split(collectionPath);
        String [] result = new String[split.length - 1];
        System.arraycopy(split, 1, result, 0, split.length - 1);
        return result;       
    }    
	
    /* @deprecated Legacy method used here and there in the code
     * if the currentPath is null return the parentPath else 
     * if the currentPath doesnt not start with "/db/" and is not equal to "/db" then adjust the path to start with the parentPath
     * 
     * Fix to Jens collection/resource name problem by deliriumsky
     * 
     * @deprecated Use {@link #resolveCollectionPath(String) resolveCollectionPath} instead
     */
    public static String checkPath(String currentPath, String parentPath)
	{
    	if(currentPath == null)
            return parentPath;	
    	//Absolute path
        if (DBBroker.ROOT_COLLECTION.equals(currentPath))
            return currentPath;
        //Absolute path
		if (currentPath.startsWith(DBBroker.ROOT_COLLECTION + "/"))
            return currentPath;
        
        //Kind of relative path : against all conventions ! -pb
		if(currentPath.startsWith("/"))
            LOG.warn("Initial '/' for relative path '" + currentPath + "'"); 
		
		//OK : let's process this so-called relative path
        if (currentPath.startsWith("/")) {
            if (parentPath.endsWith("/"))            
                return  parentPath + currentPath.substring(1);
             return  parentPath + currentPath;
        }
        //True relative pathes
        if (parentPath.endsWith("/"))            
            return  parentPath + currentPath;
        return  parentPath + "/" + currentPath;
	}	
    
    
    /** @deprecated Legacy method used here and there in the code
     * @param fileName
     * @param parentPath
     * @return
     */
    public static String checkPath2(String fileName, String parentPath) {
        //if (!fileName.startsWith("/"))
        //    fileName = "/" + fileName;
        /*if (!fileName.startsWith(ROOT_COLLECTION))
            fileName = ROOT_COLLECTION + fileName;*/
        
        return checkPath(fileName, parentPath);
    }    
   
    /**@deprecated Legacy method used here and there in the code and copied as such
     * @param name
     * @return
     */
    //TODO : changes // into /  */
    public final static String normalizeCollectionName(String name) {
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < name.length(); i++)
            //TODO : use dedicated function in XmldbURI
            if (name.charAt(i) == '/'
                && name.length() > i + 1
                && name.charAt(i + 1) == '/')
                i++;
            else
                out.append(name.charAt(i));

        String name2 = out.toString();
        if (name2.length() > 0 && name2.charAt(0) != '/')
            name2 = "/" + name2;

        if (!name2.startsWith(DBBroker.ROOT_COLLECTION))
            name2 = DBBroker.ROOT_COLLECTION + name2;

        if (name2.endsWith("/") && name2.length() > 1)
            name2 = name2.substring(0, name2.length() - 1); 
        
        return name2;
        
    }    
    


	
//	TODO : prefefined URIs as static classes...
    
}
