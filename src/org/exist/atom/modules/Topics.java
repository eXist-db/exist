/*
 * AtomProtocol.java
 *
 * Created on June 16, 2006, 11:39 AM
 *
 * (C) R. Alexander Milowski alex@milowski.com
 */

package org.exist.atom.modules;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.atom.Atom;
import org.exist.atom.IncomingMessage;
import org.exist.atom.OutgoingMessage;
import org.exist.http.BadRequestException;
import org.exist.http.NotFoundException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.URLSource;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.xml.sax.SAXException;

/**
 *
 * @author R. Alexander Milowski
 */
public class Topics extends AtomModuleBase implements Atom {
   
   protected final static Logger LOG = Logger.getLogger(Topics.class);
   
   URL getTopicQueryURL;
   URLSource getTopicQuerySource;
   
   /** Creates a new instance of AtomProtocol */
   public Topics() {
      getTopicQueryURL = getClass().getResource("topic.xq");   
      getTopicQuerySource = new URLSource(getTopicQueryURL);
   }
   
   public void doGet(DBBroker broker,IncomingMessage request,OutgoingMessage response)
      throws BadRequestException,PermissionDeniedException,NotFoundException,EXistException
   {
      
      XQuery xquery = broker.getXQueryService();
      CompiledXQuery getTopicQuery = xquery.getXQueryPool().borrowCompiledXQuery(broker,getTopicQuerySource);

      XQueryContext context;
      if (getTopicQuery==null) {
         context = xquery.newContext(AccessContext.REST);
         try {
            getTopicQuery = xquery.compile(context, getTopicQuerySource);
         } catch (XPathException ex) {
            throw new EXistException("Cannot compile xquery "+getTopicQueryURL,ex);
         } catch (IOException ex) {
            throw new EXistException("I/O exception while compiling xquery "+getTopicQueryURL,ex);
         }
      } else {
         context = getTopicQuery.getContext();
      }
      try {
         context.setStaticallyKnownDocuments(new XmldbURI[] { XmldbURI.create(request.getPath()).append(AtomProtocol.FEED_DOCUMENT_NAME) });
         context.declareVariable("path",request.getPath());
      } catch (XPathException ex) {
         throw new EXistException("Error in setting up the context for "+getTopicQueryURL,ex);
      }

      try {
         Sequence resultSequence = xquery.execute(getTopicQuery, null);
         if (resultSequence.isEmpty()) {
            throw new BadRequestException("No topic was found.");
         }
         String charset = getContext().getDefaultCharset();
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
         throw new EXistException("Cannot execute xquery "+getTopicQueryURL,ex);
      } finally {
         xquery.getXQueryPool().returnCompiledXQuery(getTopicQuerySource, getTopicQuery);
      }
      
   }
   
}
