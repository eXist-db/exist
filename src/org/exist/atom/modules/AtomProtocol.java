/*
 * AtomProtocol.java
 *
 * Created on June 16, 2006, 11:39 AM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.modules;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.atom.Atom;
import org.exist.atom.IncomingMessage;
import org.exist.atom.OutgoingMessage;
import org.exist.atom.util.DOM;
import org.exist.atom.util.DOMDB;
import org.exist.atom.util.DateFormatter;
import org.exist.atom.util.NodeHandler;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeIndexListener;
import org.exist.dom.StoredNode;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.UUIDGenerator;
import org.exist.security.User;
import org.exist.storage.DBBroker;
import org.exist.storage.StorageAddress;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;


/**
 *
 * @author R. Alexander Milowski
 */
public class AtomProtocol extends AtomFeeds implements Atom {
   
   protected final static Logger LOG = Logger.getLogger(AtomProtocol.class);
   public static final String FEED_DOCUMENT_NAME = ".feed.atom";
   public static final String ENTRY_COLLECTION_NAME = ".feed.entry";
   public static final XmldbURI FEED_DOCUMENT_URI = XmldbURI.create(FEED_DOCUMENT_NAME);
   public static final XmldbURI ENTRY_COLLECTION_URI = XmldbURI.create(ENTRY_COLLECTION_NAME);
   private static final String ENTRY_XPOINTER = "xpointer(/entry)";
   
   final static class NodeListener implements NodeIndexListener {
      
      StoredNode node;
      
      public NodeListener(StoredNode node) {
         this.node = node;
      }
      
      public void nodeChanged(StoredNode newNode) {
         final long address = newNode.getInternalAddress();
         if (StorageAddress.equals(node.getInternalAddress(), address)) {
            node = newNode;
         }
      }
   }
   
   /** Creates a new instance of AtomProtocol */
   public AtomProtocol() {
   }
   
   public void doPost(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      XmldbURI pathUri = XmldbURI.create(request.getPath());
      String contentType = request.getHeader("Content-Type");
      String charset = getContext().getDefaultCharset();
      
      MimeType mime = MimeType.BINARY_TYPE;
      if (contentType != null) {
         int semicolon = contentType.indexOf(';');
         if (semicolon>0) {
            contentType = contentType.substring(0,semicolon).trim();
         }
         mime = MimeTable.getInstance().getContentType(contentType);
         if (mime==null) {
             mime = MimeType.BINARY_TYPE;
          }
         int equals = contentType.indexOf('=',semicolon);
         if (equals>0) {
            String param = contentType.substring(semicolon+1,equals).trim();
            if (param.compareToIgnoreCase("charset=")==0) {
               charset = param.substring(equals+1).trim();
            }
         }
      }
      
      String currentDateTime = DateFormatter.toXSDDateTime(new Date());

      Collection collection = broker.getCollection(pathUri);
      
      if (mime.getName().equals(Atom.MIME_TYPE)) {
         DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
         docFactory.setNamespaceAware(true);
         DocumentBuilder docBuilder = null;
         Document doc = null;
         try {
            InputSource src = new InputSource(new InputStreamReader(request.getInputStream(),charset));
            docBuilder = docFactory.newDocumentBuilder();
            doc = docBuilder.parse(src);
         } catch (IOException e) {
            LOG.warn(e);
            throw new BadRequestException(e.getMessage());
         } catch (SAXException e) {
            LOG.warn(e);
            throw new BadRequestException(e.getMessage());
         } catch (ParserConfigurationException e) {
            LOG.warn(e);
            throw new BadRequestException(e.getMessage());
         }
         Element root = doc.getDocumentElement();
         String ns = root.getNamespaceURI();
         if (ns==null || !ns.equals(Atom.NAMESPACE_STRING)) {
            throw new BadRequestException("Any content posted with the Atom mime type must be in the Atom namespace.");
         }
         if (root.getLocalName().equals("feed")) {
             DocumentImpl feedDoc = null;
             TransactionManager transact = broker.getBrokerPool().getTransactionManager();
             Txn transaction = transact.beginTransaction();
             try {
                if (collection != null) {
                   feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
                   if (feedDoc!=null) {
                      throw new PermissionDeniedException("Collection at "+request.getPath()+" already exists.");
                   }
                } else {
                   collection = broker.getOrCreateCollection(transaction,pathUri);
                   setPermissions(broker, root, collection);
                   broker.saveCollection(transaction, collection);
                }
                String id = UUIDGenerator.getUUID();
                DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"updated",currentDateTime,true);
                DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"id","urn:uuid:"+id,true);
                Element editLink = findLink(root,"edit");
                if (editLink!=null) {
                   throw new BadRequestException("An edit link relation cannot be specified in the feed.");
                }
                editLink = doc.createElementNS(Atom.NAMESPACE_STRING,"link");
                editLink.setAttribute("rel","edit");
                editLink.setAttribute("type",Atom.MIME_TYPE);
                editLink.setAttribute("href","#");
                root.appendChild(editLink);
                Element selfLink = findLink(root,"self");
                if (selfLink!=null) {
                   throw new BadRequestException("A self link relation cannot be specified in the feed.");
                }
                selfLink = doc.createElementNS(Atom.NAMESPACE_STRING,"link");
                selfLink.setAttribute("rel","self");
                selfLink.setAttribute("type",Atom.MIME_TYPE);
                selfLink.setAttribute("href","#");
                root.appendChild(selfLink);
                IndexInfo info = collection.validateXMLResource(transaction,broker,FEED_DOCUMENT_URI,doc);
                setPermissions(broker, root, info.getDocument());
                //TODO : We should probably unlock the collection here
                collection.store(transaction,broker,info,doc,false);
                transact.commit(transaction);
                response.setStatusCode(204);
                response.setHeader("Location",request.getModuleBase()+request.getPath());
             } catch (IOException ex) {
                 transact.abort(transaction);
                 throw new EXistException("IO error: "+ex.getMessage(),ex);
             } catch (TriggerException ex) {
                 transact.abort(transaction);
                 throw new EXistException("Trigger failed: "+ex.getMessage(),ex);
             } catch (SAXException ex) {
                 transact.abort(transaction);
                 throw new EXistException("SAX error: "+ex.getMessage(),ex);
            } catch (LockException ex) {
                transact.abort(transaction);
                throw new EXistException("Cannot acquire write lock.",ex);
             }
         } else if (root.getLocalName().equals("entry")) {
             if (collection == null) {
                 throw new BadRequestException("Collection "+request.getPath()+" does not exist.");
              }
              LOG.debug("Adding entry to " + request.getPath());
              DocumentImpl feedDoc = null;
              feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
              if (!feedDoc.getPermissions().validate(broker.getUser(), Permission.UPDATE))
                  throw new PermissionDeniedException("Permission denied to update feed " + collection.getURI());
              TransactionManager transact = broker.getBrokerPool().getTransactionManager();
              Txn transaction = transact.beginTransaction();
              String uuid = UUIDGenerator.getUUID();
              String id = "urn:uuid:"+uuid;
              Element publishedE = DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"published",currentDateTime,true,true);
              DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"updated",currentDateTime,true,true);
              DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"id",id,true,true);
              Element editLink = findLink(root,"edit");
              Element editLinkSrc = findLink(root,"edit-media");
              if (editLink!=null || editLinkSrc!=null) {
                 throw new BadRequestException("An edit link relation cannot be specified in the entry.");
              }
              editLink = doc.createElementNS(Atom.NAMESPACE_STRING,"link");
              editLink.setAttribute("rel","edit");
              editLink.setAttribute("type",Atom.MIME_TYPE);
              editLink.setAttribute("href","?id="+id);
              Node next = publishedE.getNextSibling();
              if (next==null) {
                 root.appendChild(editLink);
              } else {
                 root.insertBefore(editLink,next);
              }
              try {
                 // get the feed
                 LOG.debug("Acquiring lock on feed document...");
                 ElementImpl feedRoot = (ElementImpl)feedDoc.getDocumentElement();
                 
                 // Lock the feed
                 feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
                 
                 // Append the entry
                 collection = broker.getOrCreateCollection(transaction,pathUri.append(ENTRY_COLLECTION_URI));
                 setPermissions(broker, root, collection);
                 broker.saveCollection(transaction, collection);
                 XmldbURI entryURI = entryURI(uuid);
                 DocumentImpl entryDoc = collection.getDocument(broker,entryURI);
                 if (entryDoc!=null) {
                     throw new PermissionDeniedException("Entry with "+id+" already exists.");
                  }
                 IndexInfo info = collection.validateXMLResource(transaction,broker,entryURI,doc);
                 setPermissions(broker, root, info.getDocument());
                 //TODO : We should probably unlock the collection here
                 collection.store(transaction,broker,info,doc,false);
                 
                 // Update the updated element
                 DOMDB.replaceTextElement(transaction,feedRoot,Atom.NAMESPACE_STRING,"updated",currentDateTime,true);

                 // Store the changes
                 LOG.debug("Storing change...");
                 broker.storeXMLResource(transaction, feedDoc);
                 transact.commit(transaction);
                 
                 LOG.debug("Done!");
                 
                 response.setStatusCode(201);
                 response.setHeader("Location",request.getModuleBase()+request.getPath()+"?id="+id);
                 getEntryById(broker,request.getPath(),id,response);
                 /*
                 response.setContentType(Atom.MIME_TYPE+"; charset="+charset);
                 OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream(),charset);
                 Transformer identity = TransformerFactory.newInstance().newTransformer();
                 identity.transform(new DOMSource(doc),new StreamResult(w));
                 w.flush();
                 w.close();
                  */
              } catch (IOException ex) {
                  transact.abort(transaction);
                  throw new EXistException("IO error: "+ex.getMessage(),ex);
              } catch (TriggerException ex) {
                  transact.abort(transaction);
                  throw new EXistException("Trigger failed: "+ex.getMessage(),ex);
              } catch (SAXException ex) {
                  transact.abort(transaction);
                  throw new EXistException("SAX error: "+ex.getMessage(),ex);
              } catch (LockException ex) {
                 transact.abort(transaction);
                 throw new EXistException("Cannot acquire write lock.",ex);
                 /*
              } catch (IOException ex) {
                 throw new EXistException("Internal error while serializing result.",ex);
              } catch (TransformerException ex) {
                 throw new EXistException("Serialization error.",ex);
                  */
              } finally {
                 if (feedDoc!=null) {
                    feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);
                 }
              }
         } else {
            throw new BadRequestException("Unexpected element: {http://www.w3.org/2005/Atom}"+root.getLocalName());
         }
      } else {
         if (collection == null) {
            throw new BadRequestException("Collection "+request.getPath()+" does not exist.");
         }
         DocumentImpl feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
         if (feedDoc==null) {
            throw new BadRequestException("Feed at "+request.getPath()+" does not exist.");
         }
         if (!feedDoc.getPermissions().validate(broker.getUser(), Permission.UPDATE))
             throw new PermissionDeniedException("Permission denied to update feed " + collection.getURI());
         String filename = request.getHeader("Slug");
         if (filename==null) {
            String ext = MimeTable.getInstance().getPreferredExtension(mime);
            int count = 1;
            while (filename==null) {
               filename = "resource"+count+ext;
               if (collection.getDocument(broker,XmldbURI.create(filename))!=null) {
                  filename = null;
               }
               count++;
            }
         }
         TransactionManager transact = broker.getBrokerPool().getTransactionManager();
         Txn transaction = transact.beginTransaction();
         try {
            XmldbURI docUri = XmldbURI.create(filename);
            if (collection.getDocument(broker,docUri)!=null) {
               transact.abort(transaction);
               throw new BadRequestException("Resource "+docUri+" already exists in collection "+pathUri);
            }
            
            File tempFile = storeInTemporaryFile(request.getInputStream(),request.getContentLength());
            
            if (mime.isXMLType()) {
               InputStream is = new FileInputStream(tempFile);
               IndexInfo info = collection.validateXMLResource(transaction, broker, docUri, new InputSource(new InputStreamReader(is,charset)));
               is.close();
               info.getDocument().getMetadata().setMimeType(contentType);
               is = new FileInputStream(tempFile);
               collection.store(transaction, broker, info, new InputSource(new InputStreamReader(is,charset)), false);
               is.close();
            } else {
               FileInputStream is = new FileInputStream(tempFile);
               collection.addBinaryResource(transaction, broker, docUri, is, contentType, (int) tempFile.length());
               is.close();
            }
            try {
               LOG.debug("Acquiring lock on feed document...");
               feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
               String title = request.getHeader("Title");
               if (title==null) {
                  title = filename;
               }
               String created = DateFormatter.toXSDDateTime(new Date());
               ElementImpl feedRoot = (ElementImpl)feedDoc.getDocumentElement();
               DOMDB.replaceTextElement(transaction,feedRoot,Atom.NAMESPACE_STRING,"updated",created,true);
               String uuid = UUIDGenerator.getUUID();
               String id = "urn:uuid:"+uuid;
               Element mediaEntry = generateMediaEntry(id,created,title,filename,mime.getName());
               
               collection = broker.getOrCreateCollection(transaction,pathUri.append(ENTRY_COLLECTION_URI));
               broker.saveCollection(transaction, collection);
               XmldbURI entryURI = entryURI(uuid);
               DocumentImpl entryDoc = collection.getDocument(broker,entryURI);
               if (entryDoc!=null) {
                   throw new PermissionDeniedException("Entry with "+id+" already exists.");
                }
               IndexInfo info = collection.validateXMLResource(transaction,broker,entryURI,mediaEntry);
               //TODO : We should probably unlock the collection here
               collection.store(transaction,broker,info,mediaEntry,false);
               // Update the updated element
               DOMDB.replaceTextElement(transaction,feedRoot,Atom.NAMESPACE_STRING,"updated",currentDateTime,true);
               LOG.debug("Storing change...");
               broker.storeXMLResource(transaction, feedDoc);
               transact.commit(transaction);
               LOG.debug("Done!");
               response.setStatusCode(201);
               response.setHeader("Location",request.getModuleBase()+request.getPath()+"?id="+id);
               response.setContentType(Atom.MIME_TYPE+"; charset="+charset);
               OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream(),charset);
               Transformer identity = TransformerFactory.newInstance().newTransformer();
               identity.transform(new DOMSource(mediaEntry),new StreamResult(w));
               w.flush();
               w.close();
            } catch (ParserConfigurationException ex) {
               transact.abort(transaction);
               throw new EXistException("DOM implementation is misconfigured.",ex);
            } catch (TransformerException ex) {
               throw new EXistException("Serialization error.",ex);
            } catch (LockException ex) {
               transact.abort(transaction);
               throw new EXistException("Cannot acquire write lock.",ex);
            } finally {
               if (feedDoc!=null) {
                  feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);
               }
            }
         } catch (IOException ex) {
            transact.abort(transaction);
            throw new EXistException("I/O error while handling temporary files.",ex);
         } catch (SAXParseException e) {
            transact.abort(transaction);
            throw new BadRequestException("Parsing exception at "
                    + e.getLineNumber() + "/" + e.getColumnNumber() + ": "
                    + e.toString());
         } catch (TriggerException e) {
             transact.abort(transaction);
             throw new PermissionDeniedException(e.getMessage());
         } catch (SAXException e) {
            transact.abort(transaction);
            Exception o = e.getException();
            if (o == null)
               o = e;
            throw new BadRequestException("Parsing exception: "
                    + o.getMessage());
         } catch (LockException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
         }
      }
   }

   public void doPut(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      XmldbURI pathUri = XmldbURI.create(request.getPath());
      String contentType = request.getHeader("Content-Type");
      String charset = getContext().getDefaultCharset();
      
      MimeType mime = MimeType.BINARY_TYPE;
      if (contentType != null) {
         int semicolon = contentType.indexOf(';');
         if (semicolon>0) {
            contentType = contentType.substring(0,semicolon).trim();
         }
         mime = MimeTable.getInstance().getContentType(contentType);
         if (mime==null) {
            mime = MimeType.BINARY_TYPE;
         }
         int equals = contentType.indexOf('=',semicolon);
         if (equals>0) {
            String param = contentType.substring(semicolon+1,equals).trim();
            if (param.compareToIgnoreCase("charset=")==0) {
               charset = param.substring(equals+1).trim();
            }
         }
      }
      
      String currentDateTime = DateFormatter.toXSDDateTime(new Date());

      Collection collection = broker.getCollection(pathUri);

      if (mime.getName().equals(Atom.MIME_TYPE)) {
         DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
         docFactory.setNamespaceAware(true);
         DocumentBuilder docBuilder = null;
         Document doc = null;
         try {
            InputSource src = new InputSource(new InputStreamReader(request.getInputStream(),charset));
            docBuilder = docFactory.newDocumentBuilder();
            doc = docBuilder.parse(src);
         } catch (IOException e) {
            LOG.warn(e);
            throw new BadRequestException(e.getMessage());
         } catch (SAXException e) {
            LOG.warn(e);
            throw new BadRequestException(e.getMessage());
         } catch (ParserConfigurationException e) {
            LOG.warn(e);
            throw new BadRequestException(e.getMessage());
         }
         Element root = doc.getDocumentElement();
         String ns = root.getNamespaceURI();
         if (ns==null || !ns.equals(Atom.NAMESPACE_STRING)) {
            throw new BadRequestException("Any content posted with the Atom mime type must be in the Atom namespace.");
         }
         if (root.getLocalName().equals("feed")) {
            DocumentImpl feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
            if (feedDoc==null) {
               throw new BadRequestException("Collection at "+request.getPath()+" does not exist.");
            }
            feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
            if (!feedDoc.getPermissions().validate(broker.getUser(), Permission.UPDATE))
                throw new PermissionDeniedException("Permission denied to update feed " + collection.getURI());
            
            if (DOM.findChild(root,Atom.NAMESPACE_STRING,"title")==null) {
               throw new BadRequestException("The feed metadata sent does not contain a title.");
            }

             if (!feedDoc.getPermissions().validate(broker.getUser(), Permission.UPDATE)) {
                 throw new PermissionDeniedException("Permission denied to update feed " + collection.getURI());
             }
             TransactionManager transact = broker.getBrokerPool().getTransactionManager();
             Txn transaction = transact.beginTransaction();
            try {
               feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
               ElementImpl feedRoot = (ElementImpl)feedDoc.getDocumentElement();
               
               // Modify the feed by merging the new feed-level elements
               mergeFeed(broker,transaction,feedRoot,root,DateFormatter.toXSDDateTime(new Date()));
               
               // Store the feed
               broker.storeXMLResource(transaction, feedDoc);
               transact.commit(transaction);
               response.setStatusCode(204);
            } catch (LockException ex) {
               transact.abort(transaction);
               throw new EXistException("Cannot acquire write lock.",ex);
            } catch (RuntimeException ex) {
               transact.abort(transaction);
               throw ex;
            } finally {
               if (feedDoc!=null) {
                  feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);
               }
            }
         } else if (root.getLocalName().equals("entry")) {
            if (collection == null) {
               throw new BadRequestException("Collection "+request.getPath()+" does not exist.");
            }
            String id = request.getParameter("id");
            if (id==null) {
               throw new BadRequestException("The 'id' parameter for the entry is missing.");
            }
            LOG.debug("Updating entry "+id+" in collection "+request.getPath());
            DocumentImpl feedDoc = null;
            DocumentImpl entryDoc = null;
            TransactionManager transact = broker.getBrokerPool().getTransactionManager();
            Txn transaction = transact.beginTransaction();
            try {
               // Get the feed
               LOG.debug("Acquiring lock on feed document...");
               feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
               if (!feedDoc.getPermissions().validate(broker.getUser(), Permission.UPDATE))
                   throw new PermissionDeniedException("Permission denied to update feed " + collection.getURI());
               feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);

               // Find the entry
               String uuid = id.substring(9);
               collection = broker.getCollection(pathUri.append(ENTRY_COLLECTION_URI));
               XmldbURI entryURI = entryURI(uuid);
               entryDoc = collection.getDocument(broker,entryURI);
               if (entryDoc==null) {
                   throw new BadRequestException("Cannot find entry with id "+id);
                }

               // Lock the entry
               entryDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
               
               Element entry = entryDoc.getDocumentElement();
               
               mergeEntry(transaction,(ElementImpl)entry,root,currentDateTime);
               
               // Update the feed time
               DOMDB.replaceTextElement(transaction,(ElementImpl)feedDoc.getDocumentElement(),Atom.NAMESPACE_STRING,"updated",currentDateTime,true);

               // Store the feed
               broker.storeXMLResource(transaction, feedDoc);
               broker.storeXMLResource(transaction, entryDoc);
               transact.commit(transaction);
               
               // Send back the changed entry
               response.setStatusCode(200);
               getEntryById(broker,request.getPath(),id,response);
               /*
               response.setStatusCode(200);
               response.setContentType(Atom.MIME_TYPE+"; charset="+charset);
               OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream(),charset);
               Transformer identity = TransformerFactory.newInstance().newTransformer();
               identity.transform(new DOMSource(entry),new StreamResult(w));
               w.flush();
               w.close();
                */
            } catch (LockException ex) {
               transact.abort(transaction);
               throw new EXistException("Cannot acquire write lock.",ex);
               /*
            } catch (IOException ex) {
               throw new EXistException("I/O exception during serialization of entry response.",ex);
            } catch (TransformerException ex) {
               throw new EXistException("Serialization error.",ex);
                */
            } finally {
                if (feedDoc!=null) {
                    feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);
               }
                if (entryDoc!=null) {
                    entryDoc.getUpdateLock().release(Lock.WRITE_LOCK);
               }
            }
         } else {
            throw new BadRequestException("Unexpected element: {http://www.w3.org/2005/Atom}"+root.getLocalName());
         }
      } else {
         TransactionManager transact = broker.getBrokerPool().getTransactionManager();
         Txn transaction = transact.beginTransaction();
         try {
            XmldbURI docUri = pathUri.lastSegment();
            XmldbURI collUri = pathUri.removeLastSegment();
            
            if (docUri==null || collUri==null) {
               transact.abort(transaction);
               throw new BadRequestException("The path is not valid: " + request.getPath());
            }
            collection = broker.getCollection(collUri);
            if (collection == null) {
               transact.abort(transaction);
               throw new BadRequestException("The collection does not exist: " + collUri);
            }
            if (collection.getDocument(broker,docUri)==null) {
               transact.abort(transaction);
               throw new BadRequestException("Resource "+docUri+" does not exist in collection "+collUri);
            }
            
            File tempFile = storeInTemporaryFile(request.getInputStream(),request.getContentLength());
            
            if (mime.isXMLType()) {
               InputStream is = new FileInputStream(tempFile);
               IndexInfo info = collection.validateXMLResource(transaction, broker, docUri, new InputSource(new InputStreamReader(is,charset)));
               is.close();
               info.getDocument().getMetadata().setMimeType(contentType);
               is = new FileInputStream(tempFile);
               collection.store(transaction, broker, info, new InputSource(new InputStreamReader(is,charset)), false);
               is.close();
            } else {
               FileInputStream is = new FileInputStream(tempFile);
               collection.addBinaryResource(transaction, broker, docUri, is, contentType, (int) tempFile.length());
               is.close();
            }
            transact.commit(transaction);
            
            // TODO: Change the entry updated and send back the change?
            response.setStatusCode(200);
            
         } catch (IOException ex) {
            transact.abort(transaction);
            throw new EXistException("I/O error while handling temporary files.",ex);
         } catch (SAXParseException e) {
            transact.abort(transaction);
            throw new BadRequestException("Parsing exception at "
                    + e.getLineNumber() + "/" + e.getColumnNumber() + ": "
                    + e.toString());
         } catch (TriggerException e) {
             transact.abort(transaction);
             throw new PermissionDeniedException(e.getMessage());
         } catch (SAXException e) {
            transact.abort(transaction);
            Exception o = e.getException();
            if (o == null)
               o = e;
            throw new BadRequestException("Parsing exception: "
                    + o.getMessage());
         } catch (LockException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
         }
      }
   }
   
   public void doDelete(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException,IOException
   {
      XmldbURI pathUri = XmldbURI.create(request.getPath());
      XmldbURI srcUri = null;      
      Collection collection = broker.getCollection(pathUri);
      if (collection == null) {
         throw new BadRequestException("Collection "+request.getPath()+" does not exist.");
      }
      String id = request.getParameter("id");
      if (id==null) {
          // delete collection
          TransactionManager transact = broker.getBrokerPool().getTransactionManager();
          Txn transaction = transact.beginTransaction();
          try {
              broker.removeCollection(transaction, collection);
              transact.commit(transaction);
              response.setStatusCode(204);
          } finally {
              transact.abort(transaction);
          }
          return;
      }
      
      LOG.info("Deleting entry "+id+" in collection "+request.getPath());
      DocumentImpl feedDoc = null;
      TransactionManager transact = broker.getBrokerPool().getTransactionManager();
      Txn transaction = transact.beginTransaction();
      String currentDateTime = DateFormatter.toXSDDateTime(new Date());
      try {
         
         // Get the feed
         //LOG.info("Acquiring lock on feed document...");
         feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
          if (!feedDoc.getPermissions().validate(broker.getUser(), Permission.UPDATE))
              throw new PermissionDeniedException("Permission denied to update feed " + collection.getURI());
         feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);

         // Find the entry
         String uuid = id.substring(9);
         Collection entryCollection = broker.getCollection(pathUri.append(ENTRY_COLLECTION_URI));
         XmldbURI entryURI = entryURI(uuid);
         DocumentImpl entryDoc = entryCollection.getDocument(broker,entryURI);
         if (entryDoc==null) {
             throw new BadRequestException("Entry with id "+id+" cannot be found.");
          }
         
         Element entry = entryDoc.getDocumentElement();
         
         // Remove the media resource if there is one
         Element content = DOM.findChild(entry,Atom.NAMESPACE_STRING,"content");
         if (content!=null) {
            String src = content.getAttribute("src");
            LOG.debug("Found content element, checking for resource "+src);
            if (src!=null && src.indexOf('/')<0) {
               srcUri =XmldbURI.create(src);
               DocumentImpl resource = collection.getDocument(broker,srcUri);
               if (resource!=null) {
                  LOG.debug("Deleting resource "+src+" from "+request.getPath());
                  if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
                     collection.removeBinaryResource(transaction,broker,srcUri);
                  } else {
                     collection.removeXMLResource(transaction,broker,srcUri);
                  }
               }
            }
         }
         
         // Remove the entry
         entryCollection.removeXMLResource(transaction, broker, entryURI);

         // Update the feed time
         ElementImpl feedRoot = (ElementImpl)feedDoc.getDocumentElement();
         DOMDB.replaceTextElement(transaction,feedRoot,Atom.NAMESPACE_STRING,"updated",currentDateTime,true);

         // Store the change on the feed
         LOG.debug("Storing change...");
         broker.storeXMLResource(transaction, feedDoc);
         transact.commit(transaction);
         LOG.debug("Done!");
         response.setStatusCode(204);
      } catch (TriggerException ex) {
         transact.abort(transaction);
         throw new EXistException("Cannot delete media resource "+srcUri,ex);
      } catch (LockException ex) {
         transact.abort(transaction);
         throw new EXistException("Cannot acquire write lock.",ex);
      } finally {
         if (feedDoc!=null) {
            feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);
         }
      }
      
   }
   
   public void mergeEntry(final Txn transaction,final ElementImpl target,Element source,final String updated) {
      final List toRemove = new ArrayList();
      DOM.forEachChild(target,new NodeHandler() {
         public void process(Node parent, Node child) {
            if (child.getNodeType()==Node.ELEMENT_NODE) {
               String ns = child.getNamespaceURI();
               if (ns!=null && ns.equals(Atom.NAMESPACE_STRING)) {
                  String lname = child.getLocalName();
                  if (lname.equals("updated")) {
                     // Changed updated
                     DOMDB.replaceText(transaction,(ElementImpl)child,updated);
                  } else if (lname.equals("link")) {
                     String rel = ((Element)child).getAttribute("rel");
                     if (!rel.equals("edit") && !rel.equals("edit-media")) {
                        // remove it
                        toRemove.add(child);
                     }
                  } else if (!lname.equals("id") && !lname.equals("published")) {
                     // remove it
                     toRemove.add(child);
                  }
               } else {
                  // remove it
                  toRemove.add(child);
               }
            } else {
               toRemove.add(child);
            }
         }
      });
      for (Iterator childrenToRemove = toRemove.iterator(); childrenToRemove.hasNext(); ) {
         Node child = (Node)childrenToRemove.next();
         target.removeChild(transaction,child);
      }
      DOM.forEachChild(source,new NodeHandler() {
         public void process(Node parent,Node child) {
            if (child.getNodeType()==Node.ELEMENT_NODE) {
               String ns = child.getNamespaceURI();
               if (ns!=null && ns.equals(Atom.NAMESPACE_STRING)) {
                  String lname = child.getLocalName();
                  
                  // Skip server controls updated, published, and id elements
                  if (lname.equals("updated") ||
                      lname.equals("published") ||
                      lname.equals("id")) {
                     return;
                  }
                  // Skip the edit link relations
                  if (lname.equals("link")) {
                     String rel = ((Element)child).getAttribute("rel");
                     if (rel.equals("edit") || rel.equals("edit-media")) {
                        return;
                     }
                  }
               }
               DOMDB.appendChild(transaction,target,child);
            }
         }
      });
   }
   
   public void mergeFeed(final DBBroker broker,final Txn transaction,final ElementImpl target,Element source,final String updated) {
      final DocumentImpl ownerDocument = (DocumentImpl)target.getOwnerDocument();
      final List toRemove = new ArrayList();
      DOM.forEachChild(target,new NodeHandler() {
         public void process(Node parent, Node child) {
            if (child.getNodeType()==Node.ELEMENT_NODE) {
               String ns = child.getNamespaceURI();
               if (ns!=null){
                   String lname = child.getLocalName();
                   if (ns.equals(Atom.NAMESPACE_STRING)) {
                	   if (lname.equals("updated")) {
                          // Changed updated
                          DOMDB.replaceText(transaction,(ElementImpl)child,updated);
                       } else if (lname.equals("link")) {
                     	  Element echild = (Element)child;
                     	  String rel = echild.getAttribute("rel");
                     	  if (!rel.equals("edit")) {
                             // remove it
                     		  toRemove.add(child);
                         }
                       } else if (!lname.equals("id") && !lname.equals("published")) {
                           // remove it
                           toRemove.add(child);
                       	}
                    } else {
                       // remove it
                       toRemove.add(child);
                    }
               } else {
                   // remove it
                   toRemove.add(child);
               }
            } else {
               // remove it
               toRemove.add(child);
            }
         }
      });
      
      for (Iterator childrenToRemove = toRemove.iterator(); childrenToRemove.hasNext(); ) {
         Node child = (Node)childrenToRemove.next();
         target.removeChild(transaction,child);
      }
      
      NodeList nl = source.getChildNodes();
      
      for (int i=0; i<nl.getLength(); i++) {
         Node child = nl.item(i);
         if (child.getNodeType()==Node.ELEMENT_NODE) {
            String ns = child.getNamespaceURI();
            if (ns!=null && ns.equals(Atom.NAMESPACE_STRING)) {
               String lname = child.getLocalName();

               // Skip server controls updated, published, and id elements
               if (lname.equals("updated") ||
                   lname.equals("published") ||
                   lname.equals("id")) {
                  continue;
               }
               // Skip the edit link relations
               if (lname.equals("link")) {
                  String rel = ((Element)child).getAttribute("rel");
                  if (rel.equals("edit")) {
                     continue;
                  }
               }
            }
            DOMDB.appendChild(transaction,target,child);
         }
      }
      ownerDocument.getMetadata().clearIndexListener();
      ownerDocument.getMetadata().setLastModified(System.currentTimeMillis());
   }


   protected Element findLink(Element parent,String rel) {
      NodeList nl = parent.getElementsByTagNameNS(Atom.NAMESPACE_STRING,"link");
      for (int i=0; i<nl.getLength(); i++) {
         Element link = (Element)nl.item(i);
         if (link.getAttribute("rel").equals(rel)) {
            return link;
         }
      }
      return null;
   }

   /**
    * Apply permissions to a collection. Owner, owner group and access permissions
    * can be set when creating a new feed by passing an element &lt;exist:permissions&gt;
    * in the document, e.g.:
    *
    * <pre>&lt;exist:permissions mode="0775" owner="editor" group="users"/&gt;</pre>
    */
   protected void setPermissions(DBBroker broker, Element parent, Collection collection) throws LockException, PermissionDeniedException {
      Element element = DOM.findChild(parent, Namespaces.EXIST_NS, "permissions");
      if (element != null) {
         String mode = element.getAttribute("mode");
         if (mode != null) {
            try {
               int permissions = Integer.parseInt(mode, 8);
               collection.setPermissions(permissions);
            } catch (NumberFormatException e) {
               try {
                  collection.setPermissions(mode);
               } catch (SyntaxException e1) {
                  throw new PermissionDeniedException("syntax error for mode attribute in exist:permissions element");
               }
            }
         }
         String owner = element.getAttribute("owner");
         org.exist.security.SecurityManager securityMan = broker.getBrokerPool().getSecurityManager();
         if (!securityMan.hasUser(owner))
            throw new PermissionDeniedException("Failed to change feed owner: user " + owner + " does not exist.");
         collection.getPermissions().setOwner(owner);
         String group = element.getAttribute("group");
         if (!securityMan.hasGroup(group))
            securityMan.addGroup(group);

         parent.removeChild(element);
      }
   }

   protected void setPermissions(DBBroker broker, Element parent, DocumentImpl resource) throws LockException, PermissionDeniedException {
      Element element = DOM.findChild(parent, Namespaces.EXIST_NS, "permissions");
      if (element != null) {
         String mode = element.getAttribute("mode");
         try {
            int permissions = Integer.parseInt(mode, 8);
            resource.setPermissions(permissions);
         } catch (NumberFormatException e) {
            try {
               resource.setPermissions(mode);
            } catch (SyntaxException e1) {
               throw new PermissionDeniedException("syntax error for mode attribute in exist:permissions element");
            }
         }
         String owner = element.getAttribute("owner");
         org.exist.security.SecurityManager securityMan = broker.getBrokerPool().getSecurityManager();
         if (!securityMan.hasUser(owner))
            throw new PermissionDeniedException("Failed to change feed owner: user " + owner + " does not exist.");
         resource.getPermissions().setOwner(owner);
         String group = element.getAttribute("group");
         if (!securityMan.hasGroup(group))
            securityMan.addGroup(group);
         
         parent.removeChild(element);
      }
   }

   protected XmldbURI entryURI(String uuid){
	   return XmldbURI.create(uuid + ".entry.atom");
   }
		   
   public static Element generateMediaEntry(String id, String created, String title, String filename, String mimeType) 
      throws ParserConfigurationException
   {
      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      docFactory.setNamespaceAware(true);
      Document owner = docFactory.newDocumentBuilder().getDOMImplementation().createDocument(Atom.NAMESPACE_STRING,"entry",null);
      Element entry = owner.getDocumentElement();
      
      Element idE = owner.createElementNS(Atom.NAMESPACE_STRING,"id");
      idE.appendChild(owner.createTextNode(id));
      entry.appendChild(idE);
      
      Element publishedE = owner.createElementNS(Atom.NAMESPACE_STRING,"published");
      publishedE.appendChild(owner.createTextNode(created));
      entry.appendChild(publishedE);
      
      Element updatedE = owner.createElementNS(Atom.NAMESPACE_STRING,"updated");
      updatedE.appendChild(owner.createTextNode(created));
      entry.appendChild(updatedE);
      
      Element titleE = owner.createElementNS(Atom.NAMESPACE_STRING,"title");
      titleE.appendChild(owner.createTextNode(title));
      entry.appendChild(titleE);
      
      Element linkE = owner.createElementNS(Atom.NAMESPACE_STRING,"link");
      linkE.setAttribute("rel","edit");
      linkE.setAttribute("type",Atom.MIME_TYPE);
      linkE.setAttribute("href","?id="+id);
      entry.appendChild(linkE);
      
      linkE = owner.createElementNS(Atom.NAMESPACE_STRING,"link");
      linkE.setAttribute("rel","edit-media");
      linkE.setAttribute("type",mimeType);
      linkE.setAttribute("href",filename);
      entry.appendChild(linkE);
      
      Element contentE = owner.createElementNS(Atom.NAMESPACE_STRING,"content");
      entry.appendChild(contentE);
      contentE.setAttribute("src",filename);
      contentE.setAttribute("type",mimeType);
      
      return entry;
   }
   
}
