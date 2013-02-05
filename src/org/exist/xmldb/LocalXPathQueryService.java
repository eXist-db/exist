/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xmldb;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.debuggee.Debuggee;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.xacml.AccessContext;
import org.exist.security.xacml.NullAccessContextException;
import org.exist.source.DBSource;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.util.LockException;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Node;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.XMLResource;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.security.Permission;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;

public class LocalXPathQueryService implements XPathQueryServiceImpl, XQueryService {

	private final static Logger LOG = Logger.getLogger(LocalXPathQueryService.class);

	protected BrokerPool brokerPool;
	protected LocalCollection collection;
	protected Subject user;
	protected TreeMap<String, String> namespaceDecls = new TreeMap<String, String>();
	protected TreeMap<String, Object> variableDecls = new TreeMap<String, Object>();
	protected boolean xpathCompatible = true;
	protected String moduleLoadPath = null;
	protected Properties properties = null;
	protected boolean lockDocuments = false;
	protected LockedDocumentMap lockedDocuments = null;
	protected DBBroker reservedBroker = null;

    protected AccessContext accessCtx;
	
	@SuppressWarnings("unused")
	private LocalXPathQueryService() {}
	
	public LocalXPathQueryService(
		Subject user,
		BrokerPool pool,
		LocalCollection collection,
		AccessContext accessCtx) {
		if(accessCtx == null)
			throw new NullAccessContextException();
		this.accessCtx = accessCtx;
		this.user = user;
		this.collection = collection;
		this.brokerPool = pool;
		this.properties = new Properties(collection.properties);
	}

	public void clearNamespaces() throws XMLDBException {
		namespaceDecls.clear();
	}

	public String getName() throws XMLDBException {
		return "XPathQueryService";
	}

	public String getNamespace(String prefix) throws XMLDBException {
		return namespaceDecls.get(prefix);
	}
	
	public String getProperty(String property) throws XMLDBException {
		return properties.getProperty(property);
	}

	public String getVersion() throws XMLDBException {
		return "1.0";
	}

	public ResourceSet query(String query) throws XMLDBException {
		return query(query, null);
	}

	public ResourceSet query(XMLResource res, String query) throws XMLDBException {
		return query(res, query, null);
	}

	public ResourceSet query(String query, String sortBy) throws XMLDBException {
		XmldbURI[] docs = new XmldbURI[] { XmldbURI.create(collection.getName()) };
		return doQuery(query, docs, null, sortBy);
	}

	public ResourceSet query(XMLResource res, String query, String sortBy)
		throws XMLDBException {
		final Node n = ((LocalXMLResource) res).root;
		if (n != null && n instanceof org.exist.memtree.NodeImpl) {
			
			XmldbURI[] docs = new XmldbURI[] { XmldbURI.create(res.getParentCollection().getName()) };
			return doQuery(query, docs, (org.exist.memtree.NodeImpl)n, sortBy);
		}
		NodeProxy node = ((LocalXMLResource) res).getNode();
		if (node == null) {
			// resource is a document
            //TODO : use dedicated function in XmldbURI
			XmldbURI[] docs = new XmldbURI[] { XmldbURI.create(res.getParentCollection().getName()).append(res.getDocumentId()) };
			return doQuery(query, docs, null, sortBy);
		} else {
			NodeSet set = new ExtArrayNodeSet(1);
			set.add(node);
			XmldbURI[] docs = new XmldbURI[] { node.getDocument().getURI() };
			return doQuery(query, docs, set, sortBy);
		}
	}
	
	public ResourceSet execute(CompiledExpression expression) throws XMLDBException {
		return execute(null, null, expression, null);
	}
	
	public ResourceSet execute(XMLResource res, CompiledExpression expression)
			throws XMLDBException {
		NodeProxy node = ((LocalXMLResource) res).getNode();
		if (node == null) {
			// resource is a document
			XmldbURI[] docs = new XmldbURI[] { XmldbURI.create(res.getParentCollection().getName()).append(res.getDocumentId()) };
			return execute(docs, null, expression, null);
		} else {
			NodeSet set = new ExtArrayNodeSet(1);
			set.add(node);
			XmldbURI[] docs = new XmldbURI[] { node.getDocument().getURI() };
			return execute(docs, set, expression, null);
		}
	}
	
	public ResourceSet execute(Source source) 
		throws XMLDBException {
			long start = System.currentTimeMillis();
	    	Subject preserveSubject = brokerPool.getSubject();
			DBBroker broker = null;
			Sequence result;
			try {
				broker = brokerPool.get(user);
//				DocumentSet docs = collection.getCollection().allDocs(broker, new DocumentSet(), true, true);
				XmldbURI[] docs = new XmldbURI[] { XmldbURI.create(collection.getName()) };

				XQuery xquery = broker.getXQueryService();
				XQueryPool pool = xquery.getXQueryPool();
				XQueryContext context;
				CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
				if(compiled == null)
				    context = xquery.newContext(accessCtx);
				else
				    context = compiled.getContext();
				//context.setBackwardsCompatibility(xpathCompatible);
				context.setStaticallyKnownDocuments(docs);

				if (variableDecls.containsKey(Debuggee.PREFIX+":session")) {
					context.declareVariable(Debuggee.SESSION, variableDecls.get(Debuggee.PREFIX+":session"));
					variableDecls.remove(Debuggee.PREFIX+":session");
				}

				setupContext(source, context);
				
				if(compiled == null)
				    compiled = xquery.compile(context, source);
				try {
				    result = xquery.execute(compiled, null, properties);
				} finally {
				    pool.returnCompiledXQuery(source, compiled);
				}
			} catch (EXistException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
			} catch (XPathException e) {
				throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
			} catch (IOException e) {
			    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            } catch (PermissionDeniedException e) {
			    throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
			} finally {
				brokerPool.release(broker);
	            brokerPool.setSubject(preserveSubject);
			}
			LOG.debug("query took " + (System.currentTimeMillis() - start) + " ms.");
			if(result != null)
				return new LocalResourceSet(user, brokerPool, collection, properties, result, null);
			else
				return null;
		}
	
    @Override
    public ResourceSet executeStoredQuery(final String uri) throws XMLDBException {
        final Subject preserveSubject = brokerPool.getSubject();
        DBBroker broker = null;
        Sequence result;
        try {
            broker = brokerPool.get(user);
            final DocumentImpl resource = broker.getResource(new XmldbURI(uri), Permission.READ | Permission.EXECUTE);
            if(resource == null) {
                throw new XMLDBException(ErrorCodes.INVALID_URI, "No stored XQuery exists at: " + uri);
            }
            final Source xquerySource = new DBSource(broker, (BinaryDocument)resource, false);
            return execute(xquerySource);
        } catch(final EXistException ee) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ee.getMessage(), ee);
        } catch(final PermissionDeniedException pde) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, pde.getMessage(), pde);
        } finally {
            if(broker != null) {
                brokerPool.release(broker);
            }
            brokerPool.setSubject(preserveSubject);
        }
    }
        
	public CompiledExpression compile(String query) throws XMLDBException {
		try {
			return compileAndCheck(query);
		} catch (XPathException e) {
			throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		}
	}

    public CompiledExpression compileAndCheck(String query) throws XMLDBException, XPathException {
    	Subject preserveSubject = brokerPool.getSubject();
        DBBroker broker = null;
        try {
            long start = System.currentTimeMillis();
            broker = brokerPool.get(user);
            XQuery xquery = broker.getXQueryService();
            XQueryContext context = xquery.newContext(accessCtx);
            setupContext(null, context);
            CompiledXQuery expr = xquery.compile(context, query);
//            checkPragmas(context);
            LOG.debug("compilation took "  +  (System.currentTimeMillis() - start));
            return expr;
        } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
		} finally {
            brokerPool.release(broker);
            brokerPool.setSubject(preserveSubject);
        }
    }
    
    public ResourceSet queryResource(String resource, String query)
    	throws XMLDBException {
    	LocalXMLResource res = (LocalXMLResource) collection.getResource(resource);
    	if (res == null)
    		throw new XMLDBException(
    			ErrorCodes.INVALID_RESOURCE,
    			"resource '" + resource + "' not found");
        XmldbURI[] docs = new XmldbURI[] { XmldbURI.create(res.getParentCollection().getName()).append(res.getDocumentId()) };
    	return doQuery(query, docs, null, null);
    }
	
	protected void setupContext(Source source, XQueryContext context) throws XMLDBException, XPathException {
	    try {
	    	context.setBaseURI(new AnyURIValue(properties.getProperty("base-uri", collection.getPath())));
	    } catch(XPathException e) {
	    	throw new XMLDBException(ErrorCodes.INVALID_URI,"Invalid base uri",e);
	    }
		if(moduleLoadPath != null)
			context.setModuleLoadPath(moduleLoadPath);
		else if (source != null) {
//	        context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI_PREFIX + collection.getPath());

	        String modulePath = null;
		    if (source instanceof DBSource) {
		        modulePath = ((DBSource) source).getDocumentPath().removeLastSegment().toString();
            
		    } else if (source instanceof FileSource) {
                modulePath = ((FileSource) source).getFile().getParent();
                
            }
		    
		    if (modulePath != null)
		        context.setModuleLoadPath(modulePath);
		}
		    

		// declare namespace/prefix mappings
		for (Map.Entry<String, String> entry : namespaceDecls.entrySet()) {
			context.declareNamespace(
				entry.getKey(),
				entry.getValue());
		}
		// declare static variables
		for (Map.Entry<String, Object> entry : variableDecls.entrySet()) {
			context.declareVariable(entry.getKey(), entry.getValue());
		}
		//context.setBackwardsCompatibility(xpathCompatible);
	}
	
	/**
	 * Check if the XQuery contains pragmas that define serialization settings.
	 * If yes, copy the corresponding settings to the current set of output properties.
	 *
	 * @param context
	 */
//	private void checkPragmas(XQueryContext context) throws XPathException {
//		Option pragma = context.getOption(Option.SERIALIZE_QNAME);
//		if(pragma == null)
//			return;
//		String[] contents = pragma.tokenizeContents();
//		for(int i = 0; i < contents.length; i++) {
//			String[] pair = Option.parseKeyValuePair(contents[i]);
//			if(pair == null)
//				throw new XPathException("Unknown parameter found in " + pragma.getQName().getStringValue() +
//						": '" + contents[i] + "'");
//			LOG.debug("Setting serialization property from pragma: " + pair[0] + " = " + pair[1]);
//			properties.setProperty(pair[0], pair[1]);
//		}
//	}

	private ResourceSet doQuery(
		String query,
		XmldbURI[] docs,
		Sequence contextSet,
		String sortExpr)
		throws XMLDBException {
		CompiledExpression expr = compile(query);
		return execute(docs, contextSet, expr, sortExpr);
	}

	/**
	 * Execute all following queries in a protected environment.
	 * Protected means: it is guaranteed that documents referenced by the
	 * query or the result set are not modified by other threads
	 * until {@link #endProtected} is called.
	 */
	public void beginProtected() throws XMLDBException {
//	    lockDocuments = true;
//		if (reservedBroker != null)
            // if a previous broker was not properly released, do it now (just to be sure)
//            brokerPool.release(reservedBroker);
		try {
	        boolean deadlockCaught;
            do {
                reservedBroker = brokerPool.get(user);
                deadlockCaught = false;
	        	MutableDocumentSet docs = null;
	            try {
	                org.exist.collections.Collection coll = collection.getCollection();
	                lockedDocuments = new LockedDocumentMap();
	                docs = new DefaultDocumentSet();
	                coll.allDocs(reservedBroker, docs, true, lockedDocuments, Lock.WRITE_LOCK);
	            } catch (LockException e) {
	                LOG.debug("Deadlock detected. Starting over again. Docs: " + docs.getDocumentCount() + "; locked: " +
                    lockedDocuments.size());
					lockedDocuments.unlock();
                    brokerPool.release(reservedBroker);
                    deadlockCaught = true;
                    } catch (PermissionDeniedException e) {
                        throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
                        "Permission denied on document");
	            }
            } while (deadlockCaught);
        } catch (EXistException e) {
            brokerPool.release(reservedBroker);
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage());
		}
    }
	
	/**
	 * Close the protected environment. All locks held
	 * by the current thread are released. The result set
	 * is no longer guaranteed to be stable.
	 */
	public void endProtected() {
	    lockDocuments = false;
	    if(lockedDocuments != null) {
	        lockedDocuments.unlock();
	    }
	    lockedDocuments = null;

        if (reservedBroker != null)
            brokerPool.release(reservedBroker);
        reservedBroker = null;
    }
	
	public void removeNamespace(String ns) throws XMLDBException {
		for (Iterator<String> i = namespaceDecls.values().iterator(); i.hasNext();) {
			if (i.next().equals(ns)) {
				i.remove();
			}
		}
	}

    private ResourceSet execute(XmldbURI[] docs, 
		Sequence contextSet, CompiledExpression expression, String sortExpr) 
    throws XMLDBException {
    	long start = System.currentTimeMillis();
        CompiledXQuery expr = (CompiledXQuery)expression;
    	Subject preserveSubject = brokerPool.getSubject();
    	DBBroker broker = null;
    	Sequence result;
    	XQueryContext context = expr.getContext();
        try {
    		broker = brokerPool.get(user);

    		//context.setBackwardsCompatibility(xpathCompatible);
    		context.setStaticallyKnownDocuments(docs);
            if (lockedDocuments != null)
                context.setProtectedDocs(lockedDocuments);
            setupContext(null, context);
//    		checkPragmas(context);
    		    
    		XQuery xquery = broker.getXQueryService();
    		result = xquery.execute(expr, contextSet, properties);
    	} catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
    	} catch (XPathException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
    	} catch (Exception e) {
    	    // need to catch all runtime exceptions here to be able to release locked documents
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
    	} finally {
//            if (keepLocks)
//                reservedBroker = broker;
//            else
                brokerPool.release(broker);
                brokerPool.setSubject(preserveSubject);
    	}
    	LOG.debug("query took " + (System.currentTimeMillis() - start) + " ms.");
    	if(result != null)
    		return new LocalResourceSet(user, brokerPool, collection, properties, result, sortExpr);
    	else
    		return null;
    }
    
	public void setCollection(Collection col) throws XMLDBException {
	}

	public void setNamespace(String prefix, String namespace) throws XMLDBException {
		namespaceDecls.put(prefix, namespace);
	}

	public void setProperty(String property, String value) throws XMLDBException {
		properties.setProperty(property, value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XPathQueryServiceImpl#declareVariable(java.lang.String, java.lang.Object)
	 */
	public void declareVariable(String qname, Object initialValue)
		throws XMLDBException {
		variableDecls.put(qname, initialValue);
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#setXPathCompatibility(boolean)
	 */
	public void setXPathCompatibility(boolean backwardsCompatible) {
		this.xpathCompatible = backwardsCompatible;
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#setModuleLoadPath(java.lang.String)
	 */
	public void setModuleLoadPath(String path) {
		moduleLoadPath = path;		
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#dump(org.exist.xmldb.CompiledExpression, java.io.Writer)
	 */
	public void dump(CompiledExpression expression, Writer writer) throws XMLDBException {
	    CompiledXQuery expr = (CompiledXQuery)expression;
	    expr.dump(writer);
	}
}
