package org.exist.http.webdav.methods;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.http.webdav.WebDAV;
import org.exist.http.webdav.WebDAVUtil;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class Lock extends AbstractWebDAVMethod {

    private final static int SCOPE_EXCLUSIVE = 0;
    private final static int SCOPE_SHARED = 1;
    
    private DocumentBuilderFactory docFactory;
	private BrokerPool pool;
	
	public Lock(BrokerPool pool) {
	    super(pool);
	    docFactory = DocumentBuilderFactory.newInstance();
		docFactory.setNamespaceAware(true);
	}
	
    /* (non-Javadoc)
     * @see org.exist.http.webdav.WebDAVMethod#process(org.exist.security.User, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
     */
    public void process(User user, HttpServletRequest request,
            HttpServletResponse response, String path) throws ServletException, IOException {
    	DBBroker broker = null;
		Collection collection = null;
		DocumentImpl resource = null;
		try {
			broker = pool.get(user);
			collection = broker.openCollection(path, org.exist.storage.lock.Lock.WRITE_LOCK);
			if(collection == null) {
                ///TODO : use dedicated function in XmldbURI
				int pos = path.lastIndexOf("/");
				String collName = path.substring(0, pos);
				String docName = path.substring(pos + 1);
				LOG.debug("collection = " + collName + "; doc = " + docName);
				collection = broker.openCollection(collName, org.exist.storage.lock.Lock.WRITE_LOCK);
				if(collection == null) {
					LOG.debug("No resource or collection found for path: " + path);
					response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_ERR);
					return;
				}
				resource = collection.getDocument(broker, docName);
				if(resource == null) {
					LOG.debug("No resource found for path: " + path);
					response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_ERR);
					return;
				}
			}
			if(resource == null) {
			    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Lock on collections is not supported yet");
			    return;
			}
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			docFactory.setNamespaceAware(true);
			DocumentBuilder docBuilder;
	        try {
	            docBuilder = docFactory.newDocumentBuilder();
	        } catch (ParserConfigurationException e1) {
	            throw new ServletException(WebDAVUtil.XML_CONFIGURATION_ERR, e1);
	        }
			Document doc = WebDAVUtil.parseRequestContent(request, response, docBuilder);
			Element lockinfo = doc.getDocumentElement();
			if(!(lockinfo.getLocalName().equals("lockinfo") && 
					lockinfo.getNamespaceURI().equals(WebDAV.DAV_NS))) {
				LOG.debug(WebDAVUtil.UNEXPECTED_ELEMENT_ERR + lockinfo.getNodeName());
				response.sendError(HttpServletResponse.SC_BAD_REQUEST,
						WebDAVUtil.UNEXPECTED_ELEMENT_ERR + lockinfo.getNodeName());
				return;
			}
			int scope = SCOPE_EXCLUSIVE;
			String owner = null;
			
			Node node = lockinfo.getFirstChild();
			while(node != null) {
			    if(node.getNodeType() == Node.ELEMENT_NODE) {
			        if(node.getNamespaceURI().equals(WebDAV.DAV_NS)) {
			            if("lockscope".equals(node.getLocalName())) {
			                Node scopeNode = WebDAVUtil.firstElementNode(node);
			                if("exclusive".equals(scopeNode.getLocalName()))
			                    scope = SCOPE_EXCLUSIVE;
			                else if("shared".equals(scopeNode.getLocalName()))
			                    scope = SCOPE_SHARED;
			            }
			            if("locktype".equals(node.getLocalName())) {
			                Node typeNode = WebDAVUtil.firstElementNode(node);
			                if(!"write".equals(typeNode.getLocalName())) {
			                     response.sendError(HttpServletResponse.SC_BAD_REQUEST,
			                             WebDAVUtil.UNEXPECTED_ELEMENT_ERR + typeNode.getNodeName());
			                     return;
			                }
			            }
			            if("owner".equals(node.getLocalName())) {
			                Node href = WebDAVUtil.firstElementNode(node);
			                owner = WebDAVUtil.getElementContent(href);
			            }
			        }
			    }
			    node = node.getNextSibling();
			}
			LOG.debug("Received lock request [" + scope + "] for owner " + owner);
			if(resource != null)
			    lockResource(request, response, resource, scope);
		} catch (EXistException e) {
			throw new ServletException(e);
		} finally {
			if(collection != null)
				collection.release();
		}
    }

    private void lockResource(HttpServletRequest request, HttpServletResponse response,
            DocumentImpl resource, int scope) throws ServletException, IOException {
        if(scope == SCOPE_SHARED) {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Shared locks are not implemented");
            return;
        }
    }
}
