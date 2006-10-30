/*
 * AtomProtocol.java
 *
 * Created on June 16, 2006, 11:39 AM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.modules;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.atom.Atom;
import org.exist.atom.IncomingMessage;
import org.exist.atom.OutgoingMessage;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;


/**
 *
 * @author R. Alexander Milowski
 */
public class AtomFeeds extends AtomModuleBase implements Atom {
   
   protected final static Logger LOG = Logger.getLogger(AtomProtocol.class);
   static final String FEED_DOCUMENT_NAME = ".feed.atom";
   static final XmldbURI FEED_DOCUMENT_URI = XmldbURI.create(FEED_DOCUMENT_NAME);
   
   /** Creates a new instance of AtomProtocol */
   public AtomFeeds() {
   }
   
   public void doGet(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      
      DocumentImpl resource = null;
      XmldbURI pathUri = XmldbURI.create(request.getPath());
      try {
         
         resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);
         
         if (resource == null) {
            
            // Must be a collection
            Collection collection = broker.getCollection(pathUri);
            if (collection != null) {
               if (!collection.getPermissions().validate(broker.getUser(), Permission.READ)) {
                  throw new PermissionDeniedException("Not allowed to read collection");
               }
               DocumentImpl feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
               if (feedDoc==null) {
                  throw new BadRequestException("Collection "+request.getPath()+" is not an Atom feed.");
               }
               // Return the collection feed
               String charset = getContext().getDefaultCharset();
               response.setStatusCode(200);
               response.setContentType(Atom.MIME_TYPE+"; charset="+charset);
               
               Serializer serializer = broker.getSerializer();
               serializer.reset();
               
               //Serialize the document
               try {
                  Writer w = new OutputStreamWriter(response.getOutputStream(),charset);
                  serializer.serialize(feedDoc,w);
                  w.flush();
                  w.close();
               } catch (IOException ex) {
                  LOG.fatal("Cannot read resource "+request.getPath(),ex);
                  throw new EXistException("I/O error on read of resource "+request.getPath(),ex);
               } catch (SAXException saxe) {
                  LOG.warn(saxe);
                  throw new BadRequestException("Error while serializing XML: " + saxe.getMessage());
               }
            } else {
               throw new NotFoundException("Resource " + request.getPath() + " not found");
            }
         } else {
            //Do we have permission to read the resource
            if (!resource.getPermissions().validate(broker.getUser(), Permission.READ)) {
               throw new PermissionDeniedException("Not allowed to read resource");
            }
            
            if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
               response.setContentType(resource.getMetadata().getMimeType());
               try {
                  OutputStream os = response.getOutputStream();
                  broker.readBinaryResource((BinaryDocument) resource, os);
                  os.flush();
               } catch (IOException ex) {
                  LOG.fatal("Cannot read resource "+request.getPath(),ex);
                  throw new EXistException("I/O error on read of resource "+request.getPath(),ex);
               }
            } else {
               // xml resource
               Serializer serializer = broker.getSerializer();
               serializer.reset();
               
               String charset = getContext().getDefaultCharset();
               //Serialize the document
               try {
                  response.setContentType(resource.getMetadata().getMimeType()+"; charset="+charset);
                  Writer w = new OutputStreamWriter(response.getOutputStream(),charset);
                  serializer.serialize(resource,w);
                  w.flush();
                  w.close();
               } catch (IOException ex) {
                  LOG.fatal("Cannot read resource "+request.getPath(),ex);
                  throw new EXistException("I/O error on read of resource "+request.getPath(),ex);
               } catch (SAXException saxe) {
                  LOG.warn(saxe);
                  throw new BadRequestException("Error while serializing XML: " + saxe.getMessage());
               }
            }
         }
      } finally {
         if (resource != null) {
            resource.getUpdateLock().release(Lock.READ_LOCK);
         }
      }
   }
   
}
