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
 *  $id$
 */
package org.exist.cocoon;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.parameters.Parameterizable;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.environment.Context;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.Response;
import org.apache.cocoon.environment.Session;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.environment.http.HttpEnvironment;
import org.apache.cocoon.generation.ServiceableGenerator;
import org.apache.cocoon.xml.IncludeXMLConsumer;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceValidity;
import org.apache.excalibur.source.impl.validity.AggregatedValidity;
import org.apache.excalibur.source.impl.validity.ExpiresValidity;
import org.exist.http.Descriptor;
import org.exist.source.CocoonSource;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.value.Sequence;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * A generator for Cocoon which reads an XQuery script, executes it and passes
 * the results into the Cocoon pipeline.
 * 
 * The following optional attributes are accepted on the component declaration as default eXist settings:
 * <li><tt>collection</tt>: identifies the XML:DB root collection used to process
 * the request</li>
 * <li><tt>user</tt></li>
 * <li><tt>password</tt></li>
  *<li><tt>authen</tt></li>: if set to session, then use the user and password from the session. Otherwise use the parameter values.
 * <li><tt>create-session</tt>: if set to "true", indicates that an
 * HTTP session should be created upon the first invocation.</li>
 * <li><tt>expand-xincludes</tt></li>
 * <li><tt>cache-validity</tt>: if specified, the XQuery content is
 * cached until the specified delay expressed in milliseconds is elapsed
 * or until the XQuery file is modified.  The identity of the cached content is
 * computed using the XQuery file URI and the list of all parameters passed to
 * the XQuery.</li>
 * 
 * The component also accept default parameters that will be declared as implicit variables in the XQuery.
 * See below an example declaration of the XQueryGenerator component with default eXist settings, and an extra user-defined parameter:
 * 
 * <map:generator logger="xmldb" name="xquery"
 * 		collection="xmldb:exist:///db/"
 * 		user="guest"
 * 		password="guest"
 *		create-session="false"
 * 		expand-xincludes="false"
 	        authen="session"
 *		cache-validity="-1"
 *		src="org.exist.cocoon.XQueryGenerator">
 *   <parameter name="myProjectURI" value="/db/myproject"/>
 * </map:generator>
 * 
 * These settings and parameters can be overriden on a per-pipeline basis with sitemap parameters, see below with default values and the extra user-defined parameter:
 * 
 * <pre>
 *  &lt;map:parameter name=&quot;collection&quot; value=&quot;xmldb:exist:///db&quot;/&gt;
 *  &lt;map:parameter name=&quot;user&quot; value=&quot;guest&quot;/&gt;
 *  &lt;map:parameter name=&quot;password&quot; value=&quot;guest&quot;/&gt;
 *  &lt;map:parameter name=&quot;create-session&quot; value=&quot;false&quot;/&gt;
 *  &lt;map:parameter name=&quot;expand-xincludes&quot; value=&quot;false&quot;/&gt;
 *  &lt;map:parameter name=&quot;cache-validity&quot; value=&quot;-1quot;/&gt;
 *  &lt;map:parameter name=&quot;myProjectURI&quot; value=&quot;/db/myproject&quot;/&gt;
 * </pre>
 * 
 * The last sitemap parameter overrides the value of the XQuery variable defined in the component parameters,
 * whereas others override the default eXist settings defined on the component attributes.
 *
 * @author wolf
 */
public class XQueryGenerator extends ServiceableGenerator implements Configurable, Parameterizable, CacheableProcessingComponent {
	public final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

	private Source inputSource = null;
	private Map objectModel = null;

	private boolean createSession;
	private boolean defaultCreateSession = false;
	private final static String CREATE_SESSION = "create-session";

	private boolean expandXIncludes;
	private boolean defaultExpandXIncludes = false;
	private final static String EXPAND_XINCLUDES = "expand-xincludes";

	private XmldbURI collectionURI;
	private XmldbURI defaultCollectionURI = XmldbURI.EMBEDDED_SERVER_URI.append(XmldbURI.ROOT_COLLECTION_URI);
	private final static String COLLECTION_URI = "collection";
	
	private long cacheValidity;
	private long defaultCacheValidity = SourceValidity.INVALID;
	private final static String CACHE_VALIDITY = "cache-validity";

	private String user;
	private String defaultUser = "guest";
	private final static String USER = "user";

	private String password;
	private String defaultPassword = "guest";
	private final static String PASSWORD = "password";
        
        private String authen;
        private String defaultAuthen = "session";
        private final static String AUTHEN = "authen";

	private Map optionalParameters;
	private Parameters componentParams;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.cocoon.generation.AbstractGenerator#setup(org.apache.cocoon.environment.SourceResolver,
	 *         java.util.Map, java.lang.String,
	 *         org.apache.avalon.framework.parameters.Parameters)
	 */
	public void setup(SourceResolver resolver, Map objectModel, String source,
						Parameters parameters) throws ProcessingException,
			SAXException, IOException {
		super.setup(resolver, objectModel, source, parameters);

		/*
		 * We don't do this directly in parameterize() because setup() can be
		 * called multiple times and optionalParameters needs resetting to forget
		 * sitemap parameters that may have been removed inbetween
		 * 
		 * The map must be sorted so that getKey() always returns the same
		 * object for any given oder of parameters.
		 */
		this.optionalParameters = new TreeMap();
		String paramNames[] = componentParams.getNames();
		for (int i = 0; i < paramNames.length; i++) {
			String param = paramNames[i];
			try {
				optionalParameters.put(param, componentParams.getParameter(param));
			} catch (ParameterException e1) {
				// Cannot happen as we iterate through existing parameters
			}
		}

		this.objectModel = objectModel;
		this.inputSource = resolver.resolveURI(source);
		String paramCollectionURI = parameters.getParameter(COLLECTION_URI,null);
		if(paramCollectionURI != null) {
			try {
				this.collectionURI = XmldbURI.xmldbUriFor(paramCollectionURI);
			} catch(URISyntaxException e) {
				throw new ProcessingException("Invalid XmldbURI for parameter '"+COLLECTION_URI+"': "+e.getMessage(),e);
			}
		} else {
			this.collectionURI = this.defaultCollectionURI;
		}
                
              this.authen = parameters.getParameter(AUTHEN,
				this.defaultAuthen);
		this.user = parameters.getParameter(USER, this.defaultUser);
		this.password = parameters.getParameter(PASSWORD, this.defaultPassword);
		this.createSession = parameters.getParameterAsBoolean(CREATE_SESSION,
				this.defaultCreateSession);
		this.expandXIncludes = parameters.getParameterAsBoolean(
				EXPAND_XINCLUDES, this.defaultExpandXIncludes);
		this.cacheValidity = parameters.getParameterAsLong(CACHE_VALIDITY, defaultCacheValidity);
		paramNames = parameters.getNames();
		for (int i = 0; i < paramNames.length; i++) {
			String param = paramNames[i];
			if (!(param.equals(COLLECTION_URI) || param.equals(USER)
					|| param.equals(PASSWORD) || param.equals(AUTHEN)
					|| param.equals(CREATE_SESSION) || param
					.equals(EXPAND_XINCLUDES) || param.equals(CACHE_VALIDITY))) {
				this.optionalParameters.put(param, parameters
						.getParameter(param, ""));
			}
		}
        Context context = ObjectModelHelper.getContext(objectModel);
        String dbHome = context.getRealPath("WEB-INF");
		try {
			Class driver = Class.forName(getDriverName());
			Database database = (Database)driver.newInstance();
            database.setProperty("create-database", "true");
            database.setProperty("configuration", dbHome + File.separatorChar + "conf.xml");
			DatabaseManager.registerDatabase(database);
		} catch(Exception e) {
			throw new ProcessingException("Failed to initialize database driver: " + e.getMessage(), e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.apache.cocoon.generation.AbstractGenerator#recycle()
	 */
	public void recycle() {
		if (resolver != null)
			resolver.release(inputSource);
		inputSource = null;
		super.recycle();
	}

	/** @see org.apache.cocoon.generation.Generator#generate() */
	public void generate() throws IOException, SAXException,
			ProcessingException {
		ContentHandler includeContentHandler;
		if (inputSource == null)
			throw new ProcessingException("No input source");
		Request request = ObjectModelHelper.getRequest(objectModel);
		Response response = ObjectModelHelper.getResponse(objectModel);
		Context context = ObjectModelHelper.getContext(objectModel);
		Session session = request.getSession(createSession);
		
		final String servletPath = request.getServletPath();
		final String pathInfo = request.getPathInfo();
		StringBuffer moduleLoadPathBuffer = new StringBuffer(servletPath);
		if (pathInfo != null) {
			moduleLoadPathBuffer.append(pathInfo);
		}
		int p = moduleLoadPathBuffer.lastIndexOf("/");
		if (p != Constants.STRING_NOT_FOUND) {
			moduleLoadPathBuffer.delete(p, moduleLoadPathBuffer.length());  
		}
		final String moduleLoadPath = context.getRealPath(moduleLoadPathBuffer.toString());
		XmldbURI baseUri = collectionURI;
		if(pathInfo!=null) {
			baseUri = baseUri.append(request.getPathInfo());
		}
		
		// check if user and password can be read from the session
		if (session != null  && authen.equals("session") && request.isRequestedSessionIdValid()) {
            String actualUser = getSessionAttribute(session, "user");
            String actualPass = getSessionAttribute(session, "password");
			user = actualUser == null ? null : String.valueOf(actualUser);
			password = actualPass == null ? null : String.valueOf(actualPass);
		}
		if (user == null)
			user = defaultUser;
		if (password == null)
			password = defaultPassword;
		try {
			Collection collection = DatabaseManager.getCollection(
					collectionURI.toString(), user, password);
			if (collection == null) {
				if (getLogger().isErrorEnabled())
					getLogger().error(
							"Collection " + collectionURI + " not found");
				throw new ProcessingException("Collection " + collectionURI
						+ " not found");
			}
			XQueryService service = (XQueryService) collection.getService(
					"XQueryService", "1.0");
			service.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
			service.setProperty(EXistOutputKeys.EXPAND_XINCLUDES,
					expandXIncludes ? "yes" : "no");
			service.setProperty("base-uri", baseUri.toString());
			//service.setNamespace(RequestModule.PREFIX, RequestModule.NAMESPACE_URI);
			service.setModuleLoadPath(moduleLoadPath);
			if(!((CollectionImpl)collection).isRemoteCollection()) {
				HttpServletRequest httpRequest = (HttpServletRequest) objectModel
						.get(HttpEnvironment.HTTP_REQUEST_OBJECT);
				service.declareVariable(RequestModule.PREFIX + ":request", new CocoonRequestWrapper(request, httpRequest));
				service.declareVariable(RequestModule.PREFIX + ":request", new CocoonRequestWrapper(request, httpRequest));
				service.declareVariable(ResponseModule.PREFIX + ":response", new CocoonResponseWrapper(response));
                
				if(session != null)
					service.declareVariable(SessionModule.PREFIX + ":session",new CocoonSessionWrapper(session));
				includeContentHandler = this.contentHandler;
			} else {
				includeContentHandler = new IncludeXMLConsumer(this.contentHandler);
			}

			declareParameters(service);
			
			// String uri = inputSource.getURI();
			log(request, service, this);
			
			ResourceSet result = service.execute(new CocoonSource(inputSource, true));
			XMLResource resource;
			this.contentHandler.startDocument();
			for (long i = 0; i < result.getSize(); i++) {
				resource = (XMLResource) result.getResource(i);
				resource.getContentAsSAX(includeContentHandler);
			}
			this.contentHandler.endDocument();
		} catch (XMLDBException e) {
			throw new ProcessingException("XMLDBException occurred: "
					+ e.getMessage(), e);
		}
	}

	private void declareParameters(XQueryService service) throws XMLDBException {
		for(Iterator i = optionalParameters.entrySet().iterator(); i.hasNext(); ) {
			Map.Entry entry = (Map.Entry)i.next();
			service.declareVariable((String)entry.getKey(), entry.getValue());
		}
	}

    private String getSessionAttribute(Session session, String attribute) {
        Object obj = session.getAttribute(attribute);
        if(obj == null)
            return null;
        if(obj instanceof Sequence)
            try {
                return ((Sequence)obj).getStringValue();
            } catch (XPathException e) {
                return null;
            }
        return obj.toString();
    }
    
	/**
	 * @see org.apache.avalon.framework.configuration.Configurable#configure(org.apache.avalon.framework.configuration.Configuration)
	 */
	public void configure(Configuration config) throws ConfigurationException {
		String paramCollectionURI = config.getAttribute(COLLECTION_URI,null);
		if(paramCollectionURI != null) {
			try {
				this.defaultCollectionURI = XmldbURI.xmldbUriFor(paramCollectionURI);
			} catch(URISyntaxException e) {
				throw new ConfigurationException("Invalid XmldbURI for config attribute '"+COLLECTION_URI+"': "+e.getMessage(),e);
			}
		}
		this.defaultCreateSession = config.getAttributeAsBoolean(CREATE_SESSION, this.defaultCreateSession);
		this.defaultExpandXIncludes = config.getAttributeAsBoolean(EXPAND_XINCLUDES, this.defaultExpandXIncludes);
		this.defaultPassword = config.getAttribute(PASSWORD, this.defaultPassword);
		this.defaultUser = config.getAttribute(USER, this.defaultUser);
		this.defaultCacheValidity = config.getAttributeAsLong(CACHE_VALIDITY, this.defaultCacheValidity);
	}

	/**
	 * @see org.apache.avalon.framework.parameters.Parameterizable#parameterize(org.apache.avalon.framework.parameters.Parameters)
	 */
	public void parameterize(Parameters params) throws ParameterException {
		this.componentParams = params;
	}

	public Serializable getKey() {
		StringBuffer key = new StringBuffer();
		key.append(optionalParameters.toString());
		key.append(inputSource.getURI());
		return key.toString();
	}

	public SourceValidity getValidity() {
		if (cacheValidity != SourceValidity.INVALID) {
			AggregatedValidity v = new AggregatedValidity();
			if (inputSource.getValidity() != null)
				v.add(inputSource.getValidity());
			v.add(new ExpiresValidity(cacheValidity));
			return v;
		}
		return null;
	}
	
	HttpServletRequest getRequest() {
		return (HttpServletRequest) objectModel.get( "httprequest" );
	}
	
	/** Static method to log the HTTP requests received by the {@link XQueryGenerator} */
	private static void log(Request request, XQueryService service,
			XQueryGenerator generator) {
		Descriptor descriptor = Descriptor.getDescriptorSingleton();
    	if( descriptor.allowRequestLogging() ) {
    		HttpServletRequest servletRequest = generator.getRequest();
    		descriptor.doLogRequestInReplayLog( servletRequest );
    	}
	}
	
	public String getDriverName() {
		return DRIVER;
	}
}
