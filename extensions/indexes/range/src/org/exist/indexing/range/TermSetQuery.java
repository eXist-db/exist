package org.exist.indexing.range;

import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;

import java.io.IOException;

public class TermSetQuery extends MultiTermQuery {

    private BytesRef bytes[];

    public TermSetQuery(String field, Sequence keys) throws XPathException {
        super(field);
        bytes = new BytesRef[keys.getItemCount()];
        int j = 0;
        for (SequenceIterator i = keys.iterate(); i.hasNext(); j++) {
            Item next = i.nextItem();
            bytes[j] = RangeIndexConfigElement.convertToBytes(next.atomize());
        }
    }

    @Override
    protected TermsEnum getTermsEnum(Terms terms, AttributeSource attributeSource) throws IOException {

        return null;
    }

    @Override
    public String toString(String s) {
        return null;
    }
}
