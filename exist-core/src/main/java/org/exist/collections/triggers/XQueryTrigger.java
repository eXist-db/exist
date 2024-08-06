/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.collections.triggers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

/**
 * A trigger that executes a user XQuery statement when invoked.
 * 
 * The XQuery source executed is the value of the parameter named "query" or the
 * query at the URL indicated by the parameter named "url".
 * 
 * Any additional parameters will be declared as external variables with the type xs:string
 * 
 * These external variables for the Trigger are accessible to the user XQuery statement
 * <code>xxx:type</code> : the type of event for the Trigger. Either "prepare" or "finish"
 * <code>xxx:collection</code> : the uri of the collection from which the event is triggered
 * <code>xxx:uri</code> : the uri of the document or collection from which the event is triggered
 * <code>xxx:new-uri</code> : the new uri of the document or collection from which the event is triggered
 * <code>xxx:event</code> : the kind of triggered event
 * xxx is the namespace prefix within the XQuery, can be set by the variable "bindingPrefix"
 * 
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 * @author <a href="mailto:gazdovsky@gmail.com">Evgeny Gazdovsky</a>
*/
public class XQueryTrigger extends SAXTrigger implements DocumentTrigger, CollectionTrigger {

    private static final Logger LOG = LogManager.getLogger(XQueryTrigger.class);
    
	private static final String NAMESPACE = "http://exist-db.org/xquery/trigger";
	
	private static final String DEFAULT_BINDING_PREFIX = "local:";
	
	public static final QName beforeCreateCollection = new QName("before-create-collection", NAMESPACE);
	public static final QName afterCreateCollection = new QName("after-create-collection", NAMESPACE);

	public static final QName beforeUpdateCollection = new QName("before-update-collection", NAMESPACE);
	public static final QName afterUpdateCollection = new QName("after-update-collection", NAMESPACE);

	public static final QName beforeCopyCollection = new QName("before-copy-collection", NAMESPACE);
	public static final QName afterCopyCollection = new QName("after-copy-collection", NAMESPACE);

	public static final QName beforeMoveCollection = new QName("before-move-collection", NAMESPACE);
	public static final QName afterMoveCollection = new QName("after-move-collection", NAMESPACE);

	public static final QName beforeDeleteCollection = new QName("before-delete-collection", NAMESPACE);
	public static final QName afterDeleteCollection = new QName("after-delete-collection", NAMESPACE);

	public static final QName beforeCreateDocument = new QName("before-create-document", NAMESPACE);
	public static final QName afterCreateDocument = new QName("after-create-document", NAMESPACE);

	public static final QName beforeUpdateDocument = new QName("before-update-document", NAMESPACE);
	public static final QName afterUpdateDocument = new QName("after-update-document", NAMESPACE);
	
	public static final QName beforeCopyDocument = new QName("before-copy-document", NAMESPACE);
	public static final QName afterCopyDocument = new QName("after-copy-document", NAMESPACE);

	public static final QName beforeMoveDocument = new QName("before-move-document", NAMESPACE);
	public static final QName afterMoveDocument = new QName("after-move-document", NAMESPACE);

	public static final QName beforeDeleteDocument = new QName("before-delete-document", NAMESPACE);
	public static final QName afterDeleteDocument = new QName("after-delete-document", NAMESPACE);

	private Set<TriggerEvent> events = null;
	private Collection collection = null;
	private String strQuery = null;
	private String urlQuery = null;
	private Properties userDefinedVariables = null;
	
	/** Namespace prefix associated to trigger */
	private String bindingPrefix = null;
	private XQuery service;

    public final static String PREPARE_EXCEPTION_MESSAGE = "Error during trigger prepare";

    @Override
	public void configure(final DBBroker broker, final Txn transaction, final Collection parent, final Map<String, List<?>> parameters) throws TriggerException {
 		this.collection = parent;
 		
 		//for an XQuery trigger there must be at least
 		//one parameter to specify the XQuery
 		if (parameters != null) {
 			final List<String> paramEvents = (List<String>) parameters.get("event");
 			if (paramEvents != null) {
				for (final String event : paramEvents) {
					if (this.events == null) {
						this.events = EnumSet.noneOf(TriggerEvent.class);
					}
					this.events.addAll(TriggerEvent.convertFromOldDesign(event));
					this.events.addAll(TriggerEvent.convertFromLegacyEventNamesString(event));
				}
			}

 			final List<String> urlQueries = (List<String>) parameters.get("url");
            this.urlQuery = urlQueries != null ? urlQueries.get(0) : null;

            final List<String> strQueries = (List<String>) parameters.get("query");
 			this.strQuery = strQueries != null ? strQueries.get(0) : null;

			for (final Map.Entry<String, List<?>> entry : parameters.entrySet()) {
 				final String paramName = entry.getKey();
				final Object paramValue = entry.getValue().get(0);

 				//get the binding prefix (if any)
 				if ("bindingPrefix".equals(paramName)) {
					final String bindingPrefix = (String) paramValue;
 					if (bindingPrefix != null && !bindingPrefix.trim().isEmpty()) {
 						this.bindingPrefix = bindingPrefix.trim() + ":";
 					}
 				}

 				//get the URL of the query (if any)
 				else if ("url".equals(paramName)) {
					this.urlQuery = (String) paramValue;
 				}

 				//get the query (if any)
 				else if ("query".equals(paramName)) {
					this.strQuery = (String) paramValue;
 				}

 				//make any other parameters available as external variables for the query
 				else {
                    //TODO could be enhanced to setup a sequence etc
					if (userDefinedVariables == null) {
						this.userDefinedVariables = new Properties();
					}
 					this.userDefinedVariables.put(paramName, paramValue);
 				}
 			}
 			
 			//set a default binding prefix if none was specified
 			if (this.bindingPrefix == null) {
 				this.bindingPrefix = DEFAULT_BINDING_PREFIX;
 			}
 			
 			//old
 			if (this.urlQuery != null || this.strQuery != null) {
				this.service = broker.getBrokerPool().getXQueryService();
				return;
 			}
 		}
 		
 		//no query to execute
		LOG.error("XQuery Trigger for: '{}' is missing its XQuery parameter", parent.getURI());
	}
	
	/**
	 * Get's a Source for the Trigger's XQuery
	 * 
	 * @param broker the database broker
	 * 
	 * @return the Source for the XQuery 
	 */
	private Source getQuerySource(final DBBroker broker) {
		Source querySource = null;
		
		//try and get the XQuery from a URL
		if (urlQuery != null) {
			try {
				querySource = SourceFactory.getSource(broker, null, urlQuery, false);
			} catch (final Exception e) {
				LOG.error(e);
			}
		} else if (strQuery != null) {
			//try and get the XQuery from a string
			querySource = new StringSource(strQuery);
		}
	
		return querySource;
	}
	
	private void prepare(final TriggerEvent event, final DBBroker broker, final Txn transaction, final XmldbURI src, final XmldbURI dst, final boolean isCollection) throws TriggerException {
		//get the query
		final Source query = getQuerySource(broker);
		if (query == null) {
			return;
		}

		// avoid infinite recursion
		try {
			TriggerStatePerThread.setAndTest(this, TriggerPhase.BEFORE, event, src, dst);
		} catch (final TriggerStatePerThread.CyclicTriggerException e) {
			LOG.warn(e.getMessage());
			return;
		}
		
		final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        CompiledXQuery compiledQuery = null;
        try {
        	//compile the XQuery
        	compiledQuery = service.compile(context, query);
			declareExternalVariables(context, TriggerPhase.BEFORE, event, src, dst, isCollection);
        	
        } catch (final XPathException | IOException | PermissionDeniedException e) {
    		TriggerStatePerThread.clear();
        	throw new TriggerException(PREPARE_EXCEPTION_MESSAGE, e);
	    }

        //execute the XQuery
        try {
        	//TODO : should we provide another contextSet ?
	        final NodeSet contextSet = NodeSet.EMPTY_SET;
			service.execute(broker, compiledQuery, contextSet);
			//TODO : should we have a special processing ?
			if (LOG.isDebugEnabled()) {
				LOG.debug("Trigger fired for prepare");
			}
        } catch (final XPathException | PermissionDeniedException e) {
			TriggerStatePerThread.clear();
        	throw new TriggerException(PREPARE_EXCEPTION_MESSAGE, e);
        }
    }
    
	private void finish(final TriggerEvent event, final DBBroker broker, final Txn transaction, final XmldbURI src, final XmldbURI dst, final boolean isCollection) {
    	//get the query
    	final Source query = getQuerySource(broker);
		if (query == null) {
			return;
		}

		// avoid infinite recursion
		try {
			TriggerStatePerThread.setAndTest(this, TriggerPhase.AFTER, event, src, dst);
		} catch (final TriggerStatePerThread.CyclicTriggerException e) {
			LOG.warn(e.getMessage());
			return;
		}
		
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        CompiledXQuery compiledQuery = null;
        try {
        	//compile the XQuery
        	compiledQuery = service.compile(context, query);
			declareExternalVariables(context, TriggerPhase.AFTER, event, src, dst, isCollection);

        } catch (final XPathException | IOException | PermissionDeniedException e) {
        	//Should never be reached
        	LOG.error(e);
	    }

        //execute the XQuery
        try {
        	//TODO : should we provide another contextSet ?
	        final NodeSet contextSet = NodeSet.EMPTY_SET;	        
			service.execute(broker, compiledQuery, contextSet);
			//TODO : should we have a special processing ?
        } catch (final XPathException e) {
        	//Should never be reached
			LOG.error("Error during trigger finish", e);
        } catch (final PermissionDeniedException e) {
        	//Should never be reached
        	LOG.error(e);
        }

		TriggerStatePerThread.clearIfFinished(TriggerPhase.AFTER);

		if (LOG.isDebugEnabled()) {
			LOG.debug("Trigger fired for finish");
		}
	}

	private void declareExternalVariables(final XQueryContext context, final TriggerPhase phase, final TriggerEvent event, final XmldbURI src, final XmldbURI dst, final boolean isCollection) throws XPathException {
		//declare external variables
		context.declareVariable(bindingPrefix + "type", new StringValue(phase.legacyPhaseName()));
		context.declareVariable(bindingPrefix + "event", new StringValue(event.legacyEventName()));
		if (isCollection) {
			context.declareVariable(bindingPrefix + "collection", new AnyURIValue(src));
		} else {
			context.declareVariable(bindingPrefix + "collection", new AnyURIValue(src.removeLastSegment()));
		}
		context.declareVariable(bindingPrefix + "uri", new AnyURIValue(src));
		if (dst == null) {
			context.declareVariable(bindingPrefix + "new-uri", Sequence.EMPTY_SEQUENCE);
		} else {
			context.declareVariable(bindingPrefix + "new-uri", new AnyURIValue(dst));
		}

		// For backward compatibility
		context.declareVariable(bindingPrefix + "eventType", new StringValue(phase.legacyPhaseName()));
		context.declareVariable(bindingPrefix + "triggerEvent", new StringValue(event.legacyEventName()));
		if (isCollection) {
			context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(src));
		} else {
			context.declareVariable(bindingPrefix + "collectionName", new AnyURIValue(src.removeLastSegment()));
			context.declareVariable(bindingPrefix + "documentName", new AnyURIValue(src));
		}

		//declare user defined parameters as external variables
		if (userDefinedVariables != null) {
			for (final Object o : userDefinedVariables.keySet()) {
				final String varName = (String) o;
				final String varValue = userDefinedVariables.getProperty(varName);

				context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
			}
		}
	}

	private CompiledXQuery getScript(final DBBroker broker, final Txn transaction) throws TriggerException {
		
		//get the query
		final Source query = getQuerySource(broker);
		if(query == null) {
			return null;
		}
		
		final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        if (query instanceof DBSource) {
            context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI_PREFIX + ((DBSource)query).getDocumentPath().removeLastSegment().toString());
        }

        CompiledXQuery compiledQuery;
        try {
        	//compile the XQuery
        	compiledQuery = service.compile(context, query);

        	//declare user defined parameters as external variables
			if (userDefinedVariables != null) {
				for (final Object o : userDefinedVariables.keySet()) {
					final String varName = (String) o;
					final String varValue = userDefinedVariables.getProperty(varName);

					context.declareVariable(bindingPrefix + varName, new StringValue(varValue));
				}
			}
        	
        	//reset & prepareForExecution for execution
        	compiledQuery.reset();

        	context.getWatchDog().reset();

            //do any preparation before execution
            context.prepareForExecution();

        	return compiledQuery;
        } catch(final XPathException | IOException | PermissionDeniedException e) {
            LOG.warn(e.getMessage(), e);
        	throw new TriggerException(PREPARE_EXCEPTION_MESSAGE, e);
	    }
    }

	private void execute(final TriggerPhase phase, final TriggerEvent event, final DBBroker broker, final Txn transaction, final QName functionName, final XmldbURI src, final XmldbURI dst) throws TriggerException {

		// avoid infinite recursion
		try {
			TriggerStatePerThread.setAndTest(this, phase, event, src, dst);
		} catch (final TriggerStatePerThread.CyclicTriggerException e) {
			LOG.warn("Skipping Trigger: {}", e.getMessage());
			return;
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("Execute: {} {}({}): {}", phase, event, src, getClass().getSimpleName());
		}

		final CompiledXQuery compiledQuery;
		try {
			compiledQuery = getScript(broker, transaction);
			if (compiledQuery == null) {
				// NOTE: can occur if there is no such XQueryTrigger library module available in the database
				return;
			}
		} catch (final TriggerException e) {
			TriggerStatePerThread.clear();
			throw e;
		}

		final XQueryContext context = compiledQuery.getContext();

		//execute the XQuery
        try {
            final int nParams;
            if (dst != null) {
				nParams = 2;
			} else {
				nParams = 1;
			}

			final List<Expression> args = new ArrayList<>(nParams);
			if (phase == TriggerPhase.BEFORE) {
				args.add(new LiteralValue(context, new AnyURIValue(src)));
				if (dst != null) {
					args.add(new LiteralValue(context, new AnyURIValue(dst)));
				}
			} else {
				if (dst != null) {
					args.add(new LiteralValue(context, new AnyURIValue(dst)));
				}
				args.add(new LiteralValue(context, new AnyURIValue(src)));
			}

			service.execute(broker, compiledQuery, Tuple(functionName, args, Optional.empty()), null, null, true);
        } catch (final XPathException | PermissionDeniedException e) {
			// if the exception just indicates that there is no function in the trigger to call, then we can just log and return
			if (e instanceof XPathException) {
				final XPathException xpe = (XPathException) e;
				if (xpe.getErrorCode() == ErrorCodes.EXXQDY0005 || xpe.getErrorCode() == ErrorCodes.EXXQDY0006) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("No such function '" + functionName + "' in XQueryTrigger: " + compiledQuery.getSource());
					}
					TriggerStatePerThread.clearIfFinished(phase);
					return;
				}
			}

			TriggerStatePerThread.clear();
        	throw new TriggerException(PREPARE_EXCEPTION_MESSAGE, e);
        } finally {
    		compiledQuery.reset();
        }

		TriggerStatePerThread.clearIfFinished(phase);

		if (LOG.isDebugEnabled()) {
			if (phase == TriggerPhase.AFTER) {
				LOG.debug("Trigger fired 'after'");
			} else {
				LOG.debug("Trigger fired 'before'");
			}
		}
	}

	String getUrlQuery() {
		return urlQuery;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final XQueryTrigger that = (XQueryTrigger) o;

		if (events != null ? !events.equals(that.events) : that.events != null) {
			return false;
		}

		if (collection != null ? !collection.equals(that.collection) : that.collection != null) {
			return false;
		}

		if (strQuery != null ? !strQuery.equals(that.strQuery) : that.strQuery != null) {
			return false;
		}

		if (urlQuery != null ? !urlQuery.equals(that.urlQuery) : that.urlQuery != null) {
			return false;
		}

		if (userDefinedVariables != null ? !userDefinedVariables.equals(that.userDefinedVariables) : that.userDefinedVariables != null) {
			return false;
		}

		return bindingPrefix != null ? bindingPrefix.equals(that.bindingPrefix) : that.bindingPrefix == null;
	}

	//Collection's methods

	@Override
	public void beforeCreateCollection(final DBBroker broker, final Txn txn, final XmldbURI uri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.CREATE_COLLECTION)) {
			prepare(TriggerEvent.CREATE_COLLECTION, broker, txn, uri, null, true);
		} else {
            execute(TriggerPhase.BEFORE, TriggerEvent.CREATE_COLLECTION, broker, txn, beforeCreateCollection, uri, null);
	    }
	}

	@Override
	public void afterCreateCollection(final DBBroker broker, final Txn txn, final Collection collection) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.CREATE_COLLECTION)) {
			finish(TriggerEvent.CREATE_COLLECTION, broker, txn, collection.getURI(), null, true);
		} else {
            execute(TriggerPhase.AFTER, TriggerEvent.CREATE_COLLECTION, broker, txn, afterCreateCollection, collection.getURI(), null);
	    }

	}

	@Override
	public void beforeCopyCollection(final DBBroker broker, final Txn txn, final Collection collection, final XmldbURI newUri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.COPY_COLLECTION)) {
			prepare(TriggerEvent.COPY_COLLECTION, broker, txn, collection.getURI(), newUri, true);
		} else {
		    execute(TriggerPhase.BEFORE, TriggerEvent.COPY_COLLECTION, broker, txn, beforeCopyCollection, collection.getURI(), newUri);
	    }
	}

	@Override
	public void afterCopyCollection(final DBBroker broker, final Txn txn, final Collection collection, final XmldbURI oldUri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.COPY_COLLECTION)) {
			finish(TriggerEvent.COPY_COLLECTION, broker, txn, collection.getURI(), oldUri, true);
		} else {
            execute(TriggerPhase.AFTER, TriggerEvent.COPY_COLLECTION, broker, txn, afterCopyCollection, oldUri, collection.getURI());
	    }
	}

	@Override
	public void beforeMoveCollection(final DBBroker broker, final Txn txn, final Collection collection, final XmldbURI newUri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.MOVE_COLLECTION)) {
			prepare(TriggerEvent.MOVE_COLLECTION, broker, txn, collection.getURI(), newUri, true);
		} else {
		    execute(TriggerPhase.BEFORE, TriggerEvent.MOVE_COLLECTION, broker, txn, beforeMoveCollection, collection.getURI(), newUri);
	    }
	}

	@Override
	public void afterMoveCollection(final DBBroker broker, final Txn txn, final Collection collection, final XmldbURI oldUri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.MOVE_COLLECTION)) {
			finish(TriggerEvent.MOVE_COLLECTION, broker, txn, oldUri, collection.getURI(), true);
		} else {
		    execute(TriggerPhase.AFTER, TriggerEvent.MOVE_COLLECTION, broker, txn, afterMoveCollection, oldUri, collection.getURI());
	    }
	}

	@Override
	public void beforeDeleteCollection(final DBBroker broker, final Txn txn, final Collection collection) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.DELETE_COLLECTION)) {
			prepare(TriggerEvent.DELETE_COLLECTION, broker, txn, collection.getURI(), null, true);
		} else {
            execute(TriggerPhase.BEFORE, TriggerEvent.DELETE_COLLECTION, broker, txn, beforeDeleteCollection, collection.getURI(), null);
	    }
	}

	@Override
	public void afterDeleteCollection(final DBBroker broker, final Txn txn, final XmldbURI uri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.DELETE_COLLECTION)) {
			finish(TriggerEvent.DELETE_COLLECTION, broker, txn, collection.getURI(), null, true);
		} else {
            execute(TriggerPhase.AFTER, TriggerEvent.DELETE_COLLECTION, broker, txn, afterDeleteCollection, uri, null);
	    }
	}

	@Override
	public void beforeCreateDocument(final DBBroker broker, final Txn txn, final XmldbURI uri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.CREATE_DOCUMENT)) {
			prepare(TriggerEvent.CREATE_DOCUMENT, broker, txn, uri, null, false);
		} else {
            execute(TriggerPhase.BEFORE, TriggerEvent.CREATE_DOCUMENT, broker, txn, beforeCreateDocument, uri, null);
	    }
	}

	@Override
	public void afterCreateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.CREATE_DOCUMENT)) {
			finish(TriggerEvent.CREATE_DOCUMENT, broker, txn, document.getURI(), null, false);
		} else {
            execute(TriggerPhase.AFTER, TriggerEvent.CREATE_DOCUMENT, broker, txn, afterCreateDocument, document.getURI(), null);
	    }
	}

	@Override
	public void beforeUpdateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.UPDATE_DOCUMENT)) {
			prepare(TriggerEvent.UPDATE_DOCUMENT, broker, txn, document.getURI(), null, false);
		} else {
            execute(TriggerPhase.BEFORE, TriggerEvent.UPDATE_DOCUMENT, broker, txn, beforeUpdateDocument, document.getURI(), null);
	    }
	}

	@Override
	public void afterUpdateDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.UPDATE_DOCUMENT)) {
			finish(TriggerEvent.UPDATE_DOCUMENT, broker, txn, document.getURI(), null, false);
		} else {
            execute(TriggerPhase.AFTER, TriggerEvent.UPDATE_DOCUMENT, broker, txn, afterUpdateDocument, document.getURI(), null);
	    }
	}

	@Override
	public void beforeCopyDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.COPY_DOCUMENT)) {
			prepare(TriggerEvent.COPY_DOCUMENT, broker, txn, document.getURI(), newUri, false);
		} else {
            execute(TriggerPhase.BEFORE, TriggerEvent.COPY_DOCUMENT, broker, txn, beforeCopyDocument, document.getURI(), newUri);
	    }
	}

	@Override
    public void afterCopyDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI oldUri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.COPY_DOCUMENT)) {
			finish(TriggerEvent.COPY_DOCUMENT, broker, txn, document.getURI(), oldUri, false);
		} else {
            execute(TriggerPhase.AFTER, TriggerEvent.COPY_DOCUMENT, broker, txn, afterCopyDocument, oldUri, document.getURI());
	    }
	}

	@Override
	public void beforeMoveDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.MOVE_DOCUMENT)) {
			prepare(TriggerEvent.MOVE_DOCUMENT, broker, txn, document.getURI(), newUri, false);
		} else {
            execute(TriggerPhase.BEFORE, TriggerEvent.MOVE_DOCUMENT, broker, txn, beforeMoveDocument, document.getURI(), newUri);
	    }
	}

	@Override
	public void afterMoveDocument(final DBBroker broker, final Txn txn, final DocumentImpl document, final XmldbURI oldUri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.MOVE_DOCUMENT)) {
			finish(TriggerEvent.MOVE_DOCUMENT, broker, txn, oldUri, document.getURI(), false);
		} else {
            execute(TriggerPhase.AFTER, TriggerEvent.MOVE_DOCUMENT, broker, txn, afterMoveDocument, oldUri, document.getURI());
	    }
	}

	@Override
	public void beforeDeleteDocument(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.DELETE_DOCUMENT)) {
			prepare(TriggerEvent.DELETE_DOCUMENT, broker, txn, document.getURI(), null, false);
		} else {
            execute(TriggerPhase.BEFORE, TriggerEvent.DELETE_DOCUMENT, broker, txn, beforeDeleteDocument, document.getURI(), null);
	    }
	}

	@Override
	public void afterDeleteDocument(final DBBroker broker, final Txn txn, final XmldbURI uri) throws TriggerException {
		if (events != null && events.contains(TriggerEvent.DELETE_DOCUMENT)) {
			finish(TriggerEvent.DELETE_DOCUMENT, broker, txn, uri, null, false);
		} else {
            execute(TriggerPhase.AFTER, TriggerEvent.DELETE_DOCUMENT, broker, txn, afterDeleteDocument, uri, null);
	    }
	}

	@Override
	public void beforeUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
		// NOTE: not yet implemented
	}

	@Override
	public void afterUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
		// NOTE: not yet implemented
	}
}
