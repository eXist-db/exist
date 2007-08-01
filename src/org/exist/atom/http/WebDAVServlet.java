/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id: WebDAVServlet.java 2782 2006-02-25 18:55:49Z dizzzz $
 */
package org.exist.atom.http;

import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.atom.Atom;
import org.exist.atom.modules.AtomProtocol;
import org.exist.atom.util.DOM;
import org.exist.atom.util.DOMDB;
import org.exist.atom.util.DateFormatter;
import org.exist.atom.util.NodeHandler;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.http.webdav.WebDAV;
import org.exist.http.webdav.WebDAVMethod;
import org.exist.http.webdav.WebDAVMethodFactory;
import org.exist.http.webdav.methods.Copy;
import org.exist.http.webdav.methods.Delete;
import org.exist.http.webdav.methods.Mkcol;
import org.exist.http.webdav.methods.Move;
import org.exist.http.webdav.methods.Put;
import org.exist.security.PermissionDeniedException;
import org.exist.security.UUIDGenerator;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.StringValue;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Provides a WebDAV interface that also maintains the atom feed if it exists
 * in the directory.
 *
 * @author wolf
 * @author Alex Milowski
 */
public class WebDAVServlet extends HttpServlet {

   protected final static Logger LOG = Logger.getLogger(WebDAVServlet.class);
   
   class FindEntryByResource implements NodeHandler {
      String path;
      Element matching;
      FindEntryByResource(String path) {
         this.path = path;
         this.matching = null;
      }
      public void process(Node parent,Node child) {
         Element entry = (Element)child;
         NodeList nl = entry.getElementsByTagNameNS(Atom.NAMESPACE_STRING,"content");
         if (nl.getLength()!=0) {
            if (path.equals(((Element)nl.item(0)).getAttribute("src"))) {
               matching = entry;
            }
         }
      }
      
      public Element getEntry() {
         return matching;
      }
      
   }
   
   class AtomWebDAVMethodFactory extends WebDAVMethodFactory {
       public WebDAVMethod create(String method, BrokerPool pool) {
          if (method.equals("PUT")) {
             return new AtomPut(pool);
          } else if (method.equals("DELETE")) {
             return new AtomDelete(pool);
          } else if (method.equals("MKCOL")) {
             return new AtomMkcol(pool);
          } else if (method.equals("MOVE")) {
             return new AtomMove(pool);
          } else if (method.equals("COPY")) {
             return new AtomCopy(pool);
          } else {
             return super.create(method,pool);
          }
       }
   }
   
   class AtomPut extends Put {
      AtomPut(BrokerPool pool) {
         super(pool);
      }
      public void process(User user, HttpServletRequest request, HttpServletResponse response, XmldbURI path) 
         throws ServletException, IOException 
      {
         XmldbURI filename = path.lastSegment();
         XmldbURI collUri = path.removeLastSegment();
         DBBroker broker = null;
         Collection collection = null;
         boolean updateToExisting = false;
         try {
	         try {
	            broker = pool.get(user);
	            collection = broker.openCollection(collUri, Lock.READ_LOCK);
	            updateToExisting = collection.getDocument(broker,filename)!=null;
	         } catch (EXistException ex) {
	            throw new ServletException("Exception while getting a broker from the pool.",ex);
	         }
	         
	         super.process(user,request,response,path);
	
	         if (updateToExisting) {
	            // We do nothing right now
	            LOG.debug("Update to existing resource, skipping feed update.");
	            return;
	         }
         } finally {
            if (collection!=null) {
	               collection.release(Lock.READ_LOCK);
	        }        	 
         }
         
         TransactionManager transact = pool.getTransactionManager();
         Txn transaction = transact.beginTransaction();
         DocumentImpl feedDoc = null;
         try {
            
            LOG.debug("Atom PUT collUri='"+collUri+"';  path="+filename+"';" );
            
            if (collection == null || collection.hasChildCollection(filename)) {
               // We're already in an error state from the WebDAV action so just return
               LOG.debug("No collection or subcollection already exists.");
               transact.abort(transaction);
               return;
            }
            
            MimeType mime;
            String contentType = request.getContentType();
            if (contentType == null) {
               mime = MimeTable.getInstance().getContentTypeFor(filename);
               if (mime != null) {
                  contentType = mime.getName();
               }
            } else {
               int p = contentType.indexOf(';');
               if (p > -1) {
                  contentType = StringValue.trimWhitespace(contentType.substring(0, p));
               }
               mime = MimeTable.getInstance().getContentType(contentType);
            }
            
            if (mime == null) {
               mime = MimeType.BINARY_TYPE;
            }
            
            LOG.debug("Acquiring lock on feed document...");
            feedDoc = collection.getDocument(broker,AtomProtocol.FEED_DOCUMENT_URI);
            feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
            
            String title = request.getHeader("Title");
            if (title==null) {
               title = filename.toString();
            }
            
            String created = DateFormatter.toXSDDateTime(new Date());
            ElementImpl feedRoot = (ElementImpl)feedDoc.getDocumentElement();
            DOMDB.replaceTextElement(transaction,feedRoot,Atom.NAMESPACE_STRING,"updated",created,true);
            String id = "urn:uuid:"+UUIDGenerator.getUUID();
            Element mediaEntry = AtomProtocol.generateMediaEntry(id,created,title,filename.toString(),mime.getName());
            DOMDB.appendChild(transaction,feedRoot,mediaEntry);
            broker.storeXMLResource(transaction, feedDoc);
            transact.commit(transaction);
         } catch (TransactionException ex) {
            transact.abort(transaction);
            throw new ServletException("Cannot commit transaction.",ex);
         } catch (ParserConfigurationException ex) {
            transact.abort(transaction);
            throw new ServletException("DOM implementation is misconfigured.",ex);
         } catch (LockException ex) {
            transact.abort(transaction);
            throw new ServletException("Cannot acquire write lock.",ex);
         } finally {
            if (feedDoc!=null) {
               feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);
            }
            if (collection!=null) {
               collection.release(Lock.READ_LOCK);
            }
            pool.release(broker);
         }
      }
   }
   
   class AtomDelete extends Delete {
      AtomDelete(BrokerPool pool) {
         super(pool);
      }
      public void process(User user, HttpServletRequest request, HttpServletResponse response, XmldbURI path) 
         throws ServletException, IOException 
      {
         super.process(user,request,response,path);
         TransactionManager transact = pool.getTransactionManager();
         Txn transaction = transact.beginTransaction();
         DocumentImpl feedDoc = null;
         DBBroker broker = null;
         Collection collection = null;
         try {
            
            broker = pool.get(user);
            
            XmldbURI filename = path.lastSegment();
            XmldbURI collUri = path.removeLastSegment();
            
            LOG.debug("Atom DELETE collUri='"+collUri+"';  path="+filename+"';" );
            
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            if (collection == null || collection.hasChildCollection(filename)) {
               // We're already in an error state from the WebDAV action so just return
               transact.abort(transaction);
               return;
            }
            
            feedDoc = collection.getDocument(broker,AtomProtocol.FEED_DOCUMENT_URI);
            feedDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
            // Find the entry
            FindEntryByResource finder = new FindEntryByResource(filename.toString());
            DOM.findChildren(feedDoc.getDocumentElement(),Atom.NAMESPACE_STRING,"entry",finder);
            Element entry = finder.getEntry();

            if (entry!=null) {
               // Remove the entry
               ElementImpl feedRoot = (ElementImpl)feedDoc.getDocumentElement();
               feedRoot.removeChild(transaction,entry);

               // Update the feed time
               String currentDateTime = DateFormatter.toXSDDateTime(new Date());
               DOMDB.replaceTextElement(transaction,feedRoot,Atom.NAMESPACE_STRING,"updated",currentDateTime,true);

               // Store the change on the feed
               LOG.debug("Storing change...");
               broker.storeXMLResource(transaction, feedDoc);

               transact.commit(transaction);
            } else {
               // the entry is missing, so ignore
               transact.abort(transaction);
            }
         } catch (TransactionException ex) {
            transact.abort(transaction);
            throw new ServletException("Cannot commit transaction.",ex);
         } catch (EXistException ex) {
            transact.abort(transaction);
            throw new ServletException("Exception while getting a broker from the pool.",ex);
         } catch (LockException ex) {
            transact.abort(transaction);
            throw new ServletException("Cannot acquire write lock.",ex);
         } finally {
            if (feedDoc!=null) {
               feedDoc.getUpdateLock().release(Lock.WRITE_LOCK);
            }
            if (collection!=null) {
               collection.release(Lock.READ_LOCK);
            }
            pool.release(broker);
         }
         
      }
   }
   
   class AtomMkcol extends Mkcol {
      AtomMkcol(BrokerPool pool) {
         super(pool);
      }
      public void process(User user, HttpServletRequest request, HttpServletResponse response, XmldbURI path) 
         throws ServletException, IOException 
      {
         DBBroker broker = null;
         Collection collection = null;
         try {
        	 try {
	            broker = pool.get(user);
	            collection = broker.openCollection(path, Lock.READ_LOCK);
	            if (collection != null) {
	               response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
	                       "collection " + request.getPathInfo() + " already exists");
	               return;
	            }
        	 } finally {
 	            if (collection != null) 
 	               collection.release(Lock.READ_LOCK);        		 
        	 }
         } catch (EXistException ex) {
            throw new ServletException("Exception while getting a broker from the pool.",ex);
         }
         super.process(user,request,response,path);
         collection = broker.openCollection(path, Lock.READ_LOCK);
         if (collection==null) {
            pool.release(broker);
            return;
         }
         DocumentImpl feedDoc = null;
         TransactionManager transact = broker.getBrokerPool().getTransactionManager();
         Txn transaction = transact.beginTransaction();
         try {
            String id = UUIDGenerator.getUUID();
            String currentDateTime = DateFormatter.toXSDDateTime(new Date());
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(true);
            Document doc = docFactory.newDocumentBuilder().getDOMImplementation().createDocument(Atom.NAMESPACE_STRING,"feed",null);
            Element root = doc.getDocumentElement();
            DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"id","urn:uuid:"+id,false);
            DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"updated",currentDateTime,false);
            DOM.replaceTextElement(root,Atom.NAMESPACE_STRING,"title",path.lastSegment().getCollectionPath(),false);
            Element editLink = doc.createElementNS(Atom.NAMESPACE_STRING,"link");
            editLink.setAttribute("rel","edit");
            editLink.setAttribute("type",Atom.MIME_TYPE);
            editLink.setAttribute("href","#");
            root.appendChild(editLink);
            IndexInfo info = collection.validateXMLResource(transaction,broker,AtomProtocol.FEED_DOCUMENT_URI,doc);
            //TODO : we should probably unlock the collection here
            collection.store(transaction,broker,info,doc,false);
            transact.commit(transaction);
         } catch (ParserConfigurationException ex) {
            transact.abort(transaction);
            throw new ServletException("SAX error: "+ex.getMessage(),ex);
         } catch (SAXException ex) {
            transact.abort(transaction);
            throw new ServletException("SAX error: "+ex.getMessage(),ex);
         } catch (TriggerException ex) {
            transact.abort(transaction);
            throw new ServletException("Trigger failed: "+ex.getMessage(),ex);
         } catch (LockException ex) {
            transact.abort(transaction);
            throw new ServletException("Cannot acquire write lock.",ex);
         } catch (PermissionDeniedException ex) {
            transact.abort(transaction);
            throw new ServletException("Permission denied.",ex);
         } catch (EXistException ex) {
            transact.abort(transaction);
            throw new ServletException("Database exception",ex);
         } finally {
            collection.release(Lock.READ_LOCK);
            pool.release(broker);
         }
      }
   }
   
   class AtomMove extends Move {
      AtomMove(BrokerPool pool) {
         super(pool);
      }
   }
   
   class AtomCopy extends Copy {
      AtomCopy(BrokerPool pool) {
         super(pool);
      }
   }
   
   private WebDAV webdav;
   /** id of the database registred against the BrokerPool */
   protected String databaseid = BrokerPool.DEFAULT_INSTANCE_NAME;
   
   
        /* (non-Javadoc)
         * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
         */
   public void init(ServletConfig config) throws ServletException {
      super.init(config);
      // <frederic.glorieux@ajlsm.com> to allow multi-instance webdav server,
      // use a databaseid everywhere
      String id = config.getInitParameter("database-id");
      if (id != null && !"".equals(id)) this.databaseid=id;
      
      int authMethod = WebDAV.DIGEST_AUTH;
      String param = config.getInitParameter("authentication");
      
      if(param != null && "basic".equalsIgnoreCase(param))
         authMethod = WebDAV.BASIC_AUTH;
      
      webdav = new WebDAV(authMethod, this.databaseid,new AtomWebDAVMethodFactory());
   }
   
        /* (non-Javadoc)
         * @see javax.servlet.http.HttpServlet#service(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
         */
   protected void service(HttpServletRequest request, HttpServletResponse response)
   throws ServletException, IOException {
      dumpHeaders(request);
      webdav.process(request, response);
   }
   
   private void dumpHeaders(HttpServletRequest request) {
      System.out.println("-------------------------------------------------------");
      System.out.println(request.getMethod()+" "+request.getPathInfo());
      for(Enumeration e = request.getHeaderNames(); e.hasMoreElements(); ) {
         String header = (String)e.nextElement();
         System.out.println(header + " = " + request.getHeader(header));
      }
   }
}
