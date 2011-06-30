package org.exist.xquery.modules.lucene;

import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.indexing.StreamListener;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class RemoveIndex extends BasicFunction {

	public final static FunctionSignature signature =
            new FunctionSignature(
            new QName("remove-index", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Remove any (non-XML) Lucene index associated with the document identified by the " +
            "path parameter. This function will only remove indexes which were manually created by " +
            "the user via the ft:index function. Indexes defined in collection.xconf will NOT be " +
            "removed. They are maintained automatically by the database. Please note that non-XML indexes " +
            "will also be removed automatically if the associated document is deleted.",
            new SequenceType[]{
                new FunctionParameterSequenceType("documentPath", Type.STRING, Cardinality.ONE,
                "URI path of document in database.")
            },
            new FunctionReturnSequenceType(Type.EMPTY, Cardinality.ZERO, ""));
	
	public RemoveIndex(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		DocumentImpl doc = null;
        try {
            // Get first parameter, this is the document
            String path = args[0].itemAt(0).getStringValue();

            // Retrieve document from database
            doc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), Lock.READ_LOCK);

            // Verify the document actually exists
            if (doc == null) {
                throw new XPathException("Document " + path + " does not exist.");
            }

            // Retrieve Lucene
            LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker()
                    .getIndexController().getWorkerByIndexId(LuceneIndex.ID);

            // Note: code order is important here,
            index.setDocument(doc, StreamListener.REMOVE_BINARY);

            index.flush();

        } catch (Exception ex) { // PermissionDeniedException
            throw new XPathException(ex);

        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }

        // Return nothing [status would be nice]
        return Sequence.EMPTY_SEQUENCE;
	}

}
