/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */

package org.exist.security.xacml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.apache.commons.io.output.ByteArrayOutputStream;

import com.sun.xacml.Indenter;
import com.sun.xacml.PDP;
import com.sun.xacml.PDPConfig;
import com.sun.xacml.ctx.RequestCtx;
import com.sun.xacml.ctx.ResponseCtx;
import com.sun.xacml.ctx.Result;
import com.sun.xacml.ctx.Status;
import com.sun.xacml.finder.AttributeFinder;
import com.sun.xacml.finder.PolicyFinder;
import com.sun.xacml.finder.ResourceFinder;
import com.sun.xacml.finder.impl.CurrentEnvModule;

/**
* This class is responsible for creating the XACML Policy Decision Point (PDP)
* for a database instance.  The PDP is the entity that accepts access requests
* and makes a decision whether the access is allowed.  The PDP returns a decision
* to the requesting entity (called a Policy Enforcement Point, or PEP).  This
* decision is either Permit, Deny, Indeterminate, or Not Applicable.  Not
* Applicable occurs if no policy could be found that applied to the request.
* Indeterminate occurs if there was an error processing the request or the
* request was invalid.
* <p>
* This class also provides convenience methods for most uses.  The main method
* is <code>evaluate</code>, which will throw a
* <code>PermissionDeniedException</code> unless the decision was Permit and no
* Obligations were required.  An Obligation is a conditional access decision.
* If the PEP cannot perform the Obligation, then it cannot accept the decision.
* <p>
* <code>RequestHelper</code> provides methods for creating a
* <code>RequestCtx</code>, which is then passed to the <code>PDP</code> either
* indirectly by calling <code>evaluate</code> or directly by calling
* <code>getPDP().evaluate()</code>.  The first method can probably be used in
* most cases, while the second one allows more flexibility in handling the
* response.
*
* @see XACMLConstants
* @see ExistPolicyModule
* @see RequestHelper
*/
public class ExistPDP
{
	private static final Logger LOG = LogManager.getLogger(ExistPDP.class);

	private PDPConfig pdpConfig;
	//the per database instance util object
	private XACMLUtil util;
	//the PDP object that actually evaluates requests
	private PDP pdp;
	private BrokerPool pool;
	
	/**
	 * Assists client in creating <code>RequestCtx</code>s.
	 */
	private RequestHelper helper = new RequestHelper();

	@SuppressWarnings("unused")
	private ExistPDP() {}
	/**
	* @param pool A <code>BrokerPool</code> used to obtain an instance
	* of a DBBroker in order to read policies from the database.
	*/
	public ExistPDP(BrokerPool pool)
	{
		if(pool == null)
			{throw new NullPointerException("BrokerPool cannot be null");}
		this.pool = pool;
		
		util = new XACMLUtil(this);
		
		pdpConfig = new PDPConfig(createAttributeFinder(), createPolicyFinder(), createResourceFinder());
		pdp = new PDP(pdpConfig);
	}

	public void initializePolicyCollection()
	{
		util.initializePolicyCollection();
	}
	
	/**
	* Returns the <code>PDPConfig</code> used to initialize the
	* underlying <code>PDP</code>.
	*
	* @return the <code>PDPConfig</code>
	*/
	public PDPConfig getPDPConfig()
	{
		return pdpConfig;
	}
	
	/**
	 * Obtains the <code>BrokerPool</code> with which this instance
	 * is associated.
	 * 
	 * @return This instance's associated <code>BrokerPool</code>
	 */
	public BrokerPool getBrokerPool()
	{
		return pool;
	}
	
	/**
	 * Obtains the XACML utility instance for this database instance.
	 *  
	 * @return the associated XACML utility object
	 */
	public XACMLUtil getUtil()
	{
		return util;
	}

	/**
	* Performs any necessary cleanup operations.  Generally only
	* called if XACML has been disabled.
	*/
	public void close()
	{
		util.close();
	}
	
	/**
	* The method that will be used most of the time.  It provides the
	* simplest interface to the underlying <code>PDP</code> by
	* permitting the request only if the <code>ResponseCtx</code>
	* includes <code>Result</code>s that have no <code>Obligation</code>s
	* and only have the decision <code>Permit</code>.  Other cases
	* result in a <code>PermissionDeniedException</code>.  The other cases
	* include when an applicable policy cannot be found and when an error
	* occurs.
	*
	* @param request the access request
	* @throws PermissionDeniedException if the request is not allowed
	*/
	public void evaluate(RequestCtx request) throws PermissionDeniedException
	{
		if(request == null)
			{throw new PermissionDeniedException("Request cannot be null");}
		
		if(LOG.isDebugEnabled())
		{
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			request.encode(out, new Indenter(4));
			LOG.debug("Processing request:");
			LOG.debug(out.toString());
		}
		final ResponseCtx response = pdp.evaluate(request);
		if(LOG.isDebugEnabled())
		{
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			response.encode(out, new Indenter(4));
			LOG.debug("PDP response to request:");
			LOG.debug(out.toString());
		}
		handleResponse(response);
	}
	
	/**
	* This method handles a <code>ResponseCtx</code> generated by a
	* <code>PDP</code> request by doing nothing if the <code>ResponseCtx</code>
	* includes <code>Result</code>s that have no <code>Obligation</code>s
	* and only have the decision <code>Permit</code>.  Other cases
	* result in a <code>PermissionDeniedException</code>.  The other cases
	* include the Deny, Indeterminate, and Not Applicable decisions.
	*
	* @param response the <code>PDP</code> response to an access request
	* @throws PermissionDeniedException if the response does not have a decsion
	*		of Permit or it has any <code>Obligation</code>s.
	*/
	public void handleResponse(ResponseCtx response) throws PermissionDeniedException
	{
		if(response == null)
			{throw new PermissionDeniedException("The response was null");}

		final Set<Result> results = response.getResults();
		if(results == null || results.size() == 0)
			{throw new PermissionDeniedException("The response was empty");}

		for(final Result result : results)
			handleResult(result);
	}
	
	/**
	* This method handles a single <code>Result</code> generated by a
	* <code>PDP</code> request by doing nothing if the <code>Result</code>
	* has no <code>Obligation</code>s and only has the decision
	* <code>Permit</code>.  Other cases result in a
	* <code>PermissionDeniedException</code>. The other cases include a
	* decision of Deny, Indeterminate, or Not Applicable.
	*
	* @param result a <code>Result</code> in a <code>ResponseCtx</code>
	*		generated by a <code>PDP</code> in response to an access request
	* @throws PermissionDeniedException if the result does not have a decsion
	*		of Permit or it has any <code>Obligation</code>s.
	*/
	public void handleResult(Result result) throws PermissionDeniedException
	{
		if(result == null)
			{throw new PermissionDeniedException("A result of a request's response was null");}

		final Set obligations = result.getObligations();
		if(obligations != null && obligations.size() > 0)
		{
			throw new PermissionDeniedException("The XACML response had obligations that could not be fulfilled.");
		}

		final int decision = result.getDecision();
		if(decision == Result.DECISION_PERMIT)
			{return;}

		throw new PermissionDeniedException("The response did not permit the request.  The decision was: " + getDecisionString(decision, result.getStatus()));
	}
	//only really intended to be used by handleResult
	private static String getDecisionString(final int decision, final Status status)
	{
		switch(decision)
		{
			case Result.DECISION_PERMIT:
				return "permit the request";
			case Result.DECISION_DENY:
				return "deny the request";
			case Result.DECISION_INDETERMINATE:
				String error = (status == null) ? null : status.getMessage();
				if(error == null)
					{error = "";}
				else if(error.length() > 0)
					{error = ": " + error;}
				return "indeterminate (there was an error)" + error;
			case Result.DECISION_NOT_APPLICABLE:
				return "the request was not applicable to the policy";
			default:
				return ": of an unknown type";
		}
	}

	/** For use when <code>evaluate</code> is not flexible enough.  That is,
	* use this method when you want direct access to the <code>PDP</code>.
	* This allows you to use an <code>EvaluationCtx</code> instead of a
	* <code>RequestCtx</code> and direct access to the ResponseCtx to allow
	* for handling of <code>Obligation</code>s or decisions other than Permit.
	* <p>
	* The basic usage is then:
	* <p>
	* <code>ResponseCtx response = getPDP().evaluate(RequestCtx ctx)</code>
	* <p>
	* or
	* <p>
	* <code>ResponseCtx response = getPDP().evaluate(EvaluationCtx ctx)</code>
	* <p>
	* The response should then be checked for <code>Obligation</code>s and
	* the <code>PDP</code>'s decision.
	*
	* @return the actual <code>PDP</code> wrapped by this class
	*/
	public PDP getPDP()
	{
		return pdp;
	}
	
	/**
	 * Gets a <code>RequestHelper</code>
	 * 
	 * @return The <code>RequestHelper</code> for this database instance
	 */
	public RequestHelper getRequestHelper()
	{
		return helper;
	}

	/**
	* Creates a <code>ResourceFinder</code> that is used by the
	* <code>PDP</code> to locate hierarchical resources.  Hierarchical resources
	* are not currently supported (org.exist.security.xacml, not sunxacml) so
	* this method returns null.
	*
	* @return A <code>ResourceFinder</code> for hierarchical resources.
	*/
	private ResourceFinder createResourceFinder()
	{
		return null;
	}
	
	/**
	* Creates an <code>AttributeFinder</code> that is used by the
	* <code>PDP</code> to locate attributes required by a policy but unspecified
	* by the request context.  The XACML specification requires that certain
	* attributes of the environment always be available, so the CurrentEnvModule
	* is a provided <code>AttributeFinderModule</code> in the returned
	* <code>AttributeFinder</code>.  The other module looks up attributes
	* for a <code>User</code>.  This module, <code>UserAttributeModule</code>,
	* finds the user's name and the user's groups from the subject-id (which is
	* the uid of the user).
	* 
	* @return An <code>AttributeFinder</code> for unspecified attributes.
	*/
	private AttributeFinder createAttributeFinder()
	{
		final List<Object> modules = new ArrayList<Object>(2);
		modules.add(new UserAttributeModule(this));
		modules.add(new CurrentEnvModule());
		
		final AttributeFinder attributeFinder = new AttributeFinder();
		attributeFinder.setModules(modules);
		return attributeFinder;
	}
	/**
	* Creates a <code>PolicyFinder</code> that is used by the <code>PDP</code>
	* to locate <code>Policy</code>s for a given request or to resolve policy
	* references.  The returned <code>PolicyFinder</code> uses the
	* <code>ExistPolicyModule</code> for both resolving policy references and
	* finding the applicable policy for a given request.
	* 
	* @return A <code>PolicyFinder</code> for unspecified attributes.
	*/
	private PolicyFinder createPolicyFinder()
	{
		final ExistPolicyModule policyModule = new ExistPolicyModule(this);

		final PolicyFinder policyFinder = new PolicyFinder();
		policyFinder.setModules(Collections.singleton(policyModule));
		return policyFinder;
	}
}
