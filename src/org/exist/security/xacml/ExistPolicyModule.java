package org.exist.security.xacml;

import java.net.URI;
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;

import com.sun.xacml.AbstractPolicy;
import com.sun.xacml.EvaluationCtx;
import com.sun.xacml.MatchResult;
import com.sun.xacml.ParsingException;
import com.sun.xacml.finder.PolicyFinder;
import com.sun.xacml.finder.PolicyFinderModule;
import com.sun.xacml.finder.PolicyFinderResult;
import org.exist.security.PermissionDeniedException;

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
	@SuppressWarnings("unused")
	private static final Logger LOG = LogManager.getLogger(ExistPolicyModule.class);

	private ExistPDP pdp;

	@SuppressWarnings("unused")
	private ExistPolicyModule() {}
	/**
	* Creates a new <code>ExistPolicyModule</code>.  Retains a reference
	* to the specified <code>BrokerPool</code>.
	*
	* @param pdp The <code>ExistPDP</code> for this database instance.
	*/
	public ExistPolicyModule(ExistPDP pdp)
	{
		if(pdp == null)
			{throw new NullPointerException("BrokerPool cannot be null");}
		this.pdp = pdp;
	}

	public boolean isRequestSupported()
	{
		return true;
	}
	public boolean isIdReferenceSupported()
	{
		return true;
	}
	public void init(PolicyFinder finder) {}

	public PolicyFinderResult findPolicy(EvaluationCtx context)
	{
		final BrokerPool pool = pdp.getBrokerPool();
		DBBroker broker = null;
		try
		{
			broker = pool.get(pool.getSecurityManager().getSystemSubject());
			return findPolicy(broker, context);
		}
                catch(final PermissionDeniedException pde) {
                    	return XACMLUtil.errorResult("Error while finding policy: " + pde.getMessage(), pde);
                }
		catch(final EXistException ee)
		{
			return XACMLUtil.errorResult("Error while finding policy: " + ee.getMessage(), ee);
		}
		finally
		{
			pool.release(broker);
		}
	}
	private PolicyFinderResult findPolicy(DBBroker broker, EvaluationCtx context) throws PermissionDeniedException
	{
		final DocumentSet mainPolicyDocs = XACMLUtil.getPolicyDocuments(broker, false);
		if(mainPolicyDocs == null)
			{return new PolicyFinderResult();}

		AbstractPolicy matchedPolicy = null;
		AbstractPolicy policy;
		MatchResult match;
		int result;
		try
		{
			final XACMLUtil util = pdp.getUtil();
			for(final Iterator<DocumentImpl> it = mainPolicyDocs.getDocumentIterator(); it.hasNext();)
			{
				policy = util.getPolicyDocument(it.next());
				match = policy.match(context);
				result = match.getResult();
				if(result == MatchResult.INDETERMINATE)
					{return new PolicyFinderResult(match.getStatus());}
				else if(result == MatchResult.MATCH)
				{
					if(matchedPolicy == null)
						{matchedPolicy = policy;}
					else
						{return XACMLUtil.errorResult("Matched multiple policies for reqest", null);}
				}
			}
		}
		catch(final ParsingException pe)
		{
			return XACMLUtil.errorResult("Error retrieving policies: " + pe.getMessage(), pe);
		}

		if(matchedPolicy == null)
			{return new PolicyFinderResult();}
		else
			{return new PolicyFinderResult(matchedPolicy);}
	}
	public PolicyFinderResult findPolicy(URI idReference, int type)
	{
		final BrokerPool pool = pdp.getBrokerPool();
		DBBroker broker = null;
		try
		{
			broker = pool.get(pool.getSecurityManager().getSystemSubject());
			final AbstractPolicy policy = pdp.getUtil().findPolicy(broker, idReference, type);
			return (policy == null) ? new PolicyFinderResult() : new PolicyFinderResult(policy);
		}
		catch(final Exception e)
		{
			return XACMLUtil.errorResult("Error resolving id '" + idReference.toString() + "': " + e.getMessage(), e);
		}
		finally
		{
			pool.release(broker);
		}
	}
}