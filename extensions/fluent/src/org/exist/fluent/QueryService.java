package org.exist.fluent;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentSet;
import org.exist.security.xacml.AccessContext;
import org.exist.source.*;
import org.exist.storage.*;
import org.exist.xquery.*;
import org.exist.xquery.functions.*;
import org.exist.xquery.value.*;

/**
 * Provides facilities for performing queries on a database.  It cannot
 * be instantiated directly; you must obtain an instance from a resource or the database.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class QueryService implements Cloneable {
	
	private static final Pattern PRE_SUB_PATTERN = Pattern.compile("\\$(\\d+)");
	private static final Logger LOG = Logger.getLogger(QueryService.class);
	
	private NamespaceMap namespaceBindings;
	private final Database db;
	protected DocumentSet docs;
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
		return !presub && bindings.isEmpty() && (namespaceBindings == null || namespaceBindings.isFreshFrom(origin.namespaceBindings()));
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
		if (presub) query = presub(query, params);
		DBBroker broker = null;
		try {
			broker = db.acquireBroker();
			prepareContext(broker);
			Map<String, String> combinedMap = namespaceBindings.getCombinedMap();
			org.exist.source.Source source = new NamespacedStringSource(query, combinedMap);
			XQuery xquery = broker.getXQueryService();
			XQueryPool pool = xquery.getXQueryPool();
			CompiledXQuery compiledQuery = pool.borrowCompiledXQuery(broker, source);
			try {
				XQueryContext context = compiledQuery == null
						? xquery.newContext(AccessContext.INTERNAL_PREFIX_LOOKUP)
						: compiledQuery.getContext();
				buildXQueryContext(context, params, combinedMap);
				if (compiledQuery == null) compiledQuery = xquery.compile(context, source);
				return new ItemList(xquery.execute(wrap(compiledQuery, wrapperFactory, context), base), namespaceBindings.extend(), db);
			} finally {
				if (compiledQuery != null) pool.returnCompiledXQuery(source, compiledQuery);
			}
		} catch (XPathException e) {
			LOG.debug("query execution failed --  " + query + "  -- " + (params == null ? "" : " with params " + Arrays.asList(params)) + (bindings.isEmpty() ? "" : " and bindings " + bindings));
			throw new DatabaseException("failed to execute query", e);
		} catch (IOException e) {
			throw new DatabaseException("unexpected exception", e);
		} finally {
			db.releaseBroker(broker);
		}
	}
	
	@SuppressWarnings("unchecked")
	private CompiledXQuery wrap(CompiledXQuery expr, WrapperFactory wrapperFactory, XQueryContext context) throws XPathException {
		if (wrapperFactory == null) return expr;
		Function wrapper = wrapperFactory.createWrapper(context);
		wrapper.setArguments(Collections.singletonList(expr));
		wrapper.setSource(expr.getSource());
		return wrapper;
	}

	private void buildXQueryContext(XQueryContext context, Object[] params, Map<String, String> combinedNamespaceMap) throws XPathException {
		context.declareNamespaces(combinedNamespaceMap);
		context.setBackwardsCompatibility(false);
		context.setStaticallyKnownDocuments(docs);
		context.setBaseURI(baseUri == null ? new AnyURIValue("/db") : baseUri);
		for (Map.Entry<QName, Object> entry : bindings.entrySet()) {
			context.declareVariable(
					new org.exist.dom.QName(entry.getKey().getLocalPart(), entry.getKey().getNamespaceURI(), entry.getKey().getPrefix()),
					convertValue(entry.getValue()));
		}
		if (params != null) for (int i = 0; i < params.length; i++) {
			context.declareVariable("_"+(i+1), convertValue(params[i]));
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
			matcher.appendReplacement(buf, (String) params[Integer.parseInt(matcher.group(1))-1]);
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
		
		final Collection<QName> requiredVariables = new TreeSet<QName>();
		final Collection<QName> requiredFunctions = new TreeSet<QName>();
		DBBroker broker = null;
		try {
			broker = db.acquireBroker();
			prepareContext(broker);
			Map<String, String> combinedMap = namespaceBindings.getCombinedMap();
			org.exist.source.Source source = new NamespacedStringSource(query, combinedMap);
			XQuery xquery = broker.getXQueryService();
			final XQueryContext context = new XQueryContext(broker, AccessContext.INTERNAL_PREFIX_LOOKUP) {
				@Override public Variable resolveVariable(org.exist.dom.QName qname) throws XPathException {
					Variable var = super.resolveVariable(qname);
					if (var == null) {
						requiredVariables.add(new QName(qname.getNamespaceURI(), qname.getLocalName(), qname.getPrefix()));
						var = new Variable(qname);
					}
					return var;
				}
				@Override public UserDefinedFunction resolveFunction(org.exist.dom.QName qname, int argCount) throws XPathException {
					UserDefinedFunction func = super.resolveFunction(qname, argCount);
					if (func == null) {
						requiredFunctions.add(new QName(qname.getNamespaceURI(), qname.getLocalName(), qname.getPrefix()));
						func = new UserDefinedFunction(this, new FunctionSignature(qname, null, new SequenceType(Type.ITEM, org.exist.xquery.Cardinality.ZERO_OR_MORE), true));
						func.setFunctionBody(new SequenceConstructor(this));
					}
					return func;
				}
			};
			buildXQueryContext(context, params, combinedMap);
			return new QueryAnalysis(
					xquery.compile(context, source),
					Collections.unmodifiableCollection(requiredVariables),
					Collections.unmodifiableCollection(requiredFunctions));
		} catch (XPathException e) {
			LOG.warn("query compilation failed --  " + query + "  -- " + (params == null ? "" : " with params " + Arrays.asList(params)) + (bindings.isEmpty() ? "" : " and bindings " + bindings));
			throw new DatabaseException("failed to compile query", e);
		} catch (IOException e) {
			throw new DatabaseException("unexpected exception", e);
		} finally {
			db.releaseBroker(broker);
		}
	}
	
	/**
	 * An access point for running various analyses on a query.
	 */
	public static class QueryAnalysis {
		private final CompiledXQuery query;
		private final Collection<QName> requiredVariables;
		private final Collection<QName> requiredFunctions;
		
		private QueryAnalysis(CompiledXQuery query, Collection<QName> requiredVariables, Collection<QName> requiredFunctions) {
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
		public Collection<QName> requiredVariables() {
			return requiredVariables;
		}
		
		/**
		 * Return a list of functions that are required to be defined by this query, beyond the
		 * standard XPath/XQuery ones.
		 *
		 * @return a list of functions required by this query
		 */
		public Collection<QName> requiredFunctions() {
			return requiredFunctions;
		}
	}
}
