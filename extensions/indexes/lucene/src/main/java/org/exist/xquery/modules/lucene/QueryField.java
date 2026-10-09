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
package org.exist.xquery.modules.lucene;

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;

import javax.annotation.Nullable;
import java.io.IOException;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.modules.lucene.LuceneModule.functionSignatures;

public class QueryField extends Query implements Optimizable {

    private static final FunctionParameterSequenceType FS_PARAM_FIELD = optManyParam("field", Type.STRING, "The lucene field name.");
    private static final FunctionParameterSequenceType FS_PARAM_QUERY = param("query", Type.ITEM, "The query to search for, provided either as a string or text in Lucene's default query syntax or as an XML fragment to bypass Lucene's default query parser");

    final static FunctionSignature[] signatures = functionSignatures(
            "query-field",
            "Queries a Lucene field, which has to be explicitely created in the index configuration.",
            returnsOptMany(Type.NODE, """
                    all nodes from the input node set matching the query. match highlighting information
                    will be available for all returned nodes. Lucene's match score can be retrieved via
                    the ft:score function."""),
            arities(
                    arity(
                            FS_PARAM_FIELD,
                            FS_PARAM_QUERY
                    ),
                    arity(
                            FS_PARAM_FIELD,
                            FS_PARAM_QUERY,
                            optParam("options", Type.ITEM, """
                                    An XML fragment or XDM Map containing options to be passed to Lucene's query parser. The following
                                    options are supported (a description can be found in the docs):
                                    <options>
                                       <default-operator>and|or</default-operator>
                                       <phrase-slop>number</phrase-slop>
                                       <leading-wildcard>yes|no</leading-wildcard>
                                       <filter-rewrite>yes|no</filter-rewrite>
                                    </options>"""
                            )
                    )
            )
    );

    private NodeSet preselectResult = null;

    public QueryField(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(new AnalyzeContextInfo(contextInfo));
        this.contextId = contextInfo.getContextId();
    }

    @Override
    public Sequence canOptimizeSequence(final Sequence contextSequence) {
        return contextSequence;  // always optimizable!
    }

    @Override
    public int getOptimizeAxis() {
        return Constants.DESCENDANT_SELF_AXIS;
    }

    @Override
    public NodeSet preSelect(final Sequence contextSequence, final boolean useContext) throws XPathException {
        final long start = System.currentTimeMillis();
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;

        final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        final String field = getArgument(0).eval(contextSequence, null).getStringValue();
        final DocumentSet docs = contextSequence.getDocumentSet();
        final Item query = getKey(contextSequence, null);
        final QueryOptions options = parseOptions(this, contextSequence, null, 3);
        try {
            if (Type.subTypeOf(query.getType(), Type.ELEMENT)) {
                preselectResult = index.queryField(getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null, field, (Element) ((NodeValue) query).getNode(), NodeSet.DESCENDANT, options);
            } else {
                preselectResult = index.queryField(context, getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null, field, query.getStringValue(), NodeSet.DESCENDANT, options);
            }
        } catch (final IOException e) {
            throw new XPathException(this, "Error while querying full text index: " + e.getMessage(), e);
        }
        LOG.debug("Lucene query took {}", System.currentTimeMillis() - start);
        if( context.getProfiler().traceFunctions() ) {
            context.getProfiler().traceIndexUsage( context, "lucene", this, PerformanceStats.IndexOptimizationLevel.OPTIMIZED, System.currentTimeMillis() - start );
        }
        return preselectResult;
    }

    @Override
    public Sequence eval(Sequence contextSequence, @Nullable final Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        final NodeSet result;
        if (preselectResult == null) {
        	final long start = System.currentTimeMillis();
        	final String field = getArgument(0).eval(contextSequence, null).getStringValue();
        	final Item query = getKey(contextSequence, null);
        	
        	final DocumentSet docs;
        	if (contextSequence == null) {
                docs = context.getStaticallyKnownDocuments();
            } else {
                docs = contextSequence.getDocumentSet();
            }

        	final NodeSet contextSet;
        	if (contextSequence != null) {
                contextSet = contextSequence.toNodeSet();
            } else {
                contextSet = null;
            }
        	
        	final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        	final QueryOptions options = parseOptions(this, contextSequence, contextItem, 3);
        	try {
        		if (Type.subTypeOf(query.getType(), Type.ELEMENT)) {
                    result = index.queryField(getExpressionId(), docs, contextSet, field, (Element) ((NodeValue) query).getNode(), NodeSet.ANCESTOR, options);
                } else {
                    result = index.queryField(context, getExpressionId(), docs, contextSet, field, query.getStringValue(), NodeSet.ANCESTOR, options);
                }
            } catch (final IOException e) {
        		throw new XPathException(this, e.getMessage());
        	}
        	if( context.getProfiler().traceFunctions() ) {
        		context.getProfiler().traceIndexUsage( context, "lucene", this, PerformanceStats.IndexOptimizationLevel.BASIC, System.currentTimeMillis() - start );
        	}
        } else {
            result = preselectResult.selectAncestorDescendant(contextSequence.toNodeSet(), NodeSet.DESCENDANT, true, getContextId(), true);
        }

        return result;
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_SET;
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization) {
            preselectResult = null;
        }
    }
}

