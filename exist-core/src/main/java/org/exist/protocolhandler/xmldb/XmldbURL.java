/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.protocolhandler.xmldb;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.exist.xmldb.XmldbURI;

/**
 *  A utility class for xmldb URLs. Since, java.net.URL is final this class
 * acts as a wrapper, convenience methods have been added.<BR>
 * <BR>
 * Example:<BR>
 * <I>xmldb:exist://username:password@hostname:8080/exist/xmlrpc/db/collection/document.xml</I><BR>
 * <BR>
 * Note: A collection URL ends with a "/":<BR>
 * <I>xmldb:exist://hostname:8080/exist/xmlrpc/db/collection/</I>
 *
 * @see java.net.URI
 * @see java.net.URL
 * @see org.exist.xmldb.XmldbURI
 *
 * @author Dannes Wessels
 */
public class XmldbURL {
    
    private static final int USERNAME=1;
    private static final int PASSWORD=2;
    
    private URL myUrl;
    
    /**
     * Creates a new instance of XmldbURL using an XmldbURI object.
     *
     * @param xmldbURI Resource location.
     * @throws java.net.MalformedURLException URL is not correct.
     */
    public XmldbURL(XmldbURI xmldbURI) throws MalformedURLException {
        this(xmldbURI.toURL());
    }
    
    /**
     * Creates a new instance of XmldbURL using an URL object.
     * @param url Resource location.
     * @throws java.net.MalformedURLException URL is not correct.
     */
    public XmldbURL(URL url) throws MalformedURLException  {
        // check protocol
        if("xmldb".equals(url.getProtocol())){
            myUrl = url;
        } else {
            throw new MalformedURLException("URL is not an \"xmldb:\" URL: "+ url);
        }
    }
    
    /**
     * Creates a new instance of XmldbURL using an URI object.
     *
     * @param uri Resource location.
     * @throws java.net.MalformedURLException URL is not correct.
     */
    public XmldbURL(URI uri) throws MalformedURLException  {
        this(uri.toURL());
    }
    
    /**
     * Creates a new instance of XmldbURL using an String.
     * @param txt Resource location.
     * @throws java.net.MalformedURLException URL is not correct.
     */
    public XmldbURL(String txt) throws MalformedURLException {
        this(new URL(txt));
    }
    
    /**
     * xmldb:exist://<B>username:password</B>@hostname:8080/exist/xmlrpc/db/collection/document.xml
     * @see java.net.URL#getUserInfo
     *
     * @return username:password
     */
    public String getUserInfo() {
        return myUrl.getUserInfo();
    }
    
    /**
     * xmldb:exist://<B>username</B>:password@hostname:8080/exist/xmlrpc/db/collection/document.xml
     * @return username
     */
    public String getUsername(){
        return extractCredentials(USERNAME);
    }
    
    /**
     * xmldb:exist://username:<B>password</B>@hostname:8080/exist/xmlrpc/db/collection/document.xml
     * @return password
     */
    public String getPassword(){
        return extractCredentials(PASSWORD);
    }
    
    /**
     * @return URL representation of location.
     */
    public URL getURL(){
        return myUrl;
    }
    
    /**
     * xmldb:exist://<B>username:password@hostname:8080/exist/xmlrpc/db/collection/document.xml</B>?query#fragment
     * @see java.net.URL#getAuthority
     * @return authority
     */
    public String getAuthority() {
        return myUrl.getAuthority();
    }
    
    /**
     * xmldb:exist://username:password@hostname:8080<B>/exist/xmlrpc</B>/db/collection/document.xml?query#fragment
     * @return context, null if not available.
     */
    public String getContext() {
        final String path = myUrl.getPath();
        final int dbPosition=path.indexOf("/db");
        String context=null;
        
        if(dbPosition!=-1){
            // since all paths begin with this pattern..
            context=path.substring(0,dbPosition);
        } 
        
        if(context!=null && context.isEmpty()){
            context=null;
        }
        
        return context;
    }
    
    // /exist/xmlrpc/db/shakespeare/plays/macbeth.xml
    // /exist/xmlrpc/db/shakespeare/plays/
    // /db/shakespeare/plays/macbeth.xml
    // /db/shakespeare/plays/
    
    
    /**
     * xmldb:exist://username:password@hostname:8080/exist/xmlrpc<B>/db/collection</B>/document.xml
     * @return collection
     */
    public String getCollection(){
        
        String path=myUrl.getPath();
        String collectionName=null;
        
        final int dbLocation=path.indexOf("/db");
        
        if(dbLocation!=-1){
            // found pattern "/db"
            if(path.endsWith("/")){
                // -1 removes the slash
                collectionName=path.substring(dbLocation, (path.length()-1) );
            } else {
                final int lastSep=path.lastIndexOf('/');
                if(lastSep==0){
                    collectionName="/";
                    
                } else if(lastSep!=-1){
                    collectionName=path.substring(dbLocation, lastSep);
                    
                } else {
                    collectionName=path;
                }
            }
            
        } else {  // TODO not very well tested
            // pattern not found, taking full path
            if(path.endsWith("/")){
                // -1 removes the slash
                collectionName=path.substring(0, (path.length()-1) );
            } else {
                final int lastSep=path.lastIndexOf('/');
                if(lastSep!=-1){
                    collectionName=path.substring(0, lastSep);
                } else {
                    collectionName="/";
                }
            }
        }
        
        return collectionName;
    }
    
    /**
     * xmldb:exist://username:password@hostname:8080/exist/xmlrpc/db/collection/<B>document.xml</B>
     * @return collection
     */
    public String getDocumentName(){
        String serverPath=myUrl.getPath();
        String documentName=null;
        if(!serverPath.endsWith("/")){
            final int lastSep=serverPath.lastIndexOf('/');
            if(lastSep==-1){
                documentName=serverPath;
            } else {
                documentName=serverPath.substring(lastSep+1);
            }
        }
        return documentName;
    }
    
    // Get username or password
    private String extractCredentials(int part) {
        
        String userInfo = myUrl.getUserInfo();
        String username = null;
        String password = null;
        
        if(userInfo!=null){
            final int separator = userInfo.indexOf(':');
            if(separator==-1){
                username=userInfo;
                password=null;
            } else {
                username=userInfo.substring(0,separator);
                password=userInfo.substring(separator+1);
            }
        }
        
        // Fix credentials. If not found (empty string) fill NULL
        if(username!=null && username.isEmpty()){
            username=null;
        }
        
        // Fix credentials. If not found (empty string) fill NULL
        if(password!=null && password.isEmpty()){
            password=null;
        }
        
        if(part==USERNAME){
            return username;
        } else if(part==PASSWORD){
            return password;
        }
        return null;
    }
    
    /**
     * <B>xmldb</B>:exist://username:password@hostname:8080/exist/xmlrpc/db/collection/document.xml
     * @see java.net.URL#getProtocol
     * @return protocol
     */
    public String getProtocol(){
        return myUrl.getProtocol();
    }
    
    /**
     * xmldb:exist://username:password@<B>hostname</B>:8080/exist/xmlrpc/db/collection/document.xml
     * @see java.net.URL#getProtocol
     * @return protocol
     */
    public String getHost(){
        final String hostname=myUrl.getHost();
        if(hostname != null && hostname.isEmpty()){
            return null;
        } else {
            return hostname;
        }
    }
    
    /**
     * xmldb:exist://username:password@hostname:<B>8080</B>/exist/xmlrpc/db/collection/document.xml
     * @see java.net.URL#getPort
     * @return port
     */
    public int getPort(){
        return myUrl.getPort();
    }
    
    /**
     * xmldb:exist://username:password@hostname:8080:<B>/exist/xmlrpc/db/collection/document.xml</B>
     * @see java.net.URL#getPath
     * @return port
     */
    public String getPath(){
        return myUrl.getPath();
    }
    
    /**
     * xmldb:exist://username:password@hostname:8080/exist/xmlrpc/db/collection/document.xml?<B>query</B>#fragment
     * @see java.net.URL#getQuery
     * @return query
     */
    public String getQuery(){
        return myUrl.getQuery();
    }
    
    /**
     * xmldb:exist://username:password@hostname:8080:/exist/xmlrpc<B>/db/collection/document.xml</B>
     * @return collectionpath
     */
    public String getCollectionPath(){
        return myUrl.getPath().substring(13);
    }
    
    /**
     * Get http:// URL from xmldb:exist:// URL
     * xmldb:exist://username:password@hostname:8080:/exist/xmlrpc/db/collection/document.xml
     * @return http://username:password@hostname:8080:/exist/xmlrpc/db/collection/document.xml
     */
    public String getXmlRpcURL(){
        return "http://" + myUrl.getAuthority() + getContext();
    }
    
    /**
     * Does the URL have at least a username?
     * @return TRUE when URL contains username
     */
    public boolean hasUserInfo(){
        return (getUserInfo()!=null && getUsername()!=null);
    }
    
    /**
     * Get eXist instance name.
     *
     * @return eXist-db instance name, at this moment fixed to exist
     */
    public String getInstanceName() {
        return "exist";  // No other choice
    }
    
    /**
     * Get textual representation of URL.
     *
     * @see java.net.URL#toString
     * @return Text representation of URL.
     */
    public String toString(){
        return myUrl.toString();
    }
    
    /**
     * Get information wether URL is an embedded URL.
     *
     * @return TRUE when URL refers to resource in embedded eXist-db.
     */
    public boolean isEmbedded(){
        return (getHost()==null);
    }
}
