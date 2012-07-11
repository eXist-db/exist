package org.exist.xquery.modules.cache;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class ListFunction extends CacheBasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
                new QName("list", CacheModule.NAMESPACE_URI, CacheModule.PREFIX),
                "List all keys stored in the global cache",
                new SequenceType[] {
                    new FunctionParameterSequenceType("cache-identity", Type.ITEM, Cardinality.ONE, "Either the Java cache object or the name of the cache")
                },
                new FunctionParameterSequenceType("keys", Type.STRING, Cardinality.ZERO_OR_MORE, "the sequence of keys")
        );

    public ListFunction(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String cacheId = args[0].getStringValue();
        return Cache.keys(cacheId);
    }
}
