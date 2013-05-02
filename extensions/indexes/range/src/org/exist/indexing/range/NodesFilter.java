package org.exist.indexing.range;

import org.apache.lucene.index.*;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FixedBitSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.NodeSetIterator;
import org.exist.indexing.lucene.LuceneUtil;
import org.exist.numbering.NodeId;
import org.exist.util.ByteConversion;

import java.io.IOException;

public class NodesFilter extends Filter {

    private final NodeSet contextSet;

    public NodesFilter(NodeSet contextSet) {
        this.contextSet = contextSet;
    }

    @Override
    public DocIdSet getDocIdSet(AtomicReaderContext context, Bits acceptDocs) throws IOException {
        final AtomicReader reader = context.reader();

        final FixedBitSet result = new FixedBitSet(reader.maxDoc());
        final Fields fields = reader.fields();
        Terms terms = fields.terms(RangeIndexWorker.FIELD_ID);
        int count = 0;
        if (terms != null) {
            TermsEnum termsEnum = terms.iterator(null);
            DocsEnum docs = null;
            for (NodeSetIterator i = contextSet.iterator(); i.hasNext(); ) {
                NodeProxy node = i.next();
                BytesRef lowerBound = new BytesRef(LuceneUtil.createId(node.getDoc().getDocId(), node.getNodeId()));
                BytesRef upperBound = new BytesRef(LuceneUtil.createId(node.getDoc().getDocId(), node.getNodeId().nextSibling()));

                if (termsEnum.seekCeil(lowerBound, false) != TermsEnum.SeekStatus.END) {
                    do {
                        BytesRef nextId = termsEnum.term();
                        if (nextId.compareTo(upperBound) < 0) {
                            docs = termsEnum.docs(null, docs, DocsEnum.FLAG_NONE);
                            while (docs.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                                int id = docs.docID();
                                result.set(id);
                                count++;
                            }
                        } else {
                            break;
                        }
                    } while (termsEnum.next() != null);
                }
            }
        }
//        if (count > 0)
//            System.out.println("Found " + count + " docs for mode " + contextSet.get(0).getDoc().getDocId() + "/" + contextSet.get(0).getNodeId() + " with reader " + reader);
        return result;
    }

    private static boolean startsWith(byte[] key, int offset, int length, byte[] prefix) {
        if (length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (key[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
