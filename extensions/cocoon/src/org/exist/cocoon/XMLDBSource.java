/*
 * Extended and slightly adopted version of the original XMLDBSource found in Apache Cocoon.
 * The original license is:
 *
 *  Copyright 1999-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *  
 *  $Id$
 */
package org.exist.cocoon;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.ArrayList;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.logger.Logger;
import org.apache.cocoon.CascadingIOException;
import org.apache.cocoon.xml.IncludeXMLConsumer;
import org.apache.excalibur.source.ModifiableTraversableSource;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceException;
import org.apache.excalibur.source.SourceNotFoundException;
import org.apache.excalibur.source.SourceUtil;
import org.apache.excalibur.source.SourceValidity;
import org.apache.excalibur.source.impl.validity.TimeStampValidity;
import org.apache.excalibur.xml.sax.XMLizable;
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.ExtendedResource;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

/**
 * This class implements the xmldb:// pseudo-protocol and allows to get XML
 * content from an XML:DB enabled XML database.
 *
 * <p>
 * This class starts on 
 * <a 
 *  href="http://svn.apache.org/viewcvs.cgi/cocoon/blocks/xmldb/trunk/java/org/apache/cocoon/components/source/impl/XMLDBSource.java?rev=359757&view=markup"/
 * >XMLDBSource</a> from Cocoon project.
 * Some improvments may be useful for Cocoon, some others may be Exist specific.
 * Interesting new features :
 * </p>
 * 
 *  <ul>
 *   <li>cacheable for a resource read</li>
 *   <li>handle userinfo urls like <...//user:password@host/...> (for read)</li> 
 *   <li>setContentAsDOM() to avoid encodings problem when writing a resource</li>
 *   <li>resourceToSAX() plug a lexical handler to get comments of the XML document</li>
 *   <li>getCollection(), getResource() (xmldb objects)</li>
 *   <li>getUser(), getPassword()</li>
 *  </ul>
 * 
 * @version CVS $Id$
 */
public class XMLDBSource extends AbstractLogEnabled
    implements ModifiableTraversableSource, XMLizable {

    //
    // Static Strings used for XML Collection representation
    //

    /** Source namespace */
    public static final String URI = "http://apache.org/cocoon/xmldb/1.0";

    /** Source prefix */
    public static final String PREFIX = "db";

    /** Root element <code>&lt;collections&gt;</code> */
    protected static final String COLLECTIONS  = "collections";
    /** Root element <code>&lt;xmldb:collections&gt;</code> (raw name) */
    protected static final String QCOLLECTIONS  = PREFIX + ":" + COLLECTIONS;
    /** Attribute <code>resources</code> on the root element indicates count of resources in the collection */
    protected static final String RESOURCE_COUNT_ATTR = "resources";
    /** Attribute <code>collections</code> on the root element indicates count of collections in the collection */
    protected static final String COLLECTION_COUNT_ATTR  = "collections";
    protected static final String COLLECTION_BASE_ATTR  = "base";

    /** Element <code>&lt;collection&gt;</code> */
    protected static final String COLLECTION  = "collection";
    /** Element <code>&lt;xmldb:collection&gt;</code> (raw name) */
    protected static final String QCOLLECTION  = PREFIX + ":" + COLLECTION;

    /** Element <code>&lt;resource&gt;</code> */
    protected static final String RESOURCE  = "resource";
    /** Element <code>&lt;resource&gt;</code> (raw name) */
    protected static final String QRESOURCE  = PREFIX + ":" + RESOURCE;
    /** Attribute <code>name</code> on the collection/resource element */
    protected static final String NAME_ATTR  = "name";

    /** Root element <code>&lt;results&gt;</code> */
    protected static final String RESULTSET = "results";
    /** Root element <code>&lt;xmldb:results&gt;</code> (raw name) */
    protected static final String QRESULTSET = PREFIX + ":" + RESULTSET;
    protected static final String QUERY_ATTR = "query";
    protected static final String RESULTS_COUNT_ATTR = "resources";

    /** Element <code>&lt;result&gt;</code> */
    protected static final String RESULT = "result";
    /** Element <code>&lt;xmldb:result&gt;</code> (raw name) */
    protected static final String QRESULT = PREFIX + ":" + RESULT;
    protected static final String RESULT_DOCID_ATTR = "docid";
    protected static final String RESULT_ID_ATTR = "id";

    protected static final String CDATA  = "CDATA";

    //
    // Instance variables
    //
    /** default encoding to write outputStream */
    public String encoding="UTF-8";
    
    /** The requested URL */
    public String url;

    /** The supplied user */
    public String user;

    /** The supplied password */
    public String password;

    /** The part of URL after # sign */
    protected String query;

    /** The System ID */
    protected String systemId;

    /** The path for the collection (same as url if it's a collection) */
    private final String colPath;
    
    /** The name of the resource in the collection (null if a collection) */
    private String resName;
    
    public Collection collection;
    
    public Resource resource;
    
    private static final int ST_UNKNOWN = 0;
    private static final int ST_COLLECTION = 1;
    private static final int ST_RESOURCE = 2;
    private static final int ST_NO_PARENT = 3;
    private static final int ST_NO_RESOURCE = 4;

    private int status = ST_UNKNOWN;

    /**
     * The constructor.
     *
     * @param logger the Logger instance.
     * @param user
     * @param password
     * @param srcUrl the URL being queried.
     */
    public XMLDBSource(Logger logger,
                       String user, String password,
                       String srcUrl) {
        enableLogging(logger);

        this.user = user;
        this.password = password;

        // Parse URL (with String methods for efficiency and tolerance)

        // Exist improvment, handle user:pass override from URL
        
        // default behavior
        this.url = srcUrl;
        // allow little queries
        int sharp = this.url.indexOf('#');
        if (sharp != -1) {
            this.url = this.url.substring(0, sharp);
            this.query = this.url.substring(sharp + 1);
            if (query.length() == 0) query = null;
        }

        // try userinfo, a '@' before #
        // alow things like xmldb:exist://user:password@*/**
        int at=this.url.indexOf('@');
        int root=this.url.indexOf("//");
        if (at > -1 && (sharp == -1 || at < sharp) ) {
            // take userinfo first and modify url after
            String userinfo=this.url.substring(root + 2, at);
            this.url=this.url.substring(0, root+2) + this.url.substring(at + 1);
            int column=userinfo.indexOf(":");
            if (column != -1) {
                this.user=userinfo.substring(0, column);
                this.password=userinfo.substring(column + 1);
            } else {
                this.user=userinfo;
            }
        }
        
        // Split path in collection and resource
        if (this.url.endsWith("/")) {
            this.url = this.url.substring(0, this.url.length() - 1);
        }
        // [giulio] 
        // [FG] commented till Exist is able to work without URI encoding, to keep compatibility with other possible xmldb
        // url = url.replaceAll(" ", "%20");
        int pos = this.url.lastIndexOf('/');
        colPath = this.url.substring(0, pos);
        resName = this.url.substring(pos + 1);
    }
    
    private void setup() throws XMLDBException, SourceException {
        status = ST_UNKNOWN;
        try {
            collection = DatabaseManager.getCollection(url, user, password);
            if (collection != null) {
                status = ST_COLLECTION;
                return;
            }
            
            // That may be a resource: get the parent collection
            collection = DatabaseManager.getCollection(colPath, user, password);
            if (collection == null) {
                // Even parent is unknown
                status = ST_NO_PARENT;
            } else {
                resource = collection.getResource(resName);
                if (resource != null) {
                    // A resource
                    status = ST_RESOURCE;
                } else {
                    status = ST_NO_RESOURCE;
                }
            }
        } finally {
            if (status == ST_UNKNOWN) {
                // Something went wrong: ensure any collection is closed
                cleanup();
            }
        }
    }
    
    private void cleanup() {
        close(this.collection);
    }

    private Collection createCollection(String path) throws XMLDBException, SourceException {
        Collection coll = DatabaseManager.getCollection(path, this.user, this.password);
        if (coll != null) {
            return coll;
        }
        // Need to create the collection

        // Remove any trailing '/'
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        int pos = path.lastIndexOf('/');
        if (pos == -1) {
            throw new SourceException("Invalid collection path " + path);
        }
        // Recurse
        Collection parentColl = createCollection(path.substring(0, pos));
        
        // And create the child collection
        CollectionManagementService mgtService = (CollectionManagementService)
            parentColl.getService("CollectionManagementService", "1.0");
        coll = mgtService.createCollection(path.substring(pos+1));

        return coll;
    }
    
    
    /**
     * Close an XMLDB collection, ignoring any exception
     */
    private void close(Collection coll) {
        if (coll != null) {
            try {
                coll.close();
            } catch (XMLDBException e) {
                // ignore;
            }
        }
    }
    /** A getter for the xmldb Collection object for this Source */
    public Collection getCollection() throws SourceException, XMLDBException {
        try {
            setup();
            return this.collection;
        } finally {
            cleanup();
        }
    }
    /** A getter for the xmldb Resource object for this Source */
    public Resource getResource() throws SourceException, XMLDBException {
        try {
            setup();
            return this.resource;
        } finally {
            cleanup();
        }
    }
    /** A getter for the user login configured in cocoon.xconf */
    public String getUser() {
        return this.user;
    }
    /** A getter for the password configured in cocoon.xconf for the connection */
    public String getPassword() {
        return this.password;
    }
    /**
     * Stream SAX events to a given ContentHandler. If the requested
     * resource is a collection, build an XML view of it.
     */
    public void toSAX(ContentHandler handler) throws SAXException {
        try {
            setup();
            if (status == ST_COLLECTION) {
                this.collectionToSAX(handler);
            } else if (status == ST_RESOURCE) {
                this.resourceToSAX(handler);
            } else {
                throw new SourceNotFoundException(getURI());
            }
        } catch (SAXException se) {
            throw se;
        } catch (Exception e) {
            throw new SAXException("Error processing " + getURI(), e);
        } finally {
            cleanup();
        }
    }
    
    private void resourceToSAX(ContentHandler handler)
        throws SAXException, XMLDBException, SourceException {

        if (!(resource instanceof XMLResource)) {
            throw new SAXException("Not an XML resource: " + getURI());
        }
        
        if (query != null) {
            // Query resource
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Querying resource " + resName + " from collection " + url + "; query= " + this.query);
            }

            queryToSAX(handler, collection, resName);
        } else {
            // Return entire resource
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Obtaining resource " + resName + " from collection " + colPath);
            }
            // <frederic.glorieux@ajlsm.com> exist specific improvements
            if (resource instanceof  org.exist.xmldb.EXistResource ) {
                // To output comments
                ((org.exist.xmldb.EXistResource)resource).setLexicalHandler((LexicalHandler)handler);
            }
            ((XMLResource)resource).getContentAsSAX(handler);
        }
    }

    private void collectionToSAX(ContentHandler handler)
        throws SAXException, XMLDBException {
        
        AttributesImpl attributes = new AttributesImpl();

        if (query != null) {
            // Query collection
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Querying collection " + url + "; query= " + this.query);
            }

            queryToSAX(handler, collection, null);
        } else {
            // List collection
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Listing collection " + url);
            }

            final String nresources = Integer.toString(collection.getResourceCount());
            attributes.addAttribute("", RESOURCE_COUNT_ATTR,
                                    RESOURCE_COUNT_ATTR, "CDATA", nresources);
            final String ncollections = Integer.toString(collection.getChildCollectionCount());
            attributes.addAttribute("", COLLECTION_COUNT_ATTR,
                                    COLLECTION_COUNT_ATTR, "CDATA", ncollections);
            attributes.addAttribute("", COLLECTION_BASE_ATTR,
                                    COLLECTION_BASE_ATTR, "CDATA", url);

            handler.startDocument();
            handler.startPrefixMapping(PREFIX, URI);
            handler.startElement(URI, COLLECTIONS, QCOLLECTIONS, attributes);

            // Print child collections
            String[] collections = collection.listChildCollections();
            for (int i = 0; i < collections.length; i++) {
                attributes.clear();
                attributes.addAttribute("", NAME_ATTR, NAME_ATTR, CDATA, collections[i]);
                handler.startElement(URI, COLLECTION, QCOLLECTION, attributes);
                handler.endElement(URI, COLLECTION, QCOLLECTION);
            }

            // Print child resources
            String[] resources = collection.listResources();
            for (int i = 0; i < resources.length; i++) {
                attributes.clear();
                attributes.addAttribute("", NAME_ATTR, NAME_ATTR, CDATA, resources[i]);
                handler.startElement(URI, RESOURCE, QRESOURCE, attributes);
                handler.endElement(URI, RESOURCE, QRESOURCE);
            }

            handler.endElement(URI, COLLECTIONS, QCOLLECTIONS);
            handler.endPrefixMapping(PREFIX);
            handler.endDocument();
        }
    }

    private void queryToSAX(ContentHandler handler, Collection collection, String resource)
        throws SAXException, XMLDBException {

        AttributesImpl attributes = new AttributesImpl();

        XPathQueryService service =
                (XPathQueryService) collection.getService("XPathQueryService", "1.0");
        ResourceSet resultSet = (resource == null) ?
                service.query(query) : service.queryResource(resource, query);

        attributes.addAttribute("", QUERY_ATTR, QUERY_ATTR, "CDATA", query);
        attributes.addAttribute("", RESULTS_COUNT_ATTR,
                                RESULTS_COUNT_ATTR, "CDATA", Long.toString(resultSet.getSize()));

        handler.startDocument();
        handler.startPrefixMapping(PREFIX, URI);
        handler.startElement(URI, RESULTSET, QRESULTSET, attributes);

        IncludeXMLConsumer includeHandler = new IncludeXMLConsumer(handler);

        // Print search results
        ResourceIterator results = resultSet.getIterator();
        while (results.hasMoreResources()) {
            XMLResource result = (XMLResource)results.nextResource();

            final String id = result.getId();
            final String documentId = result.getDocumentId();

            attributes.clear();
            if (id != null) {
                attributes.addAttribute("", RESULT_ID_ATTR, RESULT_ID_ATTR,
                                        CDATA, id);
            }
            if (documentId != null) {
                attributes.addAttribute("", RESULT_DOCID_ATTR, RESULT_DOCID_ATTR,
                                        CDATA, documentId);
            }

            handler.startElement(URI, RESULT, QRESULT, attributes);
            try {
                result.getContentAsSAX(includeHandler);
            } catch(XMLDBException xde) {
                // That may be a text-only result
                Object content = result.getContent();
                if (content instanceof String) {
                    String text = (String)content;
                    handler.characters(text.toCharArray(), 0, text.length());
                } else {
                    // Cannot do better
                    throw xde;
                }
            }
            handler.endElement(URI, RESULT, QRESULT);
        }

        handler.endElement(URI, RESULTSET, QRESULTSET);
        handler.endPrefixMapping(PREFIX);
        handler.endDocument();
    }

    public String getURI() {
        return url;
    }

    public long getContentLength() {        
    	long result=-1;
    	try {
    		setup();
    		result = new Integer(((EXistResource)this.resource).getContentLength()).longValue();
          } catch (Exception e){
         	if (getLogger().isDebugEnabled()) {
                getLogger().debug("getContentLength() for " + resName + " from collection " + url + " failed: " + e.getMessage());
            }
          }
          finally {
              cleanup();
          }
          return result;
    }

    public long getLastModified() {
    	long result=0;
        try {
        	setup();
            if (this.status == ST_COLLECTION){
                result = ((CollectionImpl)this.collection).getCreationTime().getTime();
            }else if(this.status == ST_RESOURCE){
                result = ((EXistResource)this.resource).getLastModificationTime().getTime();
            }
        } catch (Exception e){
         	if (getLogger().isDebugEnabled()) {
                getLogger().debug("getLastModified() for " + resName + " from collection " + url + " failed: " + e.getMessage());
            }
        }
        finally {
        	cleanup();
        }
        return result;
    }

    public boolean exists() {
        try {
            setup();
            return status == ST_COLLECTION || status == ST_RESOURCE;
        } catch (Exception e) {
            return false;
        } finally {
            cleanup();
        }
    }

    public String getMimeType() {
        return null;
    }

    public String getScheme() {
        return SourceUtil.getScheme(url);
    }

    public SourceValidity getValidity() {
        try {
            if (resource == null || collection==null) setup();  
            if (resource != null && resource instanceof  org.exist.xmldb.AbstractEXistResource)
                return new TimeStampValidity(((org.exist.xmldb.AbstractEXistResource)resource).getLastModificationTime().getTime());
            /*
            
            if (collection != null && resource instanceof  org.exist.xmldb.AbstractEXistResource)
                return new TimeStampValidity(((org.exist.xmldb.AbstractEXistResource)resource).getLastModificationTime().getTime());
            */
        } 
        catch (XMLDBException e) {}
        catch (SourceException e) {}
        return null;
    }

    public void refresh() {
    }

    /**
     * Get an InputSource for the given URL.
     */
    public InputStream getInputStream()
    throws IOException {
        
        try {
            setup();
            // Check if it's binary
            if (resource instanceof ExtendedResource) {
            	return ((ExtendedResource)resource).getStreamContent();
            } else {
                // Serialize SAX result
                TransformerFactory tf = TransformerFactory.newInstance();
                TransformerHandler th =
                    ((SAXTransformerFactory) tf).newTransformerHandler();
                ByteArrayOutputStream bOut = new ByteArrayOutputStream();
                StreamResult result = new StreamResult(bOut);
                th.setResult(result);

                toSAX(th);

                return new ByteArrayInputStream(bOut.toByteArray());
            }
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            throw new CascadingIOException("Exception during processing of " + getURI(), e);
        } finally {
            cleanup();
        }
    }

    /**
     * Return an {@link OutputStream} to write to. This method expects an XML document to be
     * written in that stream. To create a binary resource, use {@link #getBinaryOutputStream()}.
     */
    public OutputStream getOutputStream() throws IOException, MalformedURLException {
        if (query != null) {
            throw new MalformedURLException("Cannot modify a resource that includes an XPATH expression");
        }
        return new XMLDBOutputStream(false);
    }

    /**
     * set content as DOM
     * 
     * @see <a href="http://exist.sourceforge.net/api/org/xmldb/api/modules/XMLResource.html#setContentAsDOM(org.w3c.dom.Node)">XMLDB API</a>
     */
    public void setContentAsDOM(Node doc) throws IOException, MalformedURLException {
       // author frederic.glorieux@ajlsm.com
        try {
            if (query != null) {
                throw new MalformedURLException("Cannot modify a resource that includes an XPATH expression");
            }
            setup();
            if (status == ST_NO_PARENT) {
                // If there's no parent collection, create it
                collection = createCollection(colPath);
                status = ST_NO_RESOURCE;
            }
            resource = collection.createResource(this.resName, XMLResource.RESOURCE_TYPE);
            ((XMLResource)resource).setContentAsDOM(doc);
            collection.storeResource(resource);
        } catch (XMLDBException e) {
            String message = "Failed to create resource " + resName + ": " + e.errorCode;
            e.printStackTrace(System.out);
            throw new SourceException(message, e);
        }        
    }

    /**
     * get content as DOM
     * @see <a href="http://exist.sourceforge.net/api/org/xmldb/api/modules/XMLResource.html#setContentAsDOM(org.w3c.dom.Node)">XMLDB API</a>
     */
    public Node getContentAsDOM() throws IOException, MalformedURLException {
        try {
            setup();
            if (!(resource instanceof XMLResource)) {
                throw new SourceException("Not an XML resource: " + getURI());
            }
            if (query != null) {
                throw new MalformedURLException("Not yet available for queries, only for single resource.");
            }
            String name=this.resName;
            resource = collection.createResource(name, XMLResource.RESOURCE_TYPE);
            return ((XMLResource)resource).getContentAsDOM();
        } catch (XMLDBException e) {
            String message = "Failed to create resource " + resName + ": " + e.errorCode;
            throw new SourceException(message, e);
        }        
    }

    /**
     * Return an {@link OutputStream} to write data to a binary resource.
     */
    public OutputStream getBinaryOutputStream() throws IOException, MalformedURLException {
        if (query != null) {
            throw new MalformedURLException("Cannot modify a resource that includes an XPATH expression");
        }
        return new XMLDBOutputStream(true);
    }
    
    /**
     * Create a new identifier for a resource within a collection. The current source must be
     * an existing collection.
     * 
     * @throws SourceException 
     */
    public String createId() throws SourceException {
        try {
            setup();
            if (status != ST_COLLECTION) {
                throw new SourceNotFoundException("Collection for createId not found: " + getURI());
            }
            return collection.createId();
        } catch(XMLDBException xdbe) {
            throw new SourceException("Cannot get Id for " + getURI(), xdbe);
        } finally {
            cleanup();
        }
    }

    private void writeOutputStream(ByteArrayOutputStream baos, boolean binary) throws SourceException {

        try {
            setup();
            if (status == ST_NO_PARENT) {
                // If there's no parent collection, create it
                collection = createCollection(colPath);
                status = ST_NO_RESOURCE;
            }
            
            // If it's a collection create an id for a child resource.
            // FIXME(SW): kept for backwards compatibility, but do we really want this?
            String name;
            if (status == ST_COLLECTION) {
                name = collection.createId();
            } else {
                name = this.resName;
            }

            String mimeType;
            
            Resource resource;
            if (binary) {
                resource = collection.createResource(name, BinaryResource.RESOURCE_TYPE);
                resource.setContent(baos.toByteArray());
                mimeType = MimeType.BINARY_TYPE.getName();
            } else {
                resource = collection.createResource(name, XMLResource.RESOURCE_TYPE);
                // FIXME: potential encoding problems here, as we don't know the one use in the stream
                // frederic.glorieux@ajlsm.com : Yes, it is, here a quick hack, default encoding for XML=UTF-8
                resource.setContent(new String(baos.toByteArray(), encoding) );
                mimeType = MimeType.XML_TYPE.getName();
            }
            
		    MimeType mime = MimeTable.getInstance().getContentTypeFor(name);
            if (mime != null) {
                mimeType = mime.getName();
            }
                
            ((EXistResource)resource).setMimeType(mimeType);
            collection.storeResource(resource);

            getLogger().debug("Written to resource " + resName);

        } catch (XMLDBException e) {
            String message = "Failed to create resource " + resName + ": " + e.errorCode;
            throw new SourceException(message, e);
        } catch (UnsupportedEncodingException e) {
            String message = "Encoding for the resource " + resName + encoding;
            throw new SourceException(message, e);
        } finally {
            cleanup();
        }
    }
    
    /** Set a default encoding for outputStream */
    public void setEncoding(String s) {
        this.encoding=s;
    }
    /** Set a default encoding for outputStream */
    public String getEncoding() {
        return this.encoding;
    }

    /**
     * Delete the source
     */
    public void delete() throws SourceException {
        try {
            setup();
            if (status == ST_RESOURCE) {
                collection.removeResource(resource);
            } else if (status == ST_COLLECTION) {
                Collection parent = collection.getParentCollection();
                CollectionManagementService service =
                    (CollectionManagementService) parent.getService("CollectionManagementService", "1.0");
                service.removeCollection(collection.getName());
                close(parent);
            }
        } catch (SourceException se) {
            throw se;
        } catch (XMLDBException xdbe) {
            throw new SourceException("Could not delete " + getURI());
        } finally {
            cleanup();
        }
    }

    /**
     * Can the data sent to an <code>OutputStream</code> returned by
     * {@link #getOutputStream()} be cancelled ?
     *
     * @return true if the stream can be cancelled
     */
    public boolean canCancel(OutputStream stream) {
        return stream instanceof XMLDBOutputStream && !((XMLDBOutputStream)stream).isClosed();
    }

    /**
     * Cancel the data sent to an <code>OutputStream</code> returned by
     * {@link #getOutputStream()}.
     *
     * <p>After cancelling, the stream should no longer be used.</p>
     */
    public void cancel(OutputStream stream) throws IOException {
        if (canCancel(stream)) {
            ((XMLDBOutputStream)stream).cancel();
        } else {
            throw new SourceException("Cannot cancel stream for " + getURI());
        }
    }

    private class XMLDBOutputStream extends OutputStream {

        private ByteArrayOutputStream baos;
        private boolean isClosed;
        private boolean binary;
        public XMLDBOutputStream(boolean binary) {
            baos = new ByteArrayOutputStream();
            isClosed = false;
            this.binary = binary;
        }

        public void write(int b) throws IOException {
            baos.write(b);
        }

        public void write(byte b[]) throws IOException {
            baos.write(b);
        }

        public void write(byte b[], int off, int len) throws IOException {
            baos.write(b, off, len);
        }

        public void close() throws IOException, SourceException {
            if (!isClosed) {
                writeOutputStream(baos, this.binary);
                baos.close();
                this.isClosed = true;
            }
        }

        public void flush() throws IOException {
        }

        public int size() {
            return baos.size();
        }

        public boolean isClosed() {
            return this.isClosed;
        }
        public void cancel() {
            this.isClosed = true;
        }
    }

    public void makeCollection() throws SourceException {
        try {
            createCollection(this.url);
        } catch (SourceException e) {
            throw e;
        } catch (XMLDBException e) {
            throw new SourceException("Cannot make collection with " + getURI());
        }
    }

    public boolean isCollection() {
        try {
            setup();
            return status == ST_COLLECTION;
        } catch (Exception e) {
            return false;
        } finally {
            cleanup();
        }
    }

    public java.util.Collection getChildren() throws SourceException {
        try {
            setup();
            if (status != ST_COLLECTION) {
                throw new SourceException("Not a collection: " + getURI());
            }
            String[] childColl = collection.listChildCollections();
            String[] childRes = collection.listResources();
            ArrayList children = new ArrayList(childColl.length + childRes.length);
            for (int i = 0; i < childColl.length; i++) {
                children.add(new XMLDBSource(getLogger(), user, password, url + "/" + childColl[i]));
            }
            for (int i = 0; i < childRes.length; i++) {
                children.add(new XMLDBSource(getLogger(), user, password, url + "/" + childRes[i]));
            }
            
            return children;
        } catch (SourceException e) {
            throw e;
        } catch (XMLDBException e) {
            throw new SourceException("Cannot list children of " + getURI());
        } finally {
            cleanup();
        }
    }
    
    public Source getChild(String name) throws SourceException {
        return new XMLDBSource(getLogger(), user, password, this.url + "/" + name);
    }

    public String getName() {
        return resName;
    }

    public Source getParent() throws SourceException {
        return new XMLDBSource(getLogger(), user, password, this.colPath);
    }
}
