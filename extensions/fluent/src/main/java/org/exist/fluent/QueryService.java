package org.exist.fluent;

import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.DefaultDocumentSet;
import java.io.IOException;
import java.text.*;
import java.util.*;
import java.util.regex.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.PermissionDeniedException;
import org.exist.source.*;
import org.exist.storage.*;
import org.exist.util.LockException;
import org.exist.xquery.*;
import org.exist.xquery.functions.fn.*;
import org.exist.xquery.value.*;

/**
 * Provides facilities for performing queries on a database.  It cannot
 * be instantiated directly; you must obtain an instance from a resource or the database.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class QueryService implements Cloneable {
	
	private static final Pattern PRE_SUB_PATTERN = Pattern.compile("\\$(\\d+)");
	private static final Logger LOG = LogManager.getLogger(QueryService.class);
	
	private static final Statistics STATS = new Statistics();
	
	/**
	 * Get the process-wide performance statistics gathering facet.
	 * 
	 * @return the performance statistics facet
	 */
	public static Statistics statistics() {return STATS;}
	
	private NamespaceMap namespaceBindings;
	private Map<String, Document> moduleMap = new TreeMap<String, Document>();
	private final Database db;
	protected DocumentSet docs, overrideDocs;
	protected Sequence base;
	protected AnyURIValue baseUri;
	private Map<QName, Object> bindings = new HashMap<QName, Object>();
	private boolean presub;

	/**
	 * Use this constructor when the docs and base are not constant for the query
	 * service and need to be set just before the query.  You must also override
	 * the prepareContext method.
	 *
	 * @param origin
	 */
	QueryService(Resource origin) {
		this.namespaceBindings = origin.namespaceBindings().extend();
		this.db = origin.database();
	}
	
	QueryService(Resource origin, DocumentSet docs, Sequence base) {
		this(origin);
		this.docs = docs;
		this.base = base;
	}
	
	private QueryService() {
		this.namespaceBindings = null;
		this.db = null;
	}
	
	boolean isFreshFrom(Resource origin) {
		return !presub && bindings.isEmpty() && moduleMap.isEmpty() && (namespaceBindings == null || namespaceBindings.isFreshFrom(origin.namespaceBindings()));
	}
	
	static final QueryService NULL = new QueryService() {
		@Override protected ItemList executeQuery(String query, WrapperFactory wrappeFactory, Object[] params) {
			return ItemList.NULL;
		}
		@Override public QueryAnalysis analyze(String query, Object... params) {
			throw new UnsupportedOperationException("NULL query service");
		}
		@Override public QueryService let(String var, Object value) {return this;}
		@Override public QueryService namespace(String key, String uri) {return this;}
		@Override public NamespaceMap namespaceBindings() {throw new UnsupportedOperationException("NULL query service");}
		@Override public QueryService importModule(Document module) {return this;}
		@Override public Item single(String query, Object... params) {throw new DatabaseException("expected 1 result item, got 0 (NULL query)");} 
	};
	
	void prepareContext(DBBroker broker) {
		// do nothing by default, override this if you need to set docs and base just before
		// a query is evaluated
	}
	
	/**
	 * Return the database to which the resource that provides the context for
	 * this query service belongs. The returned database will inherit its
	 * namespace bindings from this query service.
	 * 
	 * @return the database that contains this object
	 */
	public Database database() {
		return new Database(db, namespaceBindings);
	}
	
	/**
	 * Bind a variable to the given value within all query expression evaluated subsequently.
	 *
	 * @param variableName the qualified name of the variable to bind;
	 * 	prefixes are taken from the namespace mappings of the folder that provided this service;
	 * 	if the name starts with a <code>$</code>, it will be stripped automatically 
	 * @param value the value the variable should take
	 * @return this service, to chain calls
	 */
	public QueryService let(String variableName, Object value) {
		if (variableName == null) throw new NullPointerException("null variable name");
		if (variableName.startsWith("$")) variableName = variableName.substring(1);
		if (variableName.length() == 0) throw new IllegalArgumentException("empty variable name");
		return let(QName.parse(variableName, namespaceBindings, ""), value);
	}
	
	/**
	 * Bind a variable to the given value within all query expression evaluated subsequently.
	 *
	 * @param variableName the qualified name of the variable to bind
	 * @param value the value the variable should take
	 * @return this service, to chain calls
	 */
	public QueryService let(QName variableName, Object value) {
		bindings.put(variableName, value);
		return this;
	}
	
	/**
	 * Declare a namespace binding within the scope of this query.
	 *
	 * @param key the key to bind
	 * @param uri the namespace uri
	 * @return this service, to chain calls
	 */
	public QueryService namespace(String key, String uri) {
		namespaceBindings.put(key, uri);
		return this;
	}
	
	/**
	 * Return this query service's namespace bindings for inspection or modification.
	 *
	 * @return this query service's namespace bindings
	 */
	public NamespaceMap namespaceBindings() {
		return namespaceBindings;
	}

	final Pattern MODULE_DECLARATION_DQUOTE = Pattern.compile("\\A\\s*module\\s+namespace\\s+[\\p{Alpha}_][\\w.-]*\\s*=\\s*\"(([^\"]*(\"\")?)*)\"\\s*;");
	final Pattern MODULE_DECLARATION_SQUOTE = Pattern.compile("\\A\\s*module\\s+namespace\\s+[\\p{Alpha}_][\\w.-]*\\s*=\\s*'(([^']*('')?)*)'\\s*;");

	/**
	 * Import an XQuery library module from the given document.  The namespace and preferred
	 * prefix of the module are extracted from the module itself.  The MIME type of the document
	 * is set to "application/xquery" as a side-effect.
	 *
	 * @param module the non-XML document that holds the library module's source
	 * @return this service, to chain calls
	 * @throws DatabaseException if the module is an XML document, or the module declaration
	 * 		cannot be found at the top of the document
	 */
	public QueryService importModule(Document module) {
		if (module instanceof XMLDocument) throw new DatabaseException("module cannot be an XML document: " + module);
		Matcher matcher = MODULE_DECLARATION_DQUOTE.matcher(module.contentsAsString());
		if (!matcher.find()) {
			matcher = MODULE_DECLARATION_SQUOTE.matcher(module.contentsAsString());
			if (!matcher.find()) throw new DatabaseException("couldn't find a module declaration at the top of " + module);
		}
		module.metadata().setMimeType("application/xquery");
		String moduleNamespace = matcher.group(1);
		// TODO: should do URILiteral processing here to replace entity and character references and normalize
		// whitespace, but since it seems that eXist doesn't do it either (bug?) there's no reason to rush.
		Document prevModule = moduleMap.get(moduleNamespace);
		if (prevModule != null && !prevModule.equals(module)) throw new DatabaseException("module " + moduleNamespace + " already bound to " + prevModule + ", can't rebind to " + module);
		moduleMap.put(moduleNamespace, module);
		return this;
	}
	
	/**
	 * Import the same modules into this query service as imported by the given query service.
	 * This is a one-time copy; further imports into either query service won't affect the other one.
	 * @param that the query service to copy module imports from
	 * @return this service, to chain calls
	 */
	public QueryService importSameModulesAs(QueryService that) {
		moduleMap.putAll(that.moduleMap);
		return this;
	}
	
	/**
	 * Limit the root documents accessible to the query to the given list, overriding the any set
	 * derived from the query's context.  The query will still be able to access other documents
	 * through bound variables or by naming them directly, though.
	 * 
	 * @param rootDocs the list of root documents to limit the query to
	 * @return this service, to chain calls
	 */
	public QueryService limitRootDocuments(XMLDocument... rootDocs) {
		return limitRootDocuments(Arrays.asList(rootDocs));
	}
	
	/**
	 * Limit the root documents accessible to the query to the given list, overriding the any set
	 * derived from the query's context.  The query will still be able to access other documents
	 * through bound variables or by naming them directly, though.
	 * 
	 * @param rootDocs the list of root documents to limit the query to
	 * @return this service, to chain calls
	 */
	public QueryService limitRootDocuments(Collection<XMLDocument> rootDocs) {
		overrideDocs = new DefaultDocumentSet();
		for (XMLDocument doc : rootDocs) ((MutableDocumentSet) overrideDocs).add(doc.doc);
		return this;
	}
	
	/**
	 * Pre-substitute variables of the form '$n' where n is an integer in all query expressions
	 * evaluated subsequently.  The values are taken from the usual postional parameter list.
	 * Parameters that are presubbed are also bound to the usual $_n variables and can be
	 * used normally as such.  Pre-subbing is useful for element and attribute names, where
	 * XQuery doesn't allow variables.
	 *
	 * @return this service, to chain calls
	 */
	public QueryService presub() {
		presub = true;
		return this;
	}
	
	@Override public QueryService clone() {
		return clone(null, null);
	}
	
	/**
	 * Clone this query service, optionally overriding the clone's namespace and variable bindings.
	 * If the namespace bindings override or variable bindings override is specified, then that object
	 * is cloned and used for its respective purpose.  If an override is not specified, the bindings
	 * are cloned from the original query service.
	 *
	 * @param nsBindingsOverride the namespace bindings to clone, or <code>null</code> to clone from the original
	 * @param varBindingsOverride the variable bindings to clone, or <code>null</code> to clone from the original
	 * @return a clone of this query service with bindings optionally overridden
	 */
	public QueryService clone(NamespaceMap nsBindingsOverride, Map<QName, ?> varBindingsOverride) {
		try {
			QueryService that = (QueryService) super.clone();
			that.namespaceBindings = nsBindingsOverride != null
					? nsBindingsOverride.clone() : that.namespaceBindings.clone();
			if (varBindingsOverride == null) {
				that.bindings = new HashMap<QName, Object>(that.bindings);
			} else {
				that.bindings = new HashMap<QName, Object>();
				for (Map.Entry<QName, ?> entry : varBindingsOverride.entrySet()) {
					that.let(entry.getKey(), entry.getValue());
				}
			}
			that.moduleMap = new TreeMap<String, Document>(moduleMap);
			return that;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException("unexpected exception", e);
		}
	}

	/**
	 * Get all items that match the given query in the context of this object.
	 * @param query the query to match
	 * @param params parameters to the query, will be substituted for $_1, $_2, etc.
	 * @return a collection of all items that match the query
	 */
	public ItemList all(String query, Object... params) {
		return executeQuery(query, null, params);
	}
	
	/**
	 * Run the given query, ignoring the results.  Useful for running update "queries" --
	 * see eXist's <a href="http://exist-db.org/update_ext.html">XQuery Update Extensions</a>.
	 * @param query the query to run
	 * @param params parameters to the query, will be substituted for $_1, $_2, etc.
	 */
	public void run(String query, Object... params) {
		executeQuery(query, null, params);
	}
	
	private interface WrapperFactory {
		Function createWrapper(XQueryContext context);
	}
		
	ItemList executeQuery(String query, WrapperFactory wrapperFactory, Object[] params) {
		long t1 = System.currentTimeMillis(), t2 = 0, t3 = 0, t4 = 0;
		if (presub) query = presub(query, params);
		DBBroker broker = null;
		try {
			broker = db.acquireBroker();
			prepareContext(broker);
			if (overrideDocs != null) docs = overrideDocs;
			final org.exist.source.Source source = buildQuerySource(query, params, "execute");
			final XQuery xquery = broker.getBrokerPool().getXQueryService();
			final XQueryPool pool = broker.getBrokerPool().getXQueryPool();
			CompiledXQuery compiledQuery = pool.borrowCompiledXQuery(broker, source);
			MutableDocumentSet docsToLock = new DefaultDocumentSet();
			if (docs != null) docsToLock.addAll(docs);
			if (base != null) docsToLock.addAll(base.getDocumentSet());
			try {
				XQueryContext context;
				if (compiledQuery == null) {
					context = new XQueryContext(broker.getBrokerPool());
					buildXQueryStaticContext(context, true);
				} else {
					// static context already set
					context = compiledQuery.getContext();
					context.prepareForReuse();
				}
				buildXQueryDynamicContext(context, params, docsToLock, true);
				t2 = System.currentTimeMillis();
				if (compiledQuery == null) {
					compiledQuery = xquery.compile(broker, context, source);
					t3 = System.currentTimeMillis();
				}
				docsToLock.lock(broker, false);
				try {
					return new ItemList(xquery.execute(broker, wrap(compiledQuery, wrapperFactory, context), base), namespaceBindings.extend(), db);
				} finally {
					docsToLock.unlock();
					t4 = System.currentTimeMillis();
				}
			} finally {
				if (compiledQuery != null) {
					compiledQuery.getContext().runCleanupTasks();
					pool.returnCompiledXQuery(source, compiledQuery);
				}
			}
		} catch (XPathException e) {
			LOG.debug("query execution failed --  " + query + "  -- " + (params == null ? "" : " with params " + Arrays.asList(params)) + (bindings.isEmpty() ? "" : " and bindings " + bindings));
			throw new DatabaseException("failed to execute query", e);
		} catch (IOException e) {
			throw new DatabaseException("unexpected exception", e);
		} catch (LockException e) {
			throw new DatabaseException("deadlock", e);
		} catch (PermissionDeniedException e) {
			throw new DatabaseException("permission denied", e);
		} finally {
			db.releaseBroker(broker);
			STATS.update(query, t1, t2, t3, t4, System.currentTimeMillis());
		}
	}
	
	private CompiledXQuery wrap(CompiledXQuery expr, WrapperFactory wrapperFactory, XQueryContext context) throws XPathException {
		if (wrapperFactory == null) return expr;
		Function wrapper = wrapperFactory.createWrapper(context);
		wrapper.setArguments(Collections.singletonList((Expression)expr));
//		wrapper.setSource(expr.getSource());
		return wrapper;
	}
	
	private org.exist.source.Source buildQuerySource(String query, Object[] params, String cookie) {
		Map<String, String> combinedMap = namespaceBindings.getCombinedMap();
		for (Map.Entry<String, Document> entry : moduleMap.entrySet()) {
			combinedMap.put("<module> " + entry.getKey(), entry.getValue().path());
		}
		for (Map.Entry<QName, Object> entry : bindings.entrySet()) {
			combinedMap.put("<var> " + entry.getKey(), null);	// don't care about values, as long as the same vars are bound
		}
		combinedMap.put("<posvars> " + params.length, null);
		combinedMap.put("<cookie>", cookie);
		// TODO: should include statically known documents and baseURI too?
		return new StringSourceWithMapKey(query, combinedMap);
	}

	private void buildXQueryDynamicContext(XQueryContext context, Object[] params, MutableDocumentSet docsToLock, boolean bindVariables) throws XPathException {
		context.setBackwardsCompatibility(false);
		context.setStaticallyKnownDocuments(docs);
		context.setBaseURI(baseUri == null ? new AnyURIValue("/db") : baseUri);
		if (bindVariables) {
			for (Map.Entry<QName, Object> entry : bindings.entrySet()) {
				context.declareVariable(
						new org.exist.dom.QName(entry.getKey().getLocalPart(), entry.getKey().getNamespaceURI(), entry.getKey().getPrefix()),
						convertValue(entry.getValue()));
			}
			if (params != null) for (int i = 0; i < params.length; i++) {
				Object convertedValue = convertValue(params[i]);
				if (docsToLock != null && convertedValue instanceof Sequence) {
					docsToLock.addAll(((Sequence) convertedValue).getDocumentSet());
				}
				context.declareVariable("_"+(i+1), convertedValue);
			}
		}
	}
		
	private void buildXQueryStaticContext(XQueryContext context, boolean importModules) throws XPathException {
		context.declareNamespaces(namespaceBindings.getCombinedMap());
		for (Map.Entry<String, Document> entry : moduleMap.entrySet()) {
			context.importModule(entry.getKey(), null, "xmldb:exist:///db" + entry.getValue().path());
		}
	}
	
	/**
	 * Convert the given object into a value appropriate for being defined as
	 * the value of a variable in an XQuery.  This will extract a sequence out
	 * of all database objects, convert collections and arrays into sequences
	 * recursively, convert <code>null</code> into an empty sequence, and
	 * pass other objects through untouched.
	 * Convertible objects that are defined in the JDK will be automatically
	 * converted by eXist.
	 * @see org.exist.xquery.XPathUtil#javaObjectToXPath(Object, XQueryContext, boolean)
	 *
	 * @param o the object to convert to a database value
	 * @return the converted value, ready for assignment to an XQuery variable
	 */
	@SuppressWarnings("unchecked")
	private Object convertValue(Object o) {
		if (o == null) return Collections.emptyList();
		if (o instanceof Resource) {
			try {
				return ((Resource) o).convertToSequence();
			} catch (UnsupportedOperationException e) {
				return o;
			}
		}
		List<Object> list = null;
		if (o instanceof Collection) list = new ArrayList<Object>((Collection) o);
		else if (o instanceof Object[]) list = new ArrayList<Object>(Arrays.asList((Object[]) o));
		if (list != null) {
			for (ListIterator<Object> it = list.listIterator(); it.hasNext(); ) {
				it.set(convertValue(it.next()));
			}
			return list;
		}
		return DataUtils.toXMLObject(o);
	}
	
	private String presub(String query, Object[] params) {
		if (params == null) return query;
		StringBuffer buf = new StringBuffer();
		Matcher matcher = PRE_SUB_PATTERN.matcher(query);
		while(matcher.find()) {
			matcher.appendReplacement(buf, ((String) params[Integer.parseInt(matcher.group(1))-1]).replace("\\", "\\\\").replace("$", "\\$"));
		}
		matcher.appendTail(buf);
		return buf.toString();
	}
	
	/**
	 * Get all items that match the given query in the context of this object,
	 * without regard for the order of the results.  This can sometimes make a query
	 * run faster.
	 * @param query the query to match
	 * @param params
	 * @return a collection of all items that match the query
	 */
	public ItemList unordered(String query, Object... params) {
		// TODO: put expression in unordered context once eXist supports it
		// TODO: verify that callers to 'all' could not use 'unordered'
		// return all("declare ordering unordered; " + query, params);
		return all(query, params);
	}
	
	private static final WrapperFactory EXACTLY_ONE = new WrapperFactory() {
		public Function createWrapper(XQueryContext context) {return new FunExactlyOne(context);}
	};
	
	/**
	 * Get the one and only item that matches the given query in the context of
	 * this object.
	 * @param query the query to match
	 * @param params
	 * @return the unique item that matches the query
	 */
	public Item single(String query, Object... params) {
		ItemList result = executeQuery(query, EXACTLY_ONE, params);
		assert result.size() == 1 : "expected single result, got " + result.size();
		return result.get(0);
	}
	
	private static final WrapperFactory ZERO_OR_ONE = new WrapperFactory() {
		public Function createWrapper(XQueryContext context) {return new FunZeroOrOne(context);}
	};
	
	/**
	 * Get no more than one item that matches the given query in the context
	 * of this object.
	 * @param query the query to match
	 * @param params
	 * @return the item that matches this query, or <code>Item.NULL</code> if none
	 */
	public Item optional(String query, Object... params) {
		ItemList result = executeQuery(query, ZERO_OR_ONE, params);
		assert result.size() <= 1 : "expected zero or one results, got " + result.size();
		return result.size() == 0 ? Item.NULL : result.get(0);
	}
	
	public boolean flag(String query, boolean defaultValue) {
		Item item = optional(query);
		if (item != Item.NULL) {
			try {
				return item.booleanValue();
			} catch (Exception e) {
				LOG.error("illegal flag value '" + item +"' found for query " + query + "; using default '" + defaultValue + "'");
			}
		}
		return defaultValue;
	}
	
	private static final WrapperFactory EXISTS = new WrapperFactory() {
		public Function createWrapper(XQueryContext context) {return new FunExists(context);}
	};
	
	/**
	 * Return whether at least one item matches the given query in the context
	 * of this object.
	 * @param query the query to match
	 * @param params
	 * @return <code>true</code> if at least one item matches, <code>false</code> otherwise
	 */
	public boolean exists(String query, Object... params) {
		return executeQuery(query, EXISTS, params).get(0).booleanValue();
	}
	
	/**
	 * Statically analyze a query for various properties.
	 *
	 * @param query the query to analyze
	 * @param params parameters for the query; if necessary parameters are left out they will be listed as required variables in the analysis
	 * @return a query analysis facet
	 */
	public QueryAnalysis analyze(String query, Object... params) {
		if (presub) query = presub(query, params);
		
		long t1 = System.currentTimeMillis(), t2 = 0, t3 = 0;
		DBBroker broker = null;
		try {
			broker = db.acquireBroker();
			prepareContext(broker);
			final org.exist.source.Source source = buildQuerySource(query, params, "analyze");
			final XQuery xquery = broker.getBrokerPool().getXQueryService();
			final XQueryPool pool = broker.getBrokerPool().getXQueryPool();
			CompiledXQuery compiledQuery = pool.borrowCompiledXQuery(broker, source);
			try {
				AnalysisXQueryContext context;
				if (compiledQuery == null) {
					context = new AnalysisXQueryContext(broker);
					buildXQueryStaticContext(context, false);
					buildXQueryDynamicContext(context, params, null, false);
					t2 = System.currentTimeMillis();
					compiledQuery = xquery.compile(broker, context, source);
					t3 = System.currentTimeMillis();
				} else {
					context = (AnalysisXQueryContext) compiledQuery.getContext();
					context.prepareForReuse();
					t2 = System.currentTimeMillis();
				}
				return new QueryAnalysis(
						compiledQuery, Collections.unmodifiableSet(context.requiredVariables), Collections.unmodifiableSet(context.requiredFunctions));
			} finally {
				if (compiledQuery != null) {
					compiledQuery.getContext().runCleanupTasks();
					pool.returnCompiledXQuery(source, compiledQuery);
				}
			}
		} catch (XPathException e) {
			LOG.warn("query compilation failed --  " + query + "  -- " + (params == null ? "" : " with params " + Arrays.asList(params)) + (bindings.isEmpty() ? "" : " and bindings " + bindings));
			throw new DatabaseException("failed to compile query", e);
		} catch (IOException e) {
			throw new DatabaseException("unexpected exception", e);
		} catch (PermissionDeniedException e) {
			throw new DatabaseException("permission denied", e);
		} finally {
			db.releaseBroker(broker);
			STATS.update(query, t1, t2, t3, 0, System.currentTimeMillis());
		}
	}
	
	private static final class AnalysisXQueryContext extends XQueryContext {
		final Set<QName> requiredFunctions = new TreeSet<QName>();
		final Set<QName> requiredVariables = new TreeSet<QName>();

		private AnalysisXQueryContext(DBBroker broker) {
			super(broker.getBrokerPool());
		}

		@Override public Variable resolveVariable(org.exist.dom.QName qname) throws XPathException {
			Variable var = super.resolveVariable(qname);
			if (var == null) {
				requiredVariables.add(new QName(qname.getNamespaceURI(), qname.getLocalPart(), qname.getPrefix()));
				var = new VariableImpl(qname);
			}
			return var;
		}

		@Override public UserDefinedFunction resolveFunction(org.exist.dom.QName qname, int argCount) throws XPathException {
			UserDefinedFunction func = super.resolveFunction(qname, argCount);
			if (func == null) {
				requiredFunctions.add(new QName(qname.getNamespaceURI(), qname.getLocalPart(), qname.getPrefix()));
				func = new UserDefinedFunction(this, new FunctionSignature(qname, null, new SequenceType(Type.ITEM, org.exist.xquery.Cardinality.ZERO_OR_MORE), true));
				func.setFunctionBody(new SequenceConstructor(this));
			}
			return func;
		}
	}

	/**
	 * An access point for running various analyses on a query.
	 */
	public static class QueryAnalysis {
		private final CompiledXQuery query;
		private final Set<QName> requiredVariables;
		private final Set<QName> requiredFunctions;
		
		private QueryAnalysis(CompiledXQuery query, Set<QName> requiredVariables, Set<QName> requiredFunctions) {
			this.query = query;
			this.requiredVariables = requiredVariables;
			this.requiredFunctions = requiredFunctions;
		}
		
		/**
		 * Return the name of the statically determined return type of the query expression.
		 * The name is in a standard form, see {@link org.exist.xquery.value.Type} for a list
		 * of possible values.  If the return type cannot be statically determined, it defaults to
		 * <code>Type.ITEM</code>, the universal supertype in XQuery.
		 *
		 * @return the name of the return type of the query being analyzed
		 */
		public String returnTypeName() {
			return org.exist.xquery.value.Type.getTypeName(
					query instanceof Expression ? ((PathExpr) query).returnsType() : org.exist.xquery.value.Type.ITEM);
		}
		
		/**
		 * The enumeration of recognized cardinalities for parameter and return types.
		 */
		public static enum Cardinality {ZERO, ZERO_OR_ONE, ONE, ZERO_OR_MORE, ONE_OR_MORE}
		
		/**
		 * Return the statically determined cardinality of the return type of the query expression.
		 * If the cardinality cannot be statically determined, it defaults to <code>ZERO_OR_MORE</code>,
		 * the least restrictive cardinality.
		 *
		 * @return the cardinality of the return type of the query being analyzed
		 */
		public Cardinality cardinality() {
			if (query instanceof Expression) {
				int cardinality = ((Expression) query).getCardinality();
				switch (cardinality) {
					case org.exist.xquery.Cardinality.EMPTY: return Cardinality.ZERO;
					case org.exist.xquery.Cardinality.EXACTLY_ONE: return Cardinality.ONE;
					case org.exist.xquery.Cardinality.ZERO_OR_ONE: return Cardinality.ZERO_OR_ONE;
					case org.exist.xquery.Cardinality.ZERO_OR_MORE: return Cardinality.ZERO_OR_MORE;
					case org.exist.xquery.Cardinality.ONE_OR_MORE: return Cardinality.ONE_OR_MORE;
					default:
						LOG.error("unexpected eXist cardinality flag " + cardinality);
				}
			}
			return Cardinality.ZERO_OR_MORE;
		}
		
		/**
		 * Return a list of variables that are required to be defined by this query, excluding any
		 * positional variables that were provided to the {@link QueryService#analyze(String, Object[]) analyze}
		 * method.  The variable names will not include the leading '$'.
		 * 
		 * @return a list of variables required by this query
		 */
		public Set<QName> requiredVariables() {
			return requiredVariables;
		}
		
		/**
		 * Return a list of functions that are required to be defined by this query, beyond the
		 * standard XPath/XQuery ones.
		 *
		 * @return a list of functions required by this query
		 */
		public Set<QName> requiredFunctions() {
			return requiredFunctions;
		}
	}
	
	public static class Statistics {
		private static final NumberFormat COUNT_FORMAT = NumberFormat.getIntegerInstance();
		private static final MessageFormat FULL_ENTRY_FORMAT = new MessageFormat(
				"{1} uses in {3,number,0.000}s ({11,number,percent}, {7,number,0.00}ms avg) [" +
				"{4,number,0.000}s compiling ({8,number,0.00}ms avg, {2,number,percent} cache hits), " +
				"{5,number,0.000}s preparing ({9,number,0.00}ms avg), {6,number,0.000}s executing ({10,number,0.00}ms avg)" +
				"]: {0}");
		private static final MessageFormat STAND_ALONE_ENTRY_FORMAT = new MessageFormat(
				"{1,number,integer} uses in {3,number,0.000}s ({7,number,0.00}ms avg) [" +
				"{4,number,0.000}s compiling ({8,number,0.00}ms avg, {2,number,percent} cache hits), " +
				"{5,number,0.000}s preparing ({9,number,0.00}ms avg), {6,number,0.000}s executing ({10,number,0.00}ms avg)" +
				"]: {0}");
		
		private static final Comparator<Entry> TOTAL_TIME_DESCENDING = new Comparator<Entry>() {
			public int compare(Entry e1, Entry e2) {
				return e1.queryTime == e2.queryTime ? 0 : (e1.queryTime > e2.queryTime ? -1 : 1); 
			}
		};
		
		private final Map<String, Entry> entries = new HashMap<String, Entry>();
		
		void update(String query, long t1, long t2, long t3, long t4, long t5) {
			long tQuery = t5 - t1, tPreparation = t2 > 0 ? t2 - t1 : -1, tCompilation = t3 > 0 ? t3 - t2 : -1, tExecution = t4 > 0 ? t4 - (t3 > 0 ? t3 : t2) : -1;
			get(null).update(tQuery, tPreparation, tCompilation, tExecution);
			get(query).update(tQuery, tPreparation, tCompilation, tExecution);
		}
		
		synchronized Entry get(String query) {
			Entry entry = entries.get(query);
			if (entry == null) entries.put(query, entry = new Entry(query));
			return entry;
		}
		
		/**
		 * Get a list of all statistics entries for which data has been gathered.  The list is a copy
		 * and can be further manipulating without affecting the service.
		 *
		 * @return a list of all statistics entries
		 */
		public synchronized List<Entry> entries() {
			return new ArrayList<Entry>(entries.values());
		}
		
		/**
		 * Get the entry that aggregates statistics over all the queries.
		 *
		 * @return the totals entry
		 */
		public Entry totals() {
			return get(null);
		}
		
		/**
		 * Reset all gathered statistics back to zero.
		 */
		public synchronized void reset() {
			entries.clear();
		}
		
		/**
		 * Return a string that describes the statistics gathered for all the entries.
		 * 
		 * @return a string describing the statistics gathered so far
		 */
		public synchronized String toString() {
			return toStringTop(entries.size());
		}
		
		/**
		 * Return a string that describes the statistics for the top n entries, sorted by
		 * descending order of total time spent dealing with the query.  This will always
		 * include the totals entry in the first position.
		 *
		 * @param n the desired number of entries to describe
		 * @return a string describing the statistics for the top n entries
		 */
		public synchronized String toStringTop(int n) {
			StringBuilder out = new StringBuilder();
			List<Entry> list = entries();
			if (list.isEmpty()) return "<no queries executed>";
			Collections.sort(list, TOTAL_TIME_DESCENDING);
			int maxCountLength = COUNT_FORMAT.format(list.get(0).numQueries).length();
			double totalDuration = list.get(0).queryTime;
			for (Entry entry : list.subList(0, Math.min(n, list.size()))) out.append(entry.toString(maxCountLength, totalDuration)).append('\n');
			return out.toString();
		}
		
		/**
		 * Performance counters for a single query.  The fields are public for convenience (and to avoid
		 * a forest of accessors) but should be considered as read-only.
		 *
		 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
		 */
		public static class Entry {
			/**
			 * The query string (after pre-substitution) that this entry is about.  If <code>null</code>
			 * then this is the totals entry.
			 */
			public final String query;
			
			// These are simple counters
			public long numQueries, queriesPrepared, queriesCompiled, queriesRun;
			
			// All times are in seconds.
			public double queryTime, queryPreparationTime, queryCompilationTime, queryRunTime;
			
			Entry(String query) {
				this.query = query;
			}
			
			synchronized void update(long tQuery, long tPreparation, long tCompilation, long tRun) {
				numQueries++;
				queryTime += tQuery / 1000.0;
				if (tPreparation >= 0) {
					queriesPrepared++;
					queryPreparationTime += tPreparation / 1000.0;
				}
				if (tCompilation >= 0) {
					queriesCompiled++;
					queryCompilationTime += tCompilation / 1000.0;
				}
				if (tRun >= 0) {
					queryRunTime += tRun / 1000.0;
					queriesRun++;
				}
			}
			
			public synchronized String toString(int maxCountLength, double totalDuration) {
				String formattedCount = String.format("%" + maxCountLength + "s", COUNT_FORMAT.format(numQueries));
				return FULL_ENTRY_FORMAT.format(new Object[] {
						query == null ? "TOTALS" : query,
						formattedCount,
						(queriesPrepared - queriesCompiled) / (double) queriesPrepared,
						queryTime,
						queryCompilationTime,
						queryPreparationTime,
						queryRunTime,
						queryTime * 1000 / numQueries,
						queriesCompiled == 0 ? 0 : queryCompilationTime * 1000 / queriesCompiled,
						queriesPrepared == 0 ? 0 : queryPreparationTime * 1000 / queriesPrepared,
						queriesRun == 0 ? 0 : queryRunTime * 1000 / queriesRun,
						queryTime / totalDuration
				});
			}

			@Override public synchronized String toString() {
				return STAND_ALONE_ENTRY_FORMAT.format(new Object[] {
						query == null ? "TOTALS" : query,
						numQueries,
						(queriesPrepared - queriesCompiled) / (double) queriesPrepared,
						queryTime,
						queryCompilationTime,
						queryPreparationTime,
						queryRunTime,
						queryTime * 1000 / numQueries,
						queriesCompiled == 0 ? 0 : queryCompilationTime * 1000 / queriesCompiled,
						queriesPrepared == 0 ? 0 : queryPreparationTime * 1000 / queriesPrepared,
						queriesRun == 0 ? 0 : queryRunTime * 1000 / queriesRun
				});
			}
		}
	}

}
