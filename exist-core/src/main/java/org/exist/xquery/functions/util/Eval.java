/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.Properties;
import java.util.SimpleTimeZone;

import javax.annotation.Nullable;
import javax.xml.datatype.Duration;

import org.exist.Namespaces;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.ReferenceNode;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.XMLReaderPool;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.functions.fn.FnModule;
import org.exist.xquery.functions.fn.FunSerialize;
import org.exist.xquery.functions.fn.FunSubSequence;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.util.UtilModule.functionSignatures;

/**
 * @author wolf
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class Eval extends BasicFunction {

    private static final String evalArgumentText = "The expression to be evaluated.  If it is of type xs:string, the function " +
            "tries to execute this string as the query. If the first argument is of type xs:anyURI, " +
            "the function will try to load the query from the resource to which the URI resolves. " +
            "If the URI has no scheme, it is assumed that the query is stored in the db and the " +
            "URI is interpreted as a database path. This is the same as calling " +
            "util:eval(xs:anyURI('xmldb:exist:///db/test/test.xq')). " +
            //TODO : to be discussed ; until now, it's been used with a null context
            "The query inherits the current execution context, i.e. all " +
            "namespace declarations and variable declarations are visible from within the " +
            "inner expression. " +
            "The function returns an empty sequence if a whitespace string is passed.";

    private static final String contextArgumentText = "The query inherits the context described by the XML fragment in this parameter. " +
            "It should have the format:\n" +
            "<static-context>\n" +
            "\t<output-size-limit value=\"-1\"/>\n" +
            "\t<unbind-namespace uri=\"http://exist.sourceforge.net/NS/exist\"/>\n" +
            "\t<current-dateTime value=\"dateTime\"/>\n" +
            "\t<implicit-timezone value=\"duration\"/>\n" +
            "\t<variable name=\"qname\">variable value</variable>\n" +
            "\t<default-context>explicitly provide default context here</default-context>\n" +
            "\t<mapModule namespace=\"uri\" uri=\"uri_to_module\"/>\n" +
            "</static-context>.\n";

    private static final FunctionParameterSequenceType FS_PARAM_EXPRESSION = param(
            "expression", Type.ITEM, evalArgumentText);

    private static final FunctionParameterSequenceType FS_PARAM_INLINE_CONTEXT = optParam(
            "inline-context", Type.ITEM, "The inline context");

    private static final FunctionParameterSequenceType FS_PARAM_EVAL_CONTEXT_ITEM = optParam(
            "eval-context-item", Type.ITEM, "the context item against which the expression will be evaluated");

    private static final FunctionParameterSequenceType FS_PARAM_CONTEXT = optParam(
            "context", Type.NODE, contextArgumentText);

    private static final FunctionParameterSequenceType FS_PARAM_CACHE = param(
            "cache-flag", Type.BOOLEAN, "The flag for whether the compiled query should be cached. The cached query" +
                    "will be globally available within the db instance.");

    private static final FunctionParameterSequenceType FS_PARAM_PASS = param(
            "pass", Type.BOOLEAN, "Passes on the original error info (line and column number). By default, this option is false"
    );

    private static final FunctionParameterSequenceType FS_PARAM_EXTERNAL_VARIABLE = optManyParam(
            "external-variable", Type.ANY_TYPE, "External variables to be bound for the query that is being " +
                    "evaluated. Should be alternating variable QName and value.");

    private static final FunctionParameterSequenceType FS_PARAM_DEFAULT_SERIALISATION_PARAMS = optParam(
            "default-serialization-params", Type.ITEM, "The default parameters for serialization, these may" +
            "be overridden by any settings within the XQuery Prolog of the $expression.");

    private static final FunctionParameterSequenceType FS_PARAM_STARTING_LOC = optParam(
            "starting-loc", Type.DOUBLE, "the starting location within the results to return the values from"
    );

    private static final FunctionParameterSequenceType FS_PARAM_LENGTH = optParam(
            "length", Type.DOUBLE, "the number of items from $starting-loc to return the values of"
    );

    private static final FunctionReturnSequenceType RETURN_NODE_TYPE = returnsOptMany(
            Type.NODE, "the results of the evaluated XPath/XQuery expression");

    private static final FunctionReturnSequenceType RETURN_ITEM_TYPE = returnsOptMany(
            Type.ITEM, "the results of the evaluated XPath/XQuery expression");

    private static final String FS_EVAL_DESCRIPTION = "Dynamically evaluates an XPath/XQuery expression.";

    private static final String FS_EVAL_NAME = "eval";
    static final FunctionSignature[] FS_EVAL = functionSignatures(
            FS_EVAL_NAME,
            FS_EVAL_DESCRIPTION,
            RETURN_NODE_TYPE,
            arities(
                    arity(FS_PARAM_EXPRESSION),
                    arity(FS_PARAM_EXPRESSION, FS_PARAM_CACHE),
                    arity(FS_PARAM_EXPRESSION, FS_PARAM_CACHE, FS_PARAM_EXTERNAL_VARIABLE),
                    arity(FS_PARAM_EXPRESSION, FS_PARAM_CACHE, FS_PARAM_EXTERNAL_VARIABLE, FS_PARAM_PASS)
            )
    );

    private static final String FS_EVAL_WITH_CONTEXT_NAME = "eval-with-context";
    static final FunctionSignature[] FS_EVAL_WITH_CONTEXT = functionSignatures(
            FS_EVAL_WITH_CONTEXT_NAME,
            FS_EVAL_DESCRIPTION,
            RETURN_NODE_TYPE,
            arities(
                    arity(FS_PARAM_EXPRESSION, FS_PARAM_CONTEXT, FS_PARAM_CACHE),
                    arity(FS_PARAM_EXPRESSION, FS_PARAM_CONTEXT, FS_PARAM_CACHE, FS_PARAM_EVAL_CONTEXT_ITEM),
                    arity(FS_PARAM_EXPRESSION, FS_PARAM_CONTEXT, FS_PARAM_CACHE, FS_PARAM_EVAL_CONTEXT_ITEM, FS_PARAM_PASS)
            )
    );

    private static final String FS_EVAL_INLINE_NAME = "eval-inline";
    static final FunctionSignature[] FS_EVAL_INLINE = functionSignatures(
            FS_EVAL_INLINE_NAME,
            FS_EVAL_DESCRIPTION,
            RETURN_ITEM_TYPE,
            arities(
                    arity(FS_PARAM_INLINE_CONTEXT, FS_PARAM_EXPRESSION),
                    arity(FS_PARAM_INLINE_CONTEXT, FS_PARAM_EXPRESSION, FS_PARAM_CACHE),
                    arity(FS_PARAM_INLINE_CONTEXT, FS_PARAM_EXPRESSION, FS_PARAM_CACHE, FS_PARAM_PASS)
            )
    );

    private static final String FS_EVAL_AND_SERIALIZE_NAME = "eval-and-serialize";
    static final FunctionSignature[] FS_EVAL_AND_SERIALIZE = functionSignatures(
            FS_EVAL_AND_SERIALIZE_NAME,
            "Dynamically evaluates an XPath/XQuery expression and serializes the results",
            RETURN_ITEM_TYPE,
            arities(
                    arity(FS_PARAM_EXPRESSION, FS_PARAM_DEFAULT_SERIALISATION_PARAMS),
                    arity(FS_PARAM_EXPRESSION, FS_PARAM_DEFAULT_SERIALISATION_PARAMS, FS_PARAM_STARTING_LOC),
                    arity(FS_PARAM_EXPRESSION, FS_PARAM_DEFAULT_SERIALISATION_PARAMS, FS_PARAM_STARTING_LOC, FS_PARAM_LENGTH),
                    arity(FS_PARAM_EXPRESSION, FS_PARAM_DEFAULT_SERIALISATION_PARAMS, FS_PARAM_STARTING_LOC, FS_PARAM_LENGTH, FS_PARAM_PASS)
            )
    );

    public Eval(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final boolean isEvalDisabled = ((UtilModule) getParentModule()).isEvalDisabled();
        if (isEvalDisabled) {
            throw new XPathException(this, "util:eval has been disabled by the eXist administrator in conf.xml");
        }

        return doEval(context, contextSequence, args);
    }

    private Sequence doEval(final XQueryContext evalContext, final Sequence contextSequence, final Sequence args[])
            throws XPathException {
        if (evalContext.getProfiler().isEnabled()) {
            evalContext.getProfiler().start(this);
            evalContext.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                evalContext.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
        }

        int argCount = 0;
        Sequence exprContext = null;
        if (isCalledAs(FS_EVAL_INLINE_NAME)) {
            // the current expression context
            exprContext = args[argCount++];
        }

        // get the query expression
        final Item expr = args[argCount++].itemAt(0);
        final Source querySource;
        if (Type.subTypeOf(expr.getType(), Type.ANY_URI)) {
            querySource = loadQueryFromURI(expr);
        } else {
            final String queryStr = expr.getStringValue();
            if (queryStr.trim().isEmpty()) {
                return new EmptySequence();
            }
            querySource = new StringSource(queryStr);
        }

        final NodeValue contextInit;
        if (isCalledAs(FS_EVAL_WITH_CONTEXT_NAME)) {
            // set the context initialization param for later use
            contextInit = (NodeValue) args[argCount++].itemAt(0);
        } else {
            contextInit = null;
        }

        // should the compiled query be cached?
        final boolean cache;
        if (isCalledAs(FS_EVAL_AND_SERIALIZE_NAME)) {
            cache = true;
        } else if (argCount < getArgumentCount()) {
            cache = ((BooleanValue) args[argCount++].itemAt(0)).effectiveBooleanValue();
        } else {
            cache = false;
        }

        // save some context properties
        evalContext.pushNamespaceContext();

        final LocalVariable mark = evalContext.markLocalVariables(false);

        // save the static document set of the current context, so it can be restored later
        final DocumentSet oldDocs = evalContext.getStaticDocs();
        if (exprContext != null) {
            evalContext.setStaticallyKnownDocuments(exprContext.getDocumentSet());
        }

        if (evalContext.isProfilingEnabled(2)) {
            evalContext.getProfiler().start(this, "eval: " + expr);
        }

        // fixme! - hook for debugger here /ljo

        final XQuery xqueryService = evalContext.getBroker().getBrokerPool().getXQueryService();
        final XQueryContext innerContext;
        final Sequence initContextSequence;
        if (contextInit != null) {
            // eval-with-context: initialize a new context
            innerContext = new XQueryContext(context.getBroker().getBrokerPool());
            initContextSequence = initContext(contextInit.getNode(), innerContext);
        } else {
            // use the existing outer context
            // TODO: check if copying the static context would be sufficient???
            innerContext = evalContext.copyContext();
            innerContext.setShared(true);
            //innerContext = context;
            initContextSequence = null;
        }

        //set module load path
        if (Type.subTypeOf(expr.getType(), Type.ANY_URI)) {
            String uri = null;

            if (querySource instanceof DBSource) {
                final XmldbURI documentPath = ((DBSource)querySource).getDocumentPath();
                uri = XmldbURI.EMBEDDED_SERVER_URI.append(documentPath).removeLastSegment().toString();
            } else if (querySource instanceof FileSource) {
                uri = ((FileSource) querySource).getPath().getParent().toString();
            }

            if (uri != null) {
                innerContext.setModuleLoadPath(uri);
            }
        }

        //bind external vars?
        if (isCalledAs(FS_EVAL_NAME) && getArgumentCount() >= 3) {
            final Sequence externalVars = args[argCount++];
            for (int i = 0; i < externalVars.getItemCount(); i++) {
                final Item varName = externalVars.itemAt(i);
                if (varName.getType() == Type.QNAME) {
                    final Item varValue = externalVars.itemAt(++i);
                    innerContext.declareVariable(((QNameValue) varName).getQName(), varValue);
                }
            }
        }

        // determine if original line/column number are passed on
        final boolean pass;
        if (isCalledAs(FS_EVAL_NAME) && getArgumentCount() == 4) {
            pass = args[3].itemAt(0).toJavaObject(Boolean.class);
        } else if (isCalledAs(FS_EVAL_WITH_CONTEXT_NAME) && getArgumentCount() == 5) {
            pass = args[4].itemAt(0).toJavaObject(Boolean.class);
        } else if (isCalledAs(FS_EVAL_INLINE_NAME) && getArgumentCount() == 4) {
            pass = args[3].itemAt(0).toJavaObject(Boolean.class);
        } else if (isCalledAs(FS_EVAL_AND_SERIALIZE_NAME) && getArgumentCount() == 5) {
            pass = args[4].itemAt(0).toJavaObject(Boolean.class);
        } else {
            // default
            pass = false;
        }

        // fixme! - hook for debugger here /ljo
        try {
            if (isCalledAs(FS_EVAL_WITH_CONTEXT_NAME) && getArgumentCount() >= 4) {
                final Item contextItem = args[argCount++].itemAt(0);
                if (contextItem != null) {
                    //TODO : sort this out
                    if (exprContext != null) {
                        LOG.warn("exprContext and contextItem are not null");
                    }
                    exprContext = contextItem.toSequence();
                }
            }


            if (initContextSequence != null) {
                exprContext = initContextSequence;
            }

            Sequence result = null;
            try {
                if (!isCalledAs(FS_EVAL_AND_SERIALIZE_NAME)) {
                    result = execute(evalContext.getBroker(), xqueryService, querySource, innerContext, exprContext,
                            cache, null);

                    return result;

                } else {
                    // get the default serialization options
                    final Properties defaultOutputOptions;
                    if (getArgumentCount() >= 2 && !args[1].isEmpty()) {
                        defaultOutputOptions = FunSerialize.getSerializationProperties(this, args[1].itemAt(0));
                    } else {
                        defaultOutputOptions = new Properties();
                    }

                    // execute the query, XQuery prolog serialization options are collected into `xqueryOutputProperties`
                    final Properties xqueryOutputProperties = new Properties();
                    result = execute(evalContext.getBroker(), xqueryService, querySource, innerContext, exprContext,
                            cache, xqueryOutputProperties);

                    // do we need to subsequence the results?
                    if (getArgumentCount() > 2) {
                        result = FunSubSequence.subsequence(result,
                                ((DoubleValue)getArgument(2).eval(contextSequence, null).convertTo(Type.DOUBLE)),
                                getArgumentCount() == 3 ? null : ((DoubleValue)getArgument(3).eval(contextSequence, null).convertTo(Type.DOUBLE))
                        );
                    }

                    // override the default options with the ones from the xquery prolog
                    final Properties serializationProperties = new Properties();
                    serializationProperties.putAll(defaultOutputOptions);
                    serializationProperties.putAll(xqueryOutputProperties);

                    // serialize the results
                    try(final StringWriter writer = new StringWriter()) {
                        final XQuerySerializer xqSerializer = new XQuerySerializer(
                                context.getBroker(), serializationProperties, writer);

                        final Sequence seq;
                        if (xqSerializer.normalize()) {
                            // TODO(JL): should this not be changed to DEFAULT_ITEM_SEPARATOR
                            seq = FunSerialize.normalize(this, context, result, null);
                        } else {
                            seq = result;
                        }

                        xqSerializer.serialize(seq);

                        return new StringValue(this, writer.toString());

                    } catch (final IOException | SAXException e) {
                        throw new XPathException(this, FnModule.SENR0001, e.getMessage());
                    }
                }
            } finally {
                cleanup(evalContext, innerContext, oldDocs, mark, expr, result);
            }

        } catch (final XPathException e) {
            try {
                e.prependMessage("Error while evaluating expression: " + querySource.getContent() + ". ");
            } catch (final IOException e1) {
            }

            if (!pass) {
                e.setLocation(line, column);
            }

            throw e;
        }
    }

    private void cleanup(final XQueryContext evalContext, final XQueryContext innerContext, final DocumentSet oldDocs,
            final LocalVariable mark, final Item expr, final Sequence resultSequence) {
        if (innerContext != evalContext) {
            evalContext.addImportedContext(innerContext);
        }

        if (oldDocs != null) {
            evalContext.setStaticallyKnownDocuments(oldDocs);
        }

        evalContext.popLocalVariables(mark);
        evalContext.popNamespaceContext();

        if (evalContext.isProfilingEnabled(2)) {
            evalContext.getProfiler().end(this, "eval: " + expr, resultSequence);
        }
    }

    private Sequence execute(final DBBroker broker, final XQuery xqueryService, final Source querySource,
            final XQueryContext innerContext, final Sequence exprContext, final boolean cache,
            @Nullable final Properties outputProperties) throws XPathException {

        CompiledXQuery compiled = null;
        final XQueryPool pool = broker.getBrokerPool().getXQueryPool();

        try {
            compiled = cache ? pool.borrowCompiledXQuery(broker, querySource) : null;
            if (compiled == null) {
                compiled = xqueryService.compile(innerContext, querySource);
            } else {
                compiled.getContext().updateContext(innerContext);
                compiled.getContext().prepareForReuse();
            }

            Sequence sequence = xqueryService.execute(broker, compiled, exprContext, outputProperties, false);
            ValueSequence newSeq = new ValueSequence();
            newSeq.keepUnOrdered(unordered);
            boolean hasSupplements = false;
            for (int i = 0; i < sequence.getItemCount(); i++) {
                //if (sequence.itemAt(i) instanceof StringValue) {
                if (Type.subTypeOf(sequence.itemAt(i).getType(), Type.STRING)) {
                    newSeq.add(new StringValue(this, ((StringValue) sequence.itemAt(i)).getStringValue(true)));
                    hasSupplements = true;
                } else {
                    newSeq.add(sequence.itemAt(i));
                }
            }

            if (hasSupplements) {
                sequence = newSeq;
            }

            return sequence;

        } catch (final IOException | PermissionDeniedException ioe) {
            throw new XPathException(this, ioe);
        } finally {
            if (compiled != null) {
                compiled.getContext().runCleanupTasks();
                if (cache) {
                    pool.returnCompiledXQuery(querySource, compiled);
                } else {
                    compiled.reset();
                }
            }
        }
    }

    /**
     * @param expr
     * @throws XPathException
     * @throws NullPointerException
     * @throws IllegalArgumentException
     */
    private Source loadQueryFromURI(final Item expr) throws XPathException, NullPointerException, IllegalArgumentException {
        final String location = expr.getStringValue();
        Source querySource = null;
        if (location.indexOf(':') < 0 || location.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            try {
                XmldbURI locationUri = XmldbURI.xmldbUriFor(location);

                // If location is relative (does not contain any / and does
                // not start with . or .. then the path of the module need to
                // be added.
                if (location.indexOf('/') < 0 || location.startsWith(".")) {
                    final XmldbURI moduleLoadPathUri = XmldbURI.xmldbUriFor(context.getModuleLoadPath());
                    locationUri = moduleLoadPathUri.resolveCollectionPath(locationUri);
                }

                try(final LockedDocument lockedSourceDoc = context.getBroker().getXMLResource(locationUri.toCollectionPathURI(), LockMode.READ_LOCK)) {
                    final DocumentImpl sourceDoc = lockedSourceDoc == null ? null : lockedSourceDoc.getDocument();
                    if (sourceDoc == null) {
                        throw new XPathException(this, "source for module " + location + " not found in database");
                    }
                    if (sourceDoc.getResourceType() != DocumentImpl.BINARY_FILE ||
                            !"application/xquery".equals(sourceDoc.getMetadata().getMimeType())) {
                        throw new XPathException(this, "source for module " + location + " is not an XQuery or " +
                        "declares a wrong mime-type");
                    }
                    querySource = new DBSource(context.getBroker().getBrokerPool(), (BinaryDocument) sourceDoc, true);
                } catch (final PermissionDeniedException e) {
                    throw new XPathException(this, "permission denied to read module source from " + location);
                }
            } catch (final URISyntaxException e) {
                throw new XPathException(this, e);
            }
        } else {
            // No. Load from file or URL
            try {
                //TODO: use URIs to ensure proper resolution of relative locations
                querySource = SourceFactory.getSource(context.getBroker(), context.getModuleLoadPath(), location, true);
                if (querySource == null) {
                    throw new XPathException(this, "source for query at " + location + " not found");
                }
            } catch (final MalformedURLException e) {
                throw new XPathException(this, "source location for query at " + location + " should be a valid URL: " +
                        e.getMessage());
            } catch (final IOException e) {
                throw new XPathException(this, "source for query at " + location + " not found: " +
                        e.getMessage());
            } catch (final PermissionDeniedException e) {
                throw new XPathException(this, "Permission denied to access query at " + location + " : " +
                        e.getMessage());
            }
        }
        return querySource;
    }

    /**
     * Read to optional static-context fragment to initialize
     * the context.
     *
     * @param root
     * @param innerContext
     * @throws XPathException
     */
    private Sequence initContext(final Node root, final XQueryContext innerContext) throws XPathException {
        final NodeList cl = root.getChildNodes();
        Sequence result = null;
        for (int i = 0; i < cl.getLength(); i++) {
            final Node child = cl.item(i);
            //TODO : more check on attributes existence and on their values
            if (child.getNodeType() == Node.ELEMENT_NODE && "variable".equals(child.getLocalName())) {
                final Element elem = (Element) child;
                final String qname = elem.getAttribute("name");
                final String source = elem.getAttribute("source");
                NodeValue value;
                if (!source.isEmpty()) {
                    // load variable contents from URI
                    value = loadVarFromURI(source);
                } else {
                    value = (NodeValue) elem.getFirstChild();
                    if (value instanceof ReferenceNode) {
                        value = ((ReferenceNode) value).getReference();
                    }
                }
                final String type = elem.getAttribute("type");
                if (!type.isEmpty() && Type.subTypeOf(Type.getType(type), Type.ANY_ATOMIC_TYPE)) {
                    innerContext.declareVariable(qname, value.atomize().convertTo(Type.getType(type)));
                } else {
                    innerContext.declareVariable(qname, value);
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE && "output-size-limit".equals(child.getLocalName())) {
                final Element elem = (Element) child;
                //TODO : error check
                innerContext.getWatchDog().setMaxNodes(Integer.parseInt(elem.getAttribute("value")));
            } else if (child.getNodeType() == Node.ELEMENT_NODE && "timeout".equals(child.getLocalName())) {
                final Element elem = (Element) child;
                //TODO : error check
                innerContext.getWatchDog().setTimeout(Long.parseLong(elem.getAttribute("value")));
            } else if (child.getNodeType() == Node.ELEMENT_NODE && "current-dateTime".equals(child.getLocalName())) {
                final Element elem = (Element) child;
                //TODO : error check
                final DateTimeValue dtv = new DateTimeValue(this, elem.getAttribute("value"));
                innerContext.setCalendar(dtv.calendar);
            } else if (child.getNodeType() == Node.ELEMENT_NODE && "implicit-timezone".equals(child.getLocalName())) {
                final Element elem = (Element) child;
                //TODO : error check
                final Duration duration = TimeUtils.getInstance().newDuration(elem.getAttribute("value"));
                innerContext.setTimeZone(new SimpleTimeZone((int) duration.getTimeInMillis(new Date()), "XQuery context"));
            } else if (child.getNodeType() == Node.ELEMENT_NODE && "unbind-namespace".equals(child.getLocalName())) {
                final Element elem = (Element) child;
                //TODO : error check
                if (elem.hasAttribute("uri")) {
                    innerContext.removeNamespace(elem.getAttribute("uri"));
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE && "staticallyKnownDocuments".equals(child.getLocalName())) {
                final Element elem = (Element) child;
                //TODO : iterate over the children
                NodeValue value = (NodeValue) elem.getFirstChild();
                if (value instanceof ReferenceNode) {
                    value = ((ReferenceNode) value).getReference();
                }
                final XmldbURI[] pathes = new XmldbURI[1];
                //TODO : aggregate !
                //TODO : cleanly seperate the statically know docollection and documents
                pathes[0] = XmldbURI.create(value.getStringValue());
                innerContext.setStaticallyKnownDocuments(pathes);
            } /*else if (child.getNodeType() == Node.ELEMENT_NODE &&	"mapModule".equals(child.getLocalPart())) {
				Element elem = (Element) child;
				//TODO : error check
				if (elem.getAttribute("namespace") != null && elem.getAttribute("uri") != null) {
					innerContext.mapModule(elem.getAttribute("namespace"),
							XmldbURI.create(elem.getAttribute("uri")));
				}
			} */ else if (child.getNodeType() == Node.ELEMENT_NODE && "default-context".equals(child.getLocalName())) {
                final Element elem = (Element) child;
                final NodeValue nodevalue = (NodeValue) elem;
                result = nodevalue.toSequence();
            }
        }

        return result;
    }

    private NodeImpl loadVarFromURI(final String uri) throws XPathException {
        XMLReader xr = null;
        final XMLReaderPool parserPool = context.getBroker().getBrokerPool().getParserPool();
        try {
            final URL url = new URL(uri);
            final InputStreamReader isr = new InputStreamReader(url.openStream(), UTF_8);
            final InputSource src = new InputSource(isr);

            xr = parserPool.borrowXMLReader();
            final SAXAdapter adapter = new SAXAdapter(this, context);
            xr.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            xr.parse(src);
            isr.close();
            return adapter.getDocument();
        } catch (final SAXException | IOException e) {
            throw new XPathException(this, e);
        } finally {
            if (xr != null) {
                parserPool.returnXMLReader(xr);
            }
        }
    }
}
