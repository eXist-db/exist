/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.cocoon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.Response;
import org.apache.cocoon.environment.Session;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.generation.ServiceableGenerator;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceValidity;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.CompiledExpression;
import org.exist.xmldb.XQueryService;
import org.exist.xpath.functions.request.RequestModule;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * A generator for Cocoon which reads an XQuery script, executes it and 
 * passes the results into the Cocoon pipeline.
 * 
 * The following sitemap parameters (with default values) are accepted:
 * 
 * <pre>
 * &lt;map:parameter name="collection" value="xmldb:exist:///db"/&gt;
 * &lt;map:parameter name="user" value="guest"/&gt;
 * &lt;map:parameter name="password" value="guest"/&gt;
 * &lt;map:parameter name="create-session" value="false"/&gt;
 * </pre>
 * 
 * Parameter collection identifies the XML:DB root collection used to
 * process the request. If set to "true", parameter create-session
 * indicates that an HTTP session should be created upon the first
 * invocation.
 *  
 * @author wolf
 */
public class XQueryGenerator extends ServiceableGenerator {

	private Source inputSource = null;
	private Map objectModel = null;
	private boolean createSession = false;
	
	private String collectionURI = null;
	private String defaultUser = null;
	private String defaultPassword = null;
	
	private Map cache = new HashMap();
	
	private class CachedExpression {
		SourceValidity validity;
		CompiledExpression expr;
		
		public CachedExpression(SourceValidity validity, CompiledExpression expr) {
			this.validity = validity;
			this.expr = expr;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.apache.cocoon.generation.AbstractGenerator#setup(org.apache.cocoon.environment.SourceResolver, java.util.Map, java.lang.String, org.apache.avalon.framework.parameters.Parameters)
	 */
	public void setup(
		SourceResolver resolver,
		Map objectModel,
		String source,
		Parameters parameters)
		throws ProcessingException, SAXException, IOException {
		super.setup(resolver, objectModel, source, parameters);
		this.objectModel = objectModel;
		this.inputSource = resolver.resolveURI(source);
		this.collectionURI = parameters.getParameter("collection", "xmldb:exist:///db");
		this.defaultUser = parameters.getParameter("user", "guest");
		this.defaultPassword = parameters.getParameter("password", "guest");
		this.createSession = parameters.getParameterAsBoolean("create-session", false);
	}

	/* (non-Javadoc)
	 * @see org.apache.cocoon.generation.AbstractGenerator#recycle()
	 */
	public void recycle() {
		if(resolver != null)
			resolver.release(inputSource);
		inputSource = null;
		super.recycle();
	}
	
	/* (non-Javadoc)
	 * @see org.apache.cocoon.generation.Generator#generate()
	 */
	public void generate()
		throws IOException, SAXException, ProcessingException {
		if (inputSource == null)
			throw new ProcessingException("No input source");
		Request request = ObjectModelHelper.getRequest(objectModel);
		Response response = ObjectModelHelper.getResponse(objectModel);
		
		Session session = request.getSession(createSession);
		String user = null;
        String password = null;
        // check if user and password can be read from the session
        if(session != null && request.isRequestedSessionIdValid()) {
            user = (String)session.getAttribute("user");
            password = (String)session.getAttribute("password");
        }
        if(user == null)
            user = defaultUser;
        if(password == null)
            password = defaultPassword;
        System.out.println("user = " + user + "; password = " + password);
		try {
			Collection collection = DatabaseManager.getCollection(collectionURI, user, password);
			if(collection == null) {
				if(getLogger().isErrorEnabled())
					getLogger().error("Collection " + collectionURI + " not found");
				throw new ProcessingException("Collection " + collectionURI + " not found");
			}
			XQueryService service = (XQueryService)
				collection.getService("XQueryService", "1.0");
			service.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
			String prefix = RequestModule.PREFIX;
			service.setNamespace(prefix, RequestModule.NAMESPACE_URI);
			service.declareVariable(prefix + ":request", new CocoonRequestWrapper(request));
			service.declareVariable(prefix + ":response", new CocoonResponseWrapper(response));
			service.declareVariable(prefix + ":session", new CocoonSessionWrapper(session));
			
			String uri = inputSource.getURI();
			CompiledExpression expr;
			CachedExpression cached;
			synchronized(cache) {
				cached = (CachedExpression)cache.get(uri);
				if(cached != null) {
					// check if source is valid or should be reloaded
					int valid = cached.validity.isValid();
					if(valid == SourceValidity.UNKNOWN)
						valid = cached.validity.isValid(inputSource.getValidity());
					if(valid != SourceValidity.VALID) {
						cache.remove(uri);
						cached = null;
					}
				}
				if(cached == null) {
					String xquery = readQuery();
					expr = service.compile(xquery);
					cached = new CachedExpression(inputSource.getValidity(), expr);
					cache.put(uri, cached);
				} else
					expr = cached.expr;
			}
			ResourceSet result = service.execute(expr);
			
            XMLResource resource;
			this.contentHandler.startDocument();
			for(long i = 0; i < result.getSize(); i++) {
				resource = (XMLResource) result.getResource(i);
				resource.getContentAsSAX(this.contentHandler);
			}
			this.contentHandler.endDocument();
		} catch (XMLDBException e) {
			throw new ProcessingException("XMLDBException occurred: " + e.getMessage(), e);
		}
	}
	
	private String readQuery() throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream((int)inputSource.getContentLength());
		byte[] t = new byte[512];
		InputStream is = inputSource.getInputStream();
		int count = 0;
		while((count = is.read(t)) != -1) {
			os.write(t, 0, count);
		}
		return new String(os.toString("UTF-8"));
	}
}
