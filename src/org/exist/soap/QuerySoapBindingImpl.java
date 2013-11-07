package org.exist.soap;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.xacml.AccessContext;
import org.exist.security.xacml.XACMLSource;
import org.exist.soap.Session.QueryResult;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

import antlr.collections.AST;

/**
 *  Provides the actual implementations for the methods defined in
 * {@link org.exist.soap.Query}.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public class QuerySoapBindingImpl implements org.exist.soap.Query {
    
    private static Logger LOG = Logger.getLogger("QueryService");
    
    private BrokerPool pool;
    
    public QuerySoapBindingImpl() {
        try {
            if (!BrokerPool.isConfigured(BrokerPool.DEFAULT_INSTANCE_NAME))
                {configure();}
            pool = BrokerPool.getInstance();
        } catch (final Exception e) {
            throw new RuntimeException("failed to initialize broker pool");
        }
    }
    
    private QueryResponseCollection[] collectQueryInfo(TreeMap collections) {
        final QueryResponseCollection c[] = new QueryResponseCollection[collections.size()];
        QueryResponseDocument doc;
        QueryResponseDocument docs[];
        XmldbURI docId;
        int k = 0;
        int l;
        TreeMap documents;
        for (final Iterator i = collections.entrySet().iterator(); i.hasNext(); k++) {
            final Map.Entry entry = (Map.Entry) i.next();
            c[k] = new QueryResponseCollection();
            c[k].setCollectionName(((XmldbURI) entry.getKey()).toString());
            documents = (TreeMap) entry.getValue();
            docs = new QueryResponseDocument[documents.size()];
            c[k].setDocuments(new QueryResponseDocuments(docs));
            l = 0;
            for (final Iterator j = documents.entrySet().iterator(); j.hasNext(); l++) {
                final Map.Entry docEntry = (Map.Entry) j.next();
                doc = new QueryResponseDocument();
                docId = (XmldbURI) docEntry.getKey();
                //TODO: Unnecessary?
                docId = docId.lastSegment();
                doc.setDocumentName(docId.toString());
                doc.setHitCount(((Integer) docEntry.getValue()).intValue());
                docs[l] = doc;
            }
        }
        return c;
    }
    
    private void configure() throws Exception {
        final Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
    }
    
    private Session getSession(String id) throws java.rmi.RemoteException {
        final Session session = SessionManager.getInstance().getSession(id);
        if (session == null)
            {throw new java.rmi.RemoteException("Session is invalid or timed out");}
        return session;
    }
    
    public java.lang.String connect(java.lang.String userId, java.lang.String password) throws java.rmi.RemoteException {
    	try {
    		final Subject u = pool.getSecurityManager().authenticate(userId, password);
			
            LOG.debug("user " + userId + " connected");
            
            return SessionManager.getInstance().createSession(u);
		} catch (final Exception e) {
            throw new RemoteException(e.getMessage());
		}
    }
    
    public void disconnect(java.lang.String sessionId) throws java.rmi.RemoteException {
        final SessionManager manager = SessionManager.getInstance();
        final Session session = manager.getSession(sessionId);
        if (session != null) {
            LOG.debug("disconnecting session " + sessionId);
            manager.disconnect(sessionId);
        }
    }
    
        /* (non-Javadoc)
         * @see org.exist.soap.Query#getResourceData(java.lang.String, byte[], boolean, boolean)
         */
    public byte[] getResourceData(java.lang.String sessionId, java.lang.String path, boolean indent, boolean xinclude, boolean processXSLPI) throws java.rmi.RemoteException {
        final Properties outputProperties = new Properties();
        outputProperties.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
        outputProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, xinclude ? "yes" : "no");
        outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI,
                processXSLPI ? "yes" : "no");
        final String xml = getResource(sessionId, path, outputProperties);
        try {
            return xml.getBytes("UTF-8");
        } catch (final UnsupportedEncodingException e) {
        	LOG.warn(e);
            return xml.getBytes();
        }
    }
    
    public java.lang.String getResource(java.lang.String sessionId, java.lang.String name, boolean indent, boolean xinclude) throws java.rmi.RemoteException {
        final Properties outputProperties = new Properties();
        outputProperties.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
        outputProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, xinclude ? "yes" : "no");
        return getResource(sessionId, name, outputProperties);
    }
    
    protected String getResource(String sessionId, String name,
            Properties outputProperties) throws java.rmi.RemoteException {
    	try {
    		return getResource(sessionId,XmldbURI.xmldbUriFor(name),outputProperties);
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    protected String getResource(String sessionId, XmldbURI name,
            Properties outputProperties) throws java.rmi.RemoteException {
        final Session session = getSession(sessionId);
        DBBroker broker = null;
        try {
            broker = pool.get(session.getUser());
// TODO check XML/Binary resource
// DocumentImpl document = (DocumentImpl) broker.getDocument(name);
            final DocumentImpl document = broker.getResource(name, Permission.READ);
            if (document == null)
                {throw new RemoteException("resource " + name + " not found");}

            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            serializer.setProperties(outputProperties);
            return serializer.serialize(document);
            
            //			if (xml != null)
            //				try {
            //					return xml.getBytes("UTF-8");
            //				} catch (java.io.UnsupportedEncodingException e) {
            //					LOG.warn(e);
            //					return xml.getBytes();
            //				}
            //
            //			return null;
        } catch (final SAXException saxe) {
            saxe.printStackTrace();
            throw new RemoteException(saxe.getMessage());
        } catch (final EXistException e) {
            throw new RemoteException(e.getMessage());
        } catch (final PermissionDeniedException e) {
            throw new RemoteException(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    public org.exist.soap.Collection listCollection(java.lang.String sessionId, java.lang.String path) throws java.rmi.RemoteException {
    	try {
    		return listCollection(sessionId,XmldbURI.xmldbUriFor(path));
    	} catch(final URISyntaxException e) {
    		throw new RemoteException("Invalid collection URI",e);
    	}
    }
    public org.exist.soap.Collection listCollection(java.lang.String sessionId, XmldbURI path) throws java.rmi.RemoteException {
        final Session session = getSession(sessionId);
        DBBroker broker = null;
        try {
            broker = pool.get(session.getUser());
            if (path == null)
                {path = XmldbURI.ROOT_COLLECTION_URI;}
            final org.exist.collections.Collection collection = broker.getCollection(path);
            if (collection == null)
                {throw new RemoteException("collection " + path + " not found");}
            if (!collection.getPermissionsNoLock().validate(session.getUser(), Permission.READ))
                {throw new RemoteException("permission denied");}
            final Collection c = new Collection();
            
            // Sub-collections
            final String childCollections[] = new String[collection.getChildCollectionCount(broker)];
            int j = 0;
            for (final Iterator i = collection.collectionIterator(broker); i.hasNext(); j++)
                childCollections[j] = ((XmldbURI) i.next()).toString();
            
            // Resources
            final String[] resources = new String[collection.getDocumentCount(broker)];
            j = 0;
            XmldbURI resource;
            for (final Iterator i = collection.iterator(broker); i.hasNext(); j++) {
                resource = ((DocumentImpl) i.next()).getFileURI();
                resources[j] = resource.lastSegment().toString();
            }
            c.setResources(new StringArray(resources));
            c.setCollections(new StringArray(childCollections));
            return c;
        } catch (final EXistException e) {
            throw new RemoteException(e.getMessage());
        } catch (final PermissionDeniedException pde) {
            throw new RemoteException(pde.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
        /* (non-Javadoc)
         * @see org.exist.soap.Query#xquery(java.lang.String, byte[])
         */
    @Override
    public org.exist.soap.QueryResponse xquery(java.lang.String sessionId, byte[] xquery) throws java.rmi.RemoteException {
        String query;
        try {
            query = new String(xquery, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
        	LOG.warn(e);
            query = new String(xquery);
        }
        return query(sessionId, query);
    }

    /**  @deprecated use {@link #xquery(String, byte[])} instead. */
    @Override
    public org.exist.soap.QueryResponse query(java.lang.String sessionId, java.lang.String xpath) throws java.rmi.RemoteException {
        final Session session = getSession(sessionId);
        
        final QueryResponse resp = new QueryResponse();
        resp.setHits(0);
        DBBroker broker = null;
        try {
//            xpath = StringValue.expand(xpath);
            LOG.debug("query: " + xpath);
            broker = pool.get(session.getUser());
            final XQueryContext context = new XQueryContext(pool, AccessContext.SOAP);
            context.setXacmlSource(XACMLSource.getInstance(xpath));
            
            session.registerContext(context);
            
            // TODO(pkaminsk2): why replicate XQuery.compile here?
            
            final XQueryLexer lexer = new XQueryLexer(context, new StringReader(xpath));
            final XQueryParser parser = new XQueryParser(lexer);
            final XQueryTreeParser treeParser = new XQueryTreeParser(context);
            parser.xpath();
            if (parser.foundErrors()) {
                LOG.debug(parser.getErrorMessage());
                throw new RemoteException(parser.getErrorMessage());
            }
            
            final AST ast = parser.getAST();
            
            final PathExpr expr = new PathExpr(context);
            treeParser.xpath(ast, expr);
            if (treeParser.foundErrors()) {
                LOG.debug(treeParser.getErrorMessage());
                throw new EXistException(treeParser.getErrorMessage());
            }
            LOG.info("query: " + ExpressionDumper.dump(expr));
            final long start = System.currentTimeMillis();
            expr.analyze(new AnalyzeContextInfo());
            final Sequence seq= expr.eval(null, null);
            
            QueryResponseCollection[] collections = null;
            if (!seq.isEmpty() && Type.subTypeOf(seq.getItemType(), Type.NODE))
                {collections = collectQueryInfo(scanResults(seq));}
            session.addQueryResult(seq);
            resp.setCollections(new QueryResponseCollections(collections));
            resp.setHits(seq.getItemCount());
            resp.setQueryTime(System.currentTimeMillis() - start);
            expr.reset();
            context.reset();
        } catch (final Exception e) {
            LOG.debug(e.getMessage(), e);
            throw new RemoteException("query execution failed: " + e.getMessage());
        } finally {
            pool.release(broker);
        }
        return resp;
    }
    
        /* (non-Javadoc)
         * @see org.exist.soap.Query#retrieveData(java.lang.String, int, int, boolean, boolean, java.lang.String)
         */
    public org.exist.soap.Base64BinaryArray retrieveData(java.lang.String sessionId, int start, int howmany, boolean indent, boolean xinclude, java.lang.String highlight) throws java.rmi.RemoteException {
        final String[] results = retrieve(sessionId, start, howmany, indent, xinclude, highlight);
        final byte[][] data = new byte[results.length][];
        for(int i = 0; i < results.length; i++) {
            try {
                data[i] = results[i].getBytes("UTF-8");
            } catch (final UnsupportedEncodingException e) {
            }
        }
        return new Base64BinaryArray(data);
    }
    
    public java.lang.String[] retrieve(java.lang.String sessionId, int start, int howmany, boolean indent, boolean xinclude, java.lang.String highlight) throws java.rmi.RemoteException {
        final Session session = getSession(sessionId);
        DBBroker broker = null;
        try {
            broker = pool.get(session.getUser());
            final QueryResult queryResult = session.getQueryResult();
            if (queryResult == null)
                {throw new RemoteException("result set unknown or timed out");}
            final Sequence seq = (Sequence) queryResult.result;
            if (start < 1 || start > seq.getItemCount())
                {throw new RuntimeException(
                        "index " + start + " out of bounds (" + seq.getItemCount() + ")");}
            if (start + howmany > seq.getItemCount() || howmany == 0)
                {howmany = seq.getItemCount() - start + 1;}
            
            final String xml[] = new String[howmany];
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            serializer.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
            serializer.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, xinclude ? "yes" : "no");
            serializer.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, highlight);
            
            Item item;
            int j = 0;
            for (int i = --start; i < start + howmany; i++, j++) {
                item = seq.itemAt(i);
                if (item == null)
                    {continue;}
                if (item.getType() == Type.ELEMENT) {
                    final NodeValue node = (NodeValue) item;
                    xml[j] = serializer.serialize(node);
                } else {
                    xml[j] = item.getStringValue();
                }
            }
            return xml;
        } catch (final Exception e) {
            LOG.warn(e);
            e.printStackTrace();
            throw new RemoteException(e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
    public java.lang.String[] retrieveByDocument(java.lang.String sessionId, int start, int howmany, java.lang.String path, boolean indent, boolean xinclude, java.lang.String highlight) throws java.rmi.RemoteException {
        final Session session = getSession(sessionId);
        DBBroker broker = null;
        try {
            broker = pool.get(session.getUser());
            final Sequence qr = (Sequence) session.getQueryResult().result;
            if (qr == null)
                {throw new RemoteException("result set unknown or timed out");}
            String xml[] = null;
            if (Type.subTypeOf(qr.getItemType(), Type.NODE)) {
                // Fix typecast exception RMT
//				NodeList resultSet = (NodeSet)qr;
                final ExtArrayNodeSet hitsByDoc = new ExtArrayNodeSet();
                NodeProxy p;
                String ppath;
                for (final SequenceIterator i = qr.iterate(); i.hasNext(); ) {
//				for (Iterator i = ((NodeSet) resultSet).iterator(); i.hasNext();) {
                    p = (NodeProxy) i.nextItem();
                    ///TODO : use dedicated function in XmldbURI
                    ppath = p.getDocument().getCollection().getURI().toString() + '/' + p.getDocument().getFileURI();
                    if (ppath.equals(path))
                        {hitsByDoc.add(p);}
                }
                --start;
                if (start < 0 || start > hitsByDoc.getLength())
                    {throw new RemoteException(
                            "index " + start + "out of bounds (" + hitsByDoc.getLength() + ")");}
                if (start + howmany >= hitsByDoc.getLength())
                    {howmany = hitsByDoc.getLength() - start;}
                final Serializer serializer = broker.getSerializer();
                serializer.reset();
                serializer.setProperty(OutputKeys.INDENT, indent ? "yes" : "no");
                serializer.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, xinclude ? "yes" : "no");
                serializer.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, highlight);
                
                xml = new String[howmany];
                int j=0;
                for (int i = start; i < start + howmany; i++) {
                    final NodeProxy proxy = ((NodeSet) hitsByDoc).get(i);
                    if (proxy == null)
                        {throw new RuntimeException("not found: " + start);}
                    xml[j] = serializer.serialize(proxy);
                    j++; // update count number of results
                }
            } else
                {throw new RemoteException("result set is not a node list");}
            return xml;
        } catch (final Exception e) {
            LOG.warn(e);
            e.printStackTrace();
            throw new RemoteException(e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }
    
    private TreeMap scanResults(Sequence results) throws RemoteException {
        final TreeMap collections = new TreeMap();
        TreeMap documents;
        Integer hits;
        try {
			for (final SequenceIterator i = results.iterate(); i.hasNext(); ) {
			    final Item item = i.nextItem();
			    if(Type.subTypeOf(item.getType(), Type.NODE)) {
			        final NodeValue node = (NodeValue)item;
			        if(node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
			            final NodeProxy p = (NodeProxy)node;
			            if ((documents = (TreeMap) collections.get(p.getDocument().getCollection().getURI())) == null) {
			                documents = new TreeMap();
			                collections.put(p.getDocument().getCollection().getURI(), documents);
			            }
			            if ((hits = (Integer) documents.get(p.getDocument().getFileURI())) == null)
			                documents.put(p.getDocument().getFileURI(), Integer.valueOf(1));
			            else
			                documents.put(p.getDocument().getFileURI(), Integer.valueOf(hits.intValue() + 1));
			        }
			    }
			}
		} catch (final XPathException e) {
			throw new RemoteException(e.getMessage());
		}
        return collections;
    }
}
