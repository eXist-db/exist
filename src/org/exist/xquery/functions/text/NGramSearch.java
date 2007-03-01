package org.exist.xquery.functions.text;

import org.exist.xquery.*;
import org.exist.xquery.util.*;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Item;
import org.exist.dom.QName;
import org.exist.dom.NodeSet;
import org.exist.dom.DocumentSet;
import org.exist.indexing.impl.NGramIndex;
import org.exist.indexing.impl.NGramIndexWorker;
import org.exist.indexing.IndexWorker;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: 28-Feb-2007
 * Time: 15:18:59
 * To change this template use File | Settings | File Templates.
 */
public class NGramSearch extends Function {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("ngram-contains", TextModule.NAMESPACE_URI, TextModule.PREFIX),
                    "",
                    new SequenceType[] {
                            new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
                    },
                    new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            );

    public NGramSearch(XQueryContext context) {
        super(context, signature);
    }


    public void setArguments(List arguments) throws XPathException {
        Expression path = (Expression) arguments.get(0);
        steps.add(path);

        Expression arg = (Expression) arguments.get(1);
        arg = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, arg,
                new org.exist.xquery.util.Error(Error.FUNC_PARAM_CARDINALITY, "2", mySignature));
        if(!Type.subTypeOf(arg.returnsType(), Type.ATOMIC))
            arg = new Atomize(context, arg);
        steps.add(arg);
    }


    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
			contextSequence = contextItem.toSequence();
        Sequence input = getArgument(0).eval(contextSequence, contextItem);
        Sequence result;
        if (input.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
            NodeSet nodes = input.toNodeSet();
            DocumentSet docs = nodes.getDocumentSet();
            String key = getArgument(1).eval(contextSequence, contextItem).getStringValue();
            NGramIndexWorker index = (NGramIndexWorker)
                    context.getBroker().getIndexDispatcher().getIndexWorker(NGramIndex.ID);
            result = index.search(docs, key, context, nodes, NodeSet.ANCESTOR);
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