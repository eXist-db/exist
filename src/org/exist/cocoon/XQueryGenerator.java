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
import java.util.Enumeration;
import java.util.Map;

import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.Session;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.generation.ServiceableGenerator;
import org.apache.excalibur.source.Source;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author wolf
 */
public class XQueryGenerator extends ServiceableGenerator {

	private Source inputSource = null;
	private Map objectModel = null;
	private boolean mapRequestParams = false;
	private boolean createSession = false;
		
	private String collectionURI = null;
	private String user = null;
	private String password = null;
	
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
		this.user = parameters.getParameter("user", "guest");
		this.password = parameters.getParameter("password", "guest");
		this.mapRequestParams = parameters.getParameterAsBoolean("use-request-parameters", false);
		this.createSession = parameters.getParameterAsBoolean("create-session", true);
	}

	/* (non-Javadoc)
	 * @see org.apache.cocoon.generation.Generator#generate()
	 */
	public void generate()
		throws IOException, SAXException, ProcessingException {
		if (inputSource == null)
			throw new ProcessingException("No input source");
		Request request = ObjectModelHelper.getRequest(objectModel);
		Session session = request.getSession(createSession);
		try {
			Collection collection = DatabaseManager.getCollection(collectionURI, user, password);
			if(collection == null) {
				if(getLogger().isErrorEnabled())
					getLogger().error("Collection " + collectionURI + " not found");
				throw new ProcessingException("Collection " + collectionURI + " not found");
			}
			XPathQueryServiceImpl service = (XPathQueryServiceImpl)
				collection.getService("XPathQueryService", "1.0");
			service.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
			
			service.declareVariable("request", request);
			service.declareVariable("session", session);
			
			String xquery = readQuery();
			if(mapRequestParams)
				mapRequestParams(request, service);
			ResourceSet result = service.query(xquery);
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

	private void mapRequestParams(Request request, XPathQueryServiceImpl service) throws XMLDBException {
		String param;
		String values[];
		for(Enumeration enum = request.getParameterNames(); enum.hasMoreElements(); ) {
			param = (String) enum.nextElement();
			values = request.getParameterValues(param);
			if(values.length == 1)
				service.declareVariable(param, values[0]);
			else
				service.declareVariable(param, values);
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
