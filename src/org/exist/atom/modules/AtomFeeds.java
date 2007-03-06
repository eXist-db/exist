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
import java.util.Properties;

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
import org.exist.security.xacml.AccessContext;
import org.exist.source.URLSource;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.xml.sax.SAXException;


/**
 *
 * @author R. Alexander Milowski
 */
public class AtomFeeds extends AtomModuleBase implements Atom {
   
   protected final static Logger LOG = Logger.getLogger(AtomProtocol.class);
   static final String FEED_DOCUMENT_NAME = ".feed.atom";
   static final XmldbURI FEED_DOCUMENT_URI = XmldbURI.create(FEED_DOCUMENT_NAME);
   URLSource entryByIdSource;
   
   /** Creates a new instance of AtomProtocol */
   public AtomFeeds() {
      entryByIdSource = new URLSource(this.getClass().getResource("entry-by-id.xq"));
   }
   
   public void doGet(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
       handleGet(true,broker,request,response);
   }
      
   public void doHead(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
       handleGet(false,broker,request,response);
   }
   
   protected void handleGet(boolean returnContent, DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      DocumentImpl resource = null;
      XmldbURI pathUri = XmldbURI.create(request.getPath());
      try {
         
         resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);
         
         if (resource == null) {
            
            String id = request.getParameter("id");
            if (id!=null) {
               id = id.trim();
               if (id.length()==0) {
                  id = null;
               }
            }
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
               if (returnContent) {
                  if (id==null) {
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
                     response.setStatusCode(200);
                     getEntryById(broker,request.getPath(),id,response);
                  }
               } else {
                  response.setStatusCode(204);
               }
            } else {
               throw new NotFoundException("Resource " + request.getPath() + " not found");
            }
         } else {
            //Do we have permission to read the resource
            if (!resource.getPermissions().validate(broker.getUser(), Permission.READ)) {
               throw new PermissionDeniedException("Not allowed to read resource");
            }
            
            if (returnContent) {
               response.setStatusCode(200);
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
            } else {
               response.setStatusCode(204);
            }
         }
      } finally {
         if (resource != null) {
            resource.getUpdateLock().release(Lock.READ_LOCK);
         }
      }
   }
   
   public void getEntryById(DBBroker broker,String path,String id,OutgoingMessage response)
      throws EXistException,BadRequestException
   {
      XQuery xquery = broker.getXQueryService();
      CompiledXQuery feedQuery = xquery.getXQueryPool().borrowCompiledXQuery(broker,entryByIdSource);

      XQueryContext context;
      if (feedQuery==null) {
         context = xquery.newContext(AccessContext.REST);
         try {
            feedQuery = xquery.compile(context, entryByIdSource);
         } catch (XPathException ex) {
            throw new EXistException("Cannot compile xquery "+entryByIdSource.getURL(),ex);
         } catch (IOException ex) {
            throw new EXistException("I/O exception while compiling xquery "+entryByIdSource.getURL(),ex);
         }
      } else {
         context = feedQuery.getContext();
      }
      context.setStaticallyKnownDocuments(new XmldbURI[] { XmldbURI.create(path).append(AtomProtocol.FEED_DOCUMENT_NAME) });

      try {
         context.declareVariable("id",id);
         Sequence resultSequence = xquery.execute(feedQuery, null);
         if (resultSequence.isEmpty()) {
            throw new BadRequestException("No topic was found.");
         }
         String charset = getContext().getDefaultCharset();
         response.setContentType("application/atom+xml; charset="+charset);
         Serializer serializer = broker.getSerializer();
         serializer.reset();
         try {
            Writer w = new OutputStreamWriter(response.getOutputStream(),charset);
            SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            Properties outputProperties = new Properties();
            sax.setOutput(w, outputProperties);
            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);

            serializer.toSAX(resultSequence, 1, 1, false);

            SerializerPool.getInstance().returnObject(sax);
            w.flush();
            w.close();
         } catch (IOException ex) {
            LOG.fatal("Cannot read resource "+path,ex);
            throw new EXistException("I/O error on read of resource "+path,ex);
         } catch (SAXException saxe) {
            LOG.warn(saxe);
            throw new BadRequestException("Error while serializing XML: " + saxe.getMessage());
         }
         resultSequence.itemAt(0);
      } catch (XPathException ex) {
         throw new EXistException("Cannot execute xquery "+entryByIdSource.getURL(),ex);
      } finally {
         xquery.getXQueryPool().returnCompiledXQuery(entryByIdSource, feedQuery);
      }
      
   }
   
}
