package org.exist.security.xacml;

import com.sun.xacml.AbstractPolicy;
import com.sun.xacml.EvaluationCtx;
import com.sun.xacml.MatchResult;
import com.sun.xacml.ParsingException;
import com.sun.xacml.Policy;
import com.sun.xacml.PolicyReference;
import com.sun.xacml.PolicySet;
import com.sun.xacml.ProcessingException;
import com.sun.xacml.ctx.Status;
import com.sun.xacml.finder.PolicyFinder;
import com.sun.xacml.finder.PolicyFinderModule;
import com.sun.xacml.finder.PolicyFinderResult;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeValueIndexByQName;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/*
*Added new constructor to AnyURIValue to accept a URI
*/
/**
* This class finds Policy and PolicySet documents located in
* the /db/system/policies collection.  It implements both of the
* <code>findPolicy</code> methods of <code>PolicyFinderModule</code>.
* Finding policies by reference uses a range index on PolicySetId
* and PolicyId, so that must be set up for references to work.
* Finding policies for a given request is not yet optimized, but
* just loads all policies in the policies collection, parses them,
* and determines if they match the request.
*
* @see XACMLConstants
*/
public class ExistPolicyModule extends PolicyFinderModule
{
	private static final Logger LOG = Logger.getLogger(ExistPolicyModule.class);
	
	private static final Map POLICY_CACHE = Collections.synchronizedMap(new HashMap(8));

	private PolicyFinder finder;
	private BrokerPool pool;

	private ExistPolicyModule() {}
	/**
	* Creates a new <code>ExistPolicyModule</code>.  Retains a reference
	* to the specified <code>BrokerPool</code>.
	*
	* @param pool The <code>BrokerPool</code> that will be used to
	*	access the database to find policies.
	*/
	public ExistPolicyModule(BrokerPool pool)
	{
		this.pool = pool;
	}

	public boolean isRequestSupported()
	{
		return true;
	}
	public boolean isIdReferenceSupported()
	{
		return true;
	}
	public void init(PolicyFinder finder)
	{
		this.finder = finder;
	}

	public PolicyFinderResult findPolicy(EvaluationCtx context)
	{
		DBBroker broker = null;
		try
		{
			broker = pool.get();
			return findPolicy(broker, context);
		}
		catch(EXistException ee)
		{
			return errorResult("Error while finding policy: " + ee.getMessage(), ee);
		}
		finally
		{
			pool.release(broker);
		}
	}
	private PolicyFinderResult findPolicy(DBBroker broker, EvaluationCtx context)
	{
		DocumentSet mainPolicyDocs = getPolicyDocuments(broker, false);
		if(mainPolicyDocs == null)
			return new PolicyFinderResult();

		AbstractPolicy matchedPolicy = null;
		AbstractPolicy policy;
		MatchResult match;
		int result;
		try
		{
			for(Iterator it = mainPolicyDocs.iterator(); it.hasNext();)
			{
				policy = getPolicyDocument((DocumentImpl)it.next());
				match = policy.match(context);
				result = match.getResult();
				if(result == MatchResult.INDETERMINATE)
					return new PolicyFinderResult(match.getStatus());
				else if(result == MatchResult.MATCH)
				{
					if(matchedPolicy == null)
						matchedPolicy = policy;
					else
						return errorResult("Matched multiple policies for reqest", null);
				}
			}
		}
		catch(ParsingException pe)
		{
			return errorResult("Error retrieving policies: " + pe.getMessage(), pe);
		}

		if(matchedPolicy == null)
			return new PolicyFinderResult();
		else
			return new PolicyFinderResult(matchedPolicy);
	}
	public PolicyFinderResult findPolicy(URI idReference, int type)
	{
		QName idAttributeQName = getIdAttributeQName(type);
		if(idAttributeQName == null)
			return errorResult("Invalid reference type: " + type, null);

		DBBroker broker = null;
		try
		{
			broker = pool.get();
			DocumentImpl policyDoc = getPolicyDocument(broker, idAttributeQName, idReference);
			if(policyDoc == null)
				return new PolicyFinderResult();

			AbstractPolicy policy = getPolicyDocument(policyDoc);
			if(policy == null)
				return new PolicyFinderResult();

			return new PolicyFinderResult(policy);
		}
		catch(Exception e)
		{
			return errorResult("Error resolving " +  idAttributeQName.getLocalName() + " '" + idReference.toString() + "': " + e.getMessage(), e);
		}
		finally
		{
			pool.release(broker);
		}
	}
	//gets documents in the policies collection
	private DocumentSet getPolicyDocuments(DBBroker broker, boolean recursive)
	{
		Collection policyCollection = broker.getCollection(XACMLConstants.POLICY_COLLECTION);
		if(policyCollection == null)
		{
			LOG.warn("Policy collection '" + XACMLConstants.POLICY_COLLECTION + "' does not exist");
			return null;
		}

		int documentCount = policyCollection.getDocumentCount();
		if(documentCount == 0)
		{
			LOG.warn("Policy collection contains no documents.");
			return null;
		}
		DocumentSet documentSet = new DocumentSet(documentCount);
		return policyCollection.allDocs(broker, documentSet, recursive, false);
	}
	//resolves a reference to a policy document
	private DocumentImpl getPolicyDocument(DBBroker broker, QName idAttributeQName, URI idReference) throws ProcessingException, XPathException
	{
		if(idReference == null)
			return null;
		AtomicValue comparison = new AnyURIValue(idReference);

		DocumentSet documentSet = getPolicyDocuments(broker, true);
		NodeSet nodeSet = documentSet.toNodeSet();

		NativeValueIndexByQName index = broker.getQNameValueIndex();
		Sequence results = index.findByQName(idAttributeQName, comparison, nodeSet);

		documentSet = (results == null) ? null : results.getDocumentSet();
		int documentCount = (documentSet == null) ? 0 : documentSet.getLength();
		if(documentCount == 0)
		{
			LOG.warn("Could not find " + idAttributeQName.getLocalName() + " '" +  idReference + "'", null);
			return null;
		}

		if(documentCount > 1)
		{
			throw new ProcessingException("Too many applicable policies for " + idAttributeQName.getLocalName() + " '" +  idReference + "'");
		}

		return (DocumentImpl)documentSet.iterator().next();
	}
	private QName getIdAttributeQName(int type)
	{
		if(type == PolicyReference.POLICY_REFERENCE)
			return new QName(XACMLConstants.POLICY_ID_LOCAL_NAME, XACMLConstants.XACML_POLICY_NAMESPACE);
		else if(type == PolicyReference.POLICYSET_REFERENCE)
			return new QName(XACMLConstants.POLICY_SET_ID_LOCAL_NAME, XACMLConstants.XACML_POLICY_NAMESPACE);
		else
			return null;
	}
	//logs the specified message and exception
	//then, returns a result with status Indeterminate and the given message
	private static PolicyFinderResult errorResult(String message, Throwable t)
	{
		LOG.warn(message, t);
		return new PolicyFinderResult(new Status(Collections.singletonList(Status.STATUS_PROCESSING_ERROR), message));
	}

	//if the document has already been parsed, returns the cached AbstractPolicy
	//otherwise, parses and caches the document
	private AbstractPolicy getPolicyDocument(DocumentImpl policyDoc) throws ParsingException
	{
		String uri = policyDoc.getName();
		AbstractPolicy policy = (AbstractPolicy)POLICY_CACHE.get(uri);
		if(policy == null)
		{
			policy = parsePolicyDocument(policyDoc);
			POLICY_CACHE.put(uri, policy);
		}
		return policy;
	}
	//parses a DOM representation of a policy document into an AbstractPolicy
	private AbstractPolicy parsePolicyDocument(Document policyDoc) throws ParsingException
	{
		Element root = policyDoc.getDocumentElement();
		String name = root.getTagName();

		if(name.equals(XACMLConstants.POLICY_SET_ELEMENT_LOCAL_NAME))
			return PolicySet.getInstance(root, finder);
		else if(name.equals(XACMLConstants.POLICY_ELEMENT_LOCAL_NAME))
			return Policy.getInstance(root);
		else
			throw new ParsingException("The root element of the policy document must be '" + XACMLConstants.POLICY_SET_ID_LOCAL_NAME + "' or '" + XACMLConstants.POLICY_SET_ID_LOCAL_NAME + "', was: '" + name + "'");
	}
}