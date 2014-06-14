package org.exist.xquery.modules.lucene;

import java.io.IOException;
import java.net.URISyntaxException;

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
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class InspectIndex extends BasicFunction {

	public final static FunctionSignature[] signatures = {
        new FunctionSignature(
                new QName("has-index", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                "Check if the given document has a lucene index defined on it. This method " +
                "will return true for both, indexes created via collection.xconf or manual index " +
                "fields added to the document with ft:index.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE, 
                    		"Full path to the resource to check")
                },
                new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_MORE, "")
            )
	};
	
	public InspectIndex(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		String path = args[0].itemAt(0).getStringValue();
		
        try {
			// Retrieve document from database
			DocumentImpl doc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), Lock.READ_LOCK);

			// Verify the document actually exists
			if (doc == null) {
			    throw new XPathException("Document " + path + " does not exist.");
			}
			
			LuceneIndexWorker index = (LuceneIndexWorker)
				context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
			return new BooleanValue(index.hasIndex(doc.getDocId()));
		} catch (PermissionDeniedException e) {
			throw new XPathException(this, LuceneModule.EXXQDYFT0001, e.getMessage());
		} catch (URISyntaxException e) {
			throw new XPathException(this, LuceneModule.EXXQDYFT0003, e.getMessage());
		} catch (IOException e) {
			throw new XPathException(this, LuceneModule.EXXQDYFT0002, e.getMessage());
		}
	}

}
