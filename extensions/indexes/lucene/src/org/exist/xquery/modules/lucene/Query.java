package org.exist.xquery.modules.lucene;

import org.apache.lucene.queryParser.ParseException;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Atomize;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.DynamicCardinalityCheck;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.io.IOException;
import java.util.List;

public class Query extends Function {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("query", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "",
            new SequenceType[] {
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
        );

    public Query(XQueryContext context) {
        super(context, signature);
    }

    public void setArguments(List arguments) throws XPathException {
        Expression path = (Expression) arguments.get(0);
        steps.add(path);

        Expression arg = (Expression) arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, arg,
                new org.exist.xquery.util.Error(org.exist.xquery.util.Error.FUNC_PARAM_CARDINALITY, "2", mySignature));
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            arg = new Atomize(context, arg);
        steps.add(arg);
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.PathExpr#analyze(org.exist.xquery.Expression)
    */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(new AnalyzeContextInfo(contextInfo));
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
            contextSequence = contextItem.toSequence();

        NodeSet result;
        Sequence input = getArgument(0).eval(contextSequence);
        if (input.isEmpty())
            result = NodeSet.EMPTY_SET;
        else {
            NodeSet inNodes = input.toNodeSet();
            DocumentSet docs = inNodes.getDocumentSet();
            LuceneIndexWorker index = (LuceneIndexWorker)
                    context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            String key = getArgument(1).eval(contextSequence, contextItem).getStringValue();
            try {
                result = index.query(getExpressionId(), docs, inNodes, key, NodeSet.ANCESTOR);
            } catch (IOException e) {
                throw new XPathException(getASTNode(), e.getMessage());
            } catch (ParseException e) {
                throw new XPathException(getASTNode(), e.getMessage());
            }
        }
        return result;
    }

    public int getDependencies() {
        final Expression stringArg = getArgument(0);
        if (Type.subTypeOf(stringArg.returnsType(), Type.NODE) &&
            !Dependency.dependsOn(stringArg, Dependency.CONTEXT_ITEM)) {
            return Dependency.CONTEXT_SET;
        } else {
            return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
        }
    }

    public int returnsType() {
        return Type.NODE;
    }
}
