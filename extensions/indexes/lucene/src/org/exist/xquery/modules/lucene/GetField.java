package org.exist.xquery.modules.lucene;

import java.io.IOException;

import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class GetField extends BasicFunction {

	public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("get-field", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Retrieve the stored content of a field.",
            new SequenceType[]{
                new FunctionParameterSequenceType("path", Type.STRING, Cardinality.ZERO_OR_MORE,
                "URI paths of documents or collections in database. Collection URIs should end on a '/'."),
                new FunctionParameterSequenceType("field", Type.STRING, Cardinality.EXACTLY_ONE,
                "query string")
            },
	        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE,
	    		"All documents that are match by the query"))
    };
	
	public GetField(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		XmldbURI uri = XmldbURI.createInternal(args[0].getStringValue());
		String field = args[1].getStringValue();
		
		DocumentImpl doc = null;
		try {
			doc = context.getBroker().getXMLResource(uri, Lock.READ_LOCK);
			if (doc == null) {
                return Sequence.EMPTY_SEQUENCE;
            }
			// Get the lucene worker
            LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            String content = index.getFieldContent(doc.getDocId(), field);
            return content == null ? Sequence.EMPTY_SEQUENCE : new org.exist.xquery.value.StringValue(content);
		} catch (PermissionDeniedException e) {
			throw new XPathException(this, LuceneModule.EXXQDYFT0001, "Permission denied to read document " + args[0].getStringValue());
		} catch (IOException e) {
			throw new XPathException(this, LuceneModule.EXXQDYFT0002, "IO error while reading document " + args[0].getStringValue());
		} finally {
			if (doc != null)
				doc.getUpdateLock().release(Lock.READ_LOCK);
		}
	}

}
