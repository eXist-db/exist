package org.exist.xquery.modules.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.queryparser.classic.ParseException;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.Properties;

public class QueryField extends Query implements Optimizable {
	
	protected static final Logger logger = Logger.getLogger(Query.class);

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("query-field", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Queries a Lucene field, which has to be explicitely created in the index configuration.",
            new SequenceType[] {
                new FunctionParameterSequenceType("field", Type.STRING, Cardinality.ZERO_OR_MORE, 
                		"The lucene field name."),
                new FunctionParameterSequenceType("query", Type.ITEM, Cardinality.EXACTLY_ONE, 
                		"The query to search for, provided either as a string or text in Lucene's default query " +
                		"syntax or as an XML fragment to bypass Lucene's default query parser")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "all nodes from the input node set matching the query. match highlighting information " +
                "will be available for all returned nodes. Lucene's match score can be retrieved via " +
                "the ft:score function.")
        ),
        new FunctionSignature(
            new QName("query-field", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Queries a Lucene field, which has to be explicitely created in the index configuration.",
            new SequenceType[] {
                new FunctionParameterSequenceType("field", Type.STRING, Cardinality.ZERO_OR_MORE,
                		"The lucene field name."),
                new FunctionParameterSequenceType("query", Type.ITEM, Cardinality.EXACTLY_ONE,
                		"The query to search for, provided either as a string or text in Lucene's default query " +
                		"syntax or as an XML fragment to bypass Lucene's default query parser"),
                new FunctionParameterSequenceType("options", Type.NODE, Cardinality.ZERO_OR_ONE,
                		"An XML fragment containing options to be passed to Lucene's query parser. The following " +
                        "options are supported (a description can be found in the docs):\n" +
                        "<options>\n" +
                        "   <default-operator>and|or</default-operator>\n" +
                        "   <phrase-slop>number</phrase-slop>\n" +
                        "   <leading-wildcard>yes|no</leading-wildcard>\n" +
                        "   <filter-rewrite>yes|no</filter-rewrite>\n" +
                        "</options>")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "all nodes from the input node set matching the query. match highlighting information " +
                "will be available for all returned nodes. Lucene's match score can be retrieved via " +
                "the ft:score function.")
        )
    };

    private NodeSet preselectResult = null;

    public QueryField(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.PathExpr#analyze(org.exist.xquery.Expression)
    */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    }

    public boolean canOptimize(Sequence contextSequence) {
    	return true;
    }

    public int getOptimizeAxis() {
        return Constants.DESCENDANT_SELF_AXIS;
    }

    public NodeSet preSelect(Sequence contextSequence, boolean useContext) throws XPathException {
        long start = System.currentTimeMillis();
        // the expression can be called multiple times, so we need to clear the previous preselectResult
        preselectResult = null;
        LuceneIndexWorker index = (LuceneIndexWorker)
                context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        String field = getArgument(0).eval(contextSequence).getStringValue();
        DocumentSet docs = contextSequence.getDocumentSet();
        Item query = getKey(contextSequence, null);
        Properties options = parseOptions(contextSequence, null);
        try {
            if (Type.subTypeOf(query.getType(), Type.ELEMENT))
                preselectResult = index.queryField(context, getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null,
                    field, (Element) ((NodeValue)query).getNode(), NodeSet.DESCENDANT, options);
            else
                preselectResult = index.queryField(context, getExpressionId(), docs, useContext ? contextSequence.toNodeSet() : null,
                    field, query.getStringValue(), NodeSet.DESCENDANT, options);
        } catch (IOException | ParseException e) {
            throw new XPathException(this, "Error while querying full text index: " + e.getMessage(), e);
        }
        LOG.debug("Lucene query took " + (System.currentTimeMillis() - start));
        if( context.getProfiler().traceFunctions() ) {
            context.getProfiler().traceIndexUsage( context, "lucene", this, PerformanceStats.OPTIMIZED_INDEX, System.currentTimeMillis() - start );
        }
        return preselectResult;
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	
        if (contextItem != null)
            contextSequence = contextItem.toSequence();

        NodeSet result;
        if (preselectResult == null) {
        	long start = System.currentTimeMillis();
        	String field = getArgument(0).eval(contextSequence).getStringValue();
        	
        	Item query = getKey(contextSequence, null);
        	
        	DocumentSet docs = null;
        	if (contextSequence == null)
        		docs = context.getStaticallyKnownDocuments();
        	else
                docs = contextSequence.getDocumentSet();

        	NodeSet contextSet = null;
        	if (contextSequence != null)
        		contextSet = contextSequence.toNodeSet();
        	
        	LuceneIndexWorker index = (LuceneIndexWorker)
        		context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
        	Properties options = parseOptions(contextSequence, contextItem);
        	try {
        		if (Type.subTypeOf(query.getType(), Type.ELEMENT))
        			result = index.queryField(context, getExpressionId(), docs, contextSet, field,
        					(Element)((NodeValue)query).getNode(), NodeSet.ANCESTOR, options);
        		else
        			result = index.queryField(context, getExpressionId(), docs, contextSet, field,
        					query.getStringValue(), NodeSet.ANCESTOR, options);
            } catch (IOException | ParseException e) {
        		throw new XPathException(this, e.getMessage());
        	}
        	if( context.getProfiler().traceFunctions() ) {
        		context.getProfiler().traceIndexUsage( context, "lucene", this, PerformanceStats.BASIC_INDEX, System.currentTimeMillis() - start );
        	}
        } else {
            result = preselectResult;
        }
        return result;
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization) {
            preselectResult = null;
        }
    }
}

