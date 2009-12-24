/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
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
package org.exist.http.webdav.methods;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.LockToken;
import org.exist.http.webdav.WebDAV;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.security.xacml.AccessContext;
import org.exist.source.ClassLoaderSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.xml.sax.SAXException;

/**
 * Implements the WebDAV GET method.
 *
 * @author wolf
 */
public class Get extends AbstractWebDAVMethod {
    
    private final static String SERIALIZE_ERROR
            = "Error while serializing document: ";
    
    private final static String COLLECTION_XQ
            = "org/exist/http/webdav/methods/collection.xq";
    
    // We use an XQuery to list collection contents
    private CompiledXQuery compiled = null;
    
    public Get(BrokerPool pool) {
        super(pool);
    }
    
    public void process(User user, HttpServletRequest request,
            HttpServletResponse response, XmldbURI path)
            throws ServletException, IOException {
        
        DBBroker broker = null;
        DocumentImpl resource = null;
        Collection collection = null;
        
        try {
            broker = pool.get(user);
            resource = broker.getXMLResource(path, Lock.READ_LOCK);
            
            if(resource == null) {
                collection = broker.openCollection(path, Lock.READ_LOCK);
                if(collection == null){
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    
                } else {
                    collectionListing(broker, collection, request, response);
                }
                return;
                //TODO : release collection lock here ?
            }
            
            if(!resource.getPermissions().validate(user, Permission.READ)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, 
                                   READ_PERMISSION_DENIED);
                return;
            }
            
            LockToken token = resource.getMetadata().getLockToken();
            if(token!=null && token.isNullResource() ){
                response.sendError(HttpServletResponse.SC_NOT_FOUND, 
                                   "Document is a Null resource and cannot be retrieved");
                return;
            }
            
            
            DocumentMetadata metadata = resource.getMetadata();
            response.setContentType(metadata.getMimeType());
            response.addDateHeader("Last-Modified", metadata.getLastModified());
			response.addDateHeader("Created", metadata.getCreated());
            
//            response.setContentLength(resource.getContentLength());
            ServletOutputStream os = response.getOutputStream();
            
            if(resource.getResourceType() == DocumentImpl.XML_FILE) {
                Serializer serializer = broker.getSerializer();
                serializer.reset();
                try {
                    serializer.setProperties(WebDAV.OUTPUT_PROPERTIES);
                    
                    Writer w = new OutputStreamWriter(os,"UTF-8");
                    serializer.serialize(resource,w);
                    w.flush();
                    w.close();
                    
                } catch (SAXException e) {
                    LOG.error(e);
                    throw new ServletException(SERIALIZE_ERROR + e.getMessage(), e);
                }
                
            } else {
                broker.readBinaryResource((BinaryDocument) resource, os);
                os.flush();
            }
            
//            os.flush();
            
        } catch (EXistException e) {
            throw new ServletException(SERIALIZE_ERROR + e.getMessage(), e);
            
        } catch (PermissionDeniedException e) {
            LOG.error(e);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, READ_PERMISSION_DENIED);
            return;
            
        } finally {
            if(resource != null)
                resource.getUpdateLock().release(Lock.READ_LOCK);
            
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            
            pool.release(broker);
        }
        
    }
    
    /**
     * Display a listing of the collection contents.
     *
     * @param collection
     * @param response
     * @throws IOException
     */
    private void collectionListing(DBBroker broker, Collection collection,
            HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // the collection listing is generated by an XQuery
        XQuery xquery = broker.getXQueryService();
        XQueryContext context;
        try {
            if(compiled == null)
                context = xquery.newContext(AccessContext.WEBDAV);
            else {
                compiled.reset();
                context = compiled.getContext();
            }
            
            context.declareVariable("collection", collection.getURI().toString() );
            context.declareVariable("uri", request.getRequestURI().toString() );
            
            if(compiled == null){
                compiled = xquery.compile(
                        context, new ClassLoaderSource(COLLECTION_XQ));
            }
            
            
            Sequence result = xquery.execute(compiled, null);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            serializer.setProperties(WebDAV.OUTPUT_PROPERTIES);
            String content = serializer.serialize((NodeValue)result.itemAt(0));
            byte[] contentData = content.getBytes("UTF-8");
            
            response.setContentType("text/html; charset=UTF-8");
            response.setContentLength(contentData.length);
            response.addDateHeader("Last-Modified", collection.getCreationTime());
			response.addDateHeader("Created", collection.getCreationTime());
            
            ServletOutputStream os = response.getOutputStream();
            os.write(contentData);
            os.flush();
            
        } catch (XPathException e) {
            LOG.error("Failed to compile xquery", e);
            throw new ServletException("Failed to compile xquery", e);
            
        } catch (SAXException e) {
            LOG.error("Failed to serialize query results", e);
            throw new ServletException("Failed to serialize query results", e);
        }
    }
}
