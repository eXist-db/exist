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
import org.exist.atom.Atom;
import org.exist.atom.IncomingMessage;
import org.exist.atom.OutgoingMessage;
import org.exist.collections.Collection;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.StringSource;
import org.exist.source.URLSource;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.value.Sequence;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 * @author R. Alexander Milowski
 */
public class Query extends AtomModuleBase implements Atom {
   
   protected final static Logger LOG = Logger.getLogger(Query.class);
   MimeType xqueryMimeType;
   
   public class MethodConfiguration {
      String contentType;
      URLSource querySource;
      MethodConfiguration() {
         querySource = null;
         contentType = Atom.MIME_TYPE;
      }
      public void setContentType(String value) {
         this.contentType = value;
      }

      public String getContentType() {
         return contentType;
      }

      public URL getQuerySource() {
         return querySource.getURL();
      }

      public void setQuerySource(URL source) {
         this.querySource = source==null ? null : new URLSource(source);
      }
   }
   
   boolean allowQueryPost;

   Map methods;
   MethodConfiguration get;
   MethodConfiguration post;
   MethodConfiguration put;
   MethodConfiguration delete;
   MethodConfiguration head;
   
   /** Creates a new instance of AtomProtocol */
   public Query() {
      xqueryMimeType = MimeTable.getInstance().getContentType("application/xquery");
      methods = new HashMap();
      methods.put("GET",new MethodConfiguration());
      methods.put("POST",new MethodConfiguration());
      methods.put("PUT",new MethodConfiguration());
      methods.put("DELETE",new MethodConfiguration());
      methods.put("HEAD",new MethodConfiguration());
      allowQueryPost = false;
   }
   
   public MethodConfiguration getMethodConfiguration(String name) {
      return (MethodConfiguration)methods.get(name);
   }
   
   public void init(Context context) 
      throws EXistException
   {
      super.init(context);
      get = (MethodConfiguration)methods.get("GET");
      post = (MethodConfiguration)methods.get("POST");
      put = (MethodConfiguration)methods.get("PUT");
      delete = (MethodConfiguration)methods.get("DELETE");
      head = (MethodConfiguration)methods.get("HEAD");
   }
   
   public void doGet(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      if (get.querySource!=null) {
         doQuery(broker,request,response,get);
      } else {
         super.doGet(broker,request,response);
      }
   }
   public void doPut(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      if (put.querySource!=null) {
         // TODO: handle put body
         doQuery(broker,request,response,put);
      } else {
         super.doGet(broker,request,response);
      }
   }
   public void doDelete(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      if (delete.querySource!=null) {
         doQuery(broker,request,response,delete);
      } else {
         super.doGet(broker,request,response);
      }
   }
   public void doHead(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      if (head.querySource!=null) {
         doQuery(broker,request,response,head);
      } else {
         super.doGet(broker,request,response);
      }
   }
   public void doPost(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      if (post.querySource!=null) {
         // TODO: handle post body
         doQuery(broker,request,response,post);
      } else if (allowQueryPost) {
         Collection collection = broker.getCollection(XmldbURI.create(request.getPath()));
         if (collection == null) {
            throw new BadRequestException("Collection "+request.getPath()+" does not exist.");
         }

         XQuery xquery = broker.getXQueryService();

         XQueryContext context = xquery.newContext(AccessContext.REST);
        context.setModuleLoadPath(getContext().getModuleLoadPath());
          
         String contentType = request.getHeader("Content-Type");
         String charset = getContext().getDefaultCharset();

         MimeType mime = MimeType.XML_TYPE;
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

         if (!mime.isXMLType() && !mime.equals(xqueryMimeType)) {
            throw new BadRequestException("The xquery mime type is not an XML mime type nor application/xquery");
         }

         CompiledXQuery compiledQuery = null;
         try {
            StringBuffer builder = new StringBuffer();
            Reader r = new InputStreamReader(request.getInputStream(),charset);
            char [] buffer = new char[4096];
            int len;
            int count = 0;
            int contentLength = request.getContentLength();
            while ((len=r.read(buffer))>=0 && count<contentLength) {
               count += len;
               builder.append(buffer,0,len);
            }
            compiledQuery = xquery.compile(context, new StringSource(builder.toString()));
         } catch (XPathException ex) {
            throw new EXistException("Cannot compile xquery.",ex);
         } catch (IOException ex) {
            throw new EXistException("I/O exception while compiling xquery.",ex);
         }

         context.setStaticallyKnownDocuments(new XmldbURI[] { XmldbURI.create(request.getPath()).append(AtomProtocol.FEED_DOCUMENT_NAME) });

         try {
            Sequence resultSequence = xquery.execute(compiledQuery, null);
            if (resultSequence.isEmpty()) {
               throw new BadRequestException("No topic was found.");
            }
            response.setStatusCode(200);
            response.setContentType(Atom.MIME_TYPE+"; charset="+charset);
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
               LOG.fatal("Cannot read resource "+request.getPath(),ex);
               throw new EXistException("I/O error on read of resource "+request.getPath(),ex);
            } catch (SAXException saxe) {
               LOG.warn(saxe);
               throw new BadRequestException("Error while serializing XML: " + saxe.getMessage());
            }
            resultSequence.itemAt(0);
         } catch (XPathException ex) {
            throw new EXistException("Cannot execute xquery.",ex);
         }
      } else {
         super.doPost(broker,request,response);
      }
      
   }
   
   private void declareVariables(XQueryContext context, HttpServletRequest request, HttpServletResponse response) throws XPathException {
       RequestWrapper reqw = new HttpRequestWrapper(request, request.getCharacterEncoding(), request.getCharacterEncoding());
       ResponseWrapper respw = new HttpResponseWrapper(response);
       //context.declareNamespace(RequestModule.PREFIX, RequestModule.NAMESPACE_URI);
       context.declareVariable(RequestModule.PREFIX + ":request", reqw);
       context.declareVariable(ResponseModule.PREFIX + ":response", respw);
       context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession());
   }
   
   public void doQuery(DBBroker broker,IncomingMessage request,OutgoingMessage response,MethodConfiguration config)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      
      Collection collection = broker.getCollection(XmldbURI.create(request.getPath()));
      if (collection == null) {
         throw new BadRequestException("Collection "+request.getPath()+" does not exist.");
      }
      
      XQuery xquery = broker.getXQueryService();
      CompiledXQuery feedQuery = xquery.getXQueryPool().borrowCompiledXQuery(broker,config.querySource);

      XQueryContext context;
      if (feedQuery==null) {
         context = xquery.newContext(AccessContext.REST);
         context.setModuleLoadPath(getContext().getModuleLoadPath());
         try {
            feedQuery = xquery.compile(context, config.querySource);
         } catch (XPathException ex) {
            throw new EXistException("Cannot compile xquery "+config.querySource.getURL(),ex);
         } catch (IOException ex) {
            throw new EXistException("I/O exception while compiling xquery "+config.querySource.getURL(),ex);
         }
      } else {
         context = feedQuery.getContext();
         context.setModuleLoadPath(getContext().getModuleLoadPath());
      }
      
      context.setStaticallyKnownDocuments(new XmldbURI[] { XmldbURI.create(request.getPath()).append(AtomProtocol.FEED_DOCUMENT_NAME) });
      
      try {
    	  
    	 declareVariables(context, request.getRequest(), response.getResponse());
    	  
         Sequence resultSequence = xquery.execute(feedQuery, null);
         if (resultSequence.isEmpty()) {
            throw new BadRequestException("No topic was found.");
         }
         String charset = getContext().getDefaultCharset();
         response.setStatusCode(200);
         response.setContentType(config.contentType+"; charset="+charset);
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
            LOG.fatal("Cannot read resource "+request.getPath(),ex);
            throw new EXistException("I/O error on read of resource "+request.getPath(),ex);
         } catch (SAXException saxe) {
            LOG.warn(saxe);
            throw new BadRequestException("Error while serializing XML: " + saxe.getMessage());
         }
         resultSequence.itemAt(0);
      } catch (XPathException ex) {
         throw new EXistException("Cannot execute xquery "+config.querySource.getURL(),ex);
      } finally {
         xquery.getXQueryPool().returnCompiledXQuery(config.querySource, feedQuery);
      }
      
   }

   public boolean isQueryByPostAllowed() {
      return allowQueryPost;
   }

   public void setQueryByPost(boolean allowed) {
      this.allowQueryPost = allowed;
   }

   
}
