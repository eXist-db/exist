/*
 * AtomProtocol.java
 *
 * Created on June 16, 2006, 11:39 AM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.modules;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.atom.Atom;
import org.exist.atom.IncomingMessage;
import org.exist.atom.OutgoingMessage;
import org.exist.atom.util.DOM;
import org.exist.atom.util.DOMDB;
import org.exist.atom.util.NodeHandler;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeListImpl;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException; 
import org.xml.sax.SAXParseException;


/**
 *
 * @author R. Alexander Milowski
 */
public class AtomProtocol extends AtomFeeds implements Atom {
   
   protected final static Logger LOG = Logger.getLogger(AtomProtocol.class);
   static final String FEED_DOCUMENT_NAME = ".feed.atom";
   static final XmldbURI FEED_DOCUMENT_URI = XmldbURI.create(FEED_DOCUMENT_NAME);
   
   class FindEntry implements NodeHandler {
      String id;
      Element matching;
      FindEntry(String id) {
         this.id = id;
      }
      public void process(Node parent,Node child) {
         Element entry = (Element)child;
         NodeList nl = entry.getElementsByTagNameNS(Atom.NAMESPACE_STRING,"id");
         if (nl.getLength()!=0) {
            String value = DOM.textContent(nl.item(0));
            if (value.equals(id)) {
               matching = entry;
            }
         }
      }
      
      public Element getEntry() {
         return matching;
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
         int equals = contentType.indexOf('=',semicolon);
         if (equals>0) {
            String param = contentType.substring(semicolon+1,equals).trim();
            if (param.compareToIgnoreCase("charset=")==0) {
               charset = param.substring(equals+1).trim();
            }
         }
      }
      
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
         if (root.getLocalName().equals("entry")) {
            if (collection == null) {
               throw new BadRequestException("Collection "+request.getPath()+" does not exist.");
            }
            LOG.info("Adding entry to "+request.getPath());
            DocumentImpl feedDoc = null;
            TransactionManager transact = broker.getBrokerPool().getTransactionManager();
            Txn transaction = transact.beginTransaction();
            String id = "urn:uuid:"+UUID.randomUUID();
            String currentDateTime = toXSDDateTime(new Date());
            Element publishedE = DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"published",currentDateTime,true);
            DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"updated",currentDateTime,true);
            DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"id",id,true);
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
               LOG.info("Acquiring lock on feed document...");
               feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
               ElementImpl feedRoot = (ElementImpl)feedDoc.getDocumentElement();
               
               // Lock the feed
               feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
               
               // Append the entry
               NodeListImpl nl = new NodeListImpl(1);
               nl.add(root);
               feedRoot.appendChildren(transaction,nl,-1);
               
               // Update the updated element
               DOMDB.replaceTextElement(transaction,feedRoot,Atom.NAMESPACE_STRING,"updated",currentDateTime,true);

               // Store the changes
               LOG.info("Storing change...");
               broker.storeXMLResource(transaction, feedDoc);
               transact.commit(transaction);
               
               LOG.info("Done!");
               
               response.setStatusCode(201);
               response.setHeader("Location",request.getModuleBase()+request.getPath()+"?id="+id);
               response.setContentType(Atom.MIME_TYPE+"; charset="+charset);
               OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream(),charset);
               Transformer identity = TransformerFactory.newInstance().newTransformer();
               identity.transform(new DOMSource(doc),new StreamResult(w));
               w.flush();
               w.close();
               
            } catch (LockException ex) {
               transact.abort(transaction);
               throw new EXistException("Cannot acquire write lock.",ex);
            } catch (IOException ex) {
               throw new EXistException("Internal error while serializing result.",ex);
            } catch (TransformerException ex) {
               throw new EXistException("Serialization error.",ex);
            } finally {
               if (feedDoc!=null) {
                  feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);
               }
            }
         } else if (root.getLocalName().equals("feed")) {
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
                  broker.saveCollection(transaction, collection);
               }
               UUID id = UUID.randomUUID();
               String currentDateTime = toXSDDateTime(new Date());
               DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"updated",currentDateTime,true);
               DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"id","urn:uuid:"+id,true);
               Element editLink = findLink(root,"edit");
               if (editLink!=null) {
                  throw new BadRequestException("An edit link relation cannot be specified in the entry.");
               }
               editLink = doc.createElementNS(Atom.NAMESPACE_STRING,"link");
               editLink.setAttribute("rel","edit");
               editLink.setAttribute("type",Atom.MIME_TYPE);
               editLink.setAttribute("href","#");
               IndexInfo info = collection.validateXMLResource(transaction,broker,FEED_DOCUMENT_URI,doc);
               collection.store(transaction,broker,info,doc,false);
               transact.commit(transaction);
            } catch (SAXException ex) {
               transact.abort(transaction);
               throw new EXistException("SAX error: "+ex.getMessage(),ex);
            } catch (TriggerException ex) {
               transact.abort(transaction);
               throw new EXistException("Trigger failed: "+ex.getMessage(),ex);
            } catch (LockException ex) {
               transact.abort(transaction);
               throw new EXistException("Cannot acquire write lock.",ex);
            }
         } else {
            throw new BadRequestException("Unexpected element: {http://www.w3.org/2005/Atom}"+root.getLocalName());
         }
      } else {
         if (collection == null) {
            throw new BadRequestException("Collection "+request.getPath()+" does not exist.");
         }
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
            DocumentImpl feedDoc = null;
            try {
               LOG.info("Acquiring lock on feed document...");
               feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
               feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
               String title = request.getHeader("Title");
               if (title==null) {
                  title = filename;
               }
               String created = toXSDDateTime(new Date());
               ElementImpl feedRoot = (ElementImpl)feedDoc.getDocumentElement();
               DOMDB.replaceTextElement(transaction,feedRoot,Atom.NAMESPACE_STRING,"updated",created,true);
               DOMDB.appendChild(transaction,feedRoot,generateMediaEntry(created,title,filename,mime.getName()));
               LOG.info("Storing change...");
               broker.storeXMLResource(transaction, feedDoc);
               transact.commit(transaction);
               LOG.info("Done!");
               response.setStatusCode(201);
            } catch (ParserConfigurationException ex) {
               transact.abort(transaction);
               throw new EXistException("DOM implementation is misconfigured.",ex);
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
         } catch (SAXException e) {
            transact.abort(transaction);
            Exception o = e.getException();
            if (o == null)
               o = e;
            throw new BadRequestException("Parsing exception: "
                    + o.getMessage());
         } catch (TriggerException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
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
            Collection collection = broker.getCollection(pathUri);
            DocumentImpl feedDoc = null;
            TransactionManager transact = broker.getBrokerPool().getTransactionManager();
            Txn transaction = transact.beginTransaction();
            
            // Prepare the feed w/ the additional metadata 
            
            UUID id = UUID.randomUUID();
            DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"updated",toXSDDateTime(new Date()),true);
            DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"id","urn:uuid:"+id,true);
            try {
               if (collection != null) {
                  feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
                  if (feedDoc!=null) {
                     throw new BadRequestException("Collection at "+request.getPath()+" already exists.");
                  }
               } else {
                  collection = broker.getOrCreateCollection(transaction,pathUri);
               }
               IndexInfo info = collection.validateXMLResource(transaction,broker,FEED_DOCUMENT_URI,doc);
               info.getDocument().getMetadata().setMimeType(Atom.MIME_TYPE);
               collection.store(transaction,broker,info,doc,false);
               transact.commit(transaction);
            } catch (SAXException ex) {
               transact.abort(transaction);
               throw new EXistException("SAX error: "+ex.getMessage(),ex);
            } catch (TriggerException ex) {
               transact.abort(transaction);
               throw new EXistException("Trigger failed: "+ex.getMessage(),ex);
            } catch (LockException ex) {
               transact.abort(transaction);
               throw new EXistException("Cannot acquire write lock.",ex);
            }
         } else if (root.getLocalName().equals("entry")) {
            Collection collection = broker.getCollection(pathUri);
            if (collection == null) {
               throw new BadRequestException("Collection "+request.getPath()+" does not exist.");
            }
            String id = request.getParameter("id");
            if (id==null) {
               throw new BadRequestException("The 'id' parameter for the entry is missing.");
            }
            LOG.info("Updating entry "+id+" in collection "+request.getPath());
            DocumentImpl feedDoc = null;
            TransactionManager transact = broker.getBrokerPool().getTransactionManager();
            Txn transaction = transact.beginTransaction();
            String currentDateTime = toXSDDateTime(new Date());
            try {
               // Get the feed
               LOG.info("Acquiring lock on feed document...");
               feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
               feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);

               // Find the entry
               FindEntry finder = new FindEntry(id);
               DOM.findChildren(feedDoc.getDocumentElement(),Atom.NAMESPACE_STRING,"entry",finder);
               Element entry = finder.getEntry();
               mergeEntry(transaction,(ElementImpl)entry,root,currentDateTime);

               // Update the feed time
               DOMDB.replaceTextElement(transaction,(ElementImpl)feedDoc.getDocumentElement(),Atom.NAMESPACE_STRING,"updated",currentDateTime,true);

               // Store the feed
               broker.storeXMLResource(transaction, feedDoc);
               transact.commit(transaction);
               
               // Send back the changed entry
               response.setStatusCode(200);
               response.setContentType(Atom.MIME_TYPE+"; charset="+charset);
               OutputStreamWriter w = new OutputStreamWriter(response.getOutputStream(),charset);
               Transformer identity = TransformerFactory.newInstance().newTransformer();
               identity.transform(new DOMSource(entry),new StreamResult(w));
               w.flush();
               w.close();
            } catch (LockException ex) {
               transact.abort(transaction);
               throw new EXistException("Cannot acquire write lock.",ex);
            } catch (IOException ex) {
               throw new EXistException("I/O exception during serialization of entry response.",ex);
            } catch (TransformerException ex) {
               throw new EXistException("Serialization error.",ex);
            } finally {
               if (feedDoc!=null) {
                  feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);
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
            Collection collection = broker.getCollection(collUri);
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
            response.setStatusCode(200);
            transact.commit(transaction);
         } catch (IOException ex) {
            transact.abort(transaction);
            throw new EXistException("I/O error while handling temporary files.",ex);
         } catch (SAXParseException e) {
            transact.abort(transaction);
            throw new BadRequestException("Parsing exception at "
                    + e.getLineNumber() + "/" + e.getColumnNumber() + ": "
                    + e.toString());
         } catch (SAXException e) {
            transact.abort(transaction);
            Exception o = e.getException();
            if (o == null)
               o = e;
            throw new BadRequestException("Parsing exception: "
                    + o.getMessage());
         } catch (TriggerException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
         } catch (LockException e) {
            transact.abort(transaction);
            throw new PermissionDeniedException(e.getMessage());
         }
      }
   }
   
   public void doDelete(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
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
         broker.removeCollection(transaction, collection);
         transact.commit(transaction);
         response.setStatusCode(200);
         return;
      }
      
      LOG.info("Deleting entry "+id+" in collection "+request.getPath());
      DocumentImpl feedDoc = null;
      TransactionManager transact = broker.getBrokerPool().getTransactionManager();
      Txn transaction = transact.beginTransaction();
      String currentDateTime = toXSDDateTime(new Date());
      try {
         
         // Get the feed
         LOG.info("Acquiring lock on feed document...");
         feedDoc = collection.getDocument(broker,FEED_DOCUMENT_URI);
         feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);

         // Find the entry
         FindEntry finder = new FindEntry(id);
         DOM.findChildren(feedDoc.getDocumentElement(),Atom.NAMESPACE_STRING,"entry",finder);
         Element entry = finder.getEntry();
         
         if (entry==null) {
            transact.abort(transaction);
            throw new BadRequestException("Entry with id "+id+" cannot be found.");
         }
         
         // Remove the media resource if there is one
         Element content = DOM.findChild(entry,Atom.NAMESPACE_STRING,"content");
         if (content!=null) {
            String src = content.getAttribute("src");
            LOG.info("Found content element, checking for resource "+src);
            if (src!=null && src.indexOf('/')<0) {
               srcUri =XmldbURI.create(src);
               DocumentImpl resource = collection.getDocument(broker,srcUri);
               if (resource!=null) {
                  LOG.info("Deleting resource "+src+" from "+request.getPath());
                  if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
                     collection.removeBinaryResource(transaction,broker,srcUri);
                  } else {
                     collection.removeXMLResource(transaction,broker,srcUri);
                  }
               }
            }
         }
         
         // Remove the entry
         ElementImpl feedRoot = (ElementImpl)feedDoc.getDocumentElement();
         feedRoot.removeChild(transaction,entry);

         // Update the feed time
         DOMDB.replaceTextElement(transaction,feedRoot,Atom.NAMESPACE_STRING,"updated",currentDateTime,true);

         // Store the change on the feed
         LOG.info("Storing change...");
         broker.storeXMLResource(transaction, feedDoc);
         transact.commit(transaction);
         LOG.info("Done!");
         response.setStatusCode(200);
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
                        target.removeChild(transaction,child);
                     }
                  } else if (!lname.equals("id") && !lname.equals("published")) {
                     // remove it
                     target.removeChild(transaction,child);
                  }
               } else {
                  // remove it
                  target.removeChild(transaction,child);
               }
            }
         }
      });
      final Document ownerDocument = target.getOwnerDocument();
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
                     if (!rel.equals("edit") && !rel.equals("edit-media")) {
                        return;
                     }
                  }
               }
               target.appendChild(child);
               target.appendChild(ownerDocument.createTextNode("\n"));
            }
         }
      });
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
   
   protected Element generateMediaEntry(String created,String title,String filename,String mimeType) 
      throws ParserConfigurationException
   {

      String id = "urn:uuid:"+UUID.randomUUID();
      
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
