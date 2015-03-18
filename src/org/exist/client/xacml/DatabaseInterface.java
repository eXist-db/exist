package org.exist.client.xacml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.client.ClientFrame;
import org.exist.security.xacml.XACMLConstants;
import org.exist.security.xacml.XACMLUtil;

import org.apache.commons.io.output.ByteArrayOutputStream;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import com.sun.xacml.ParsingException;
import com.sun.xacml.Policy;
import com.sun.xacml.PolicySet;

public class DatabaseInterface
{
	private static final Logger LOG = LogManager.getLogger(DatabaseInterface.class);
	
	private Collection policyCollection;

	@SuppressWarnings("unused")
	private DatabaseInterface() {}
	public DatabaseInterface(Collection systemCollection)
	{
		setup(systemCollection);
	}

	public Collection getPolicyCollection()
	{
		return policyCollection;
	}
	
	private void setup(Collection systemCollection)
	{
		if(systemCollection == null)
			{throw new NullPointerException("System collection cannot be null");}
			
		InputStream in = null;
		try
		{
			final CollectionManagementService service = (CollectionManagementService)systemCollection.getService("CollectionManagementService", "1.0");
			policyCollection = service.createCollection(XACMLConstants.POLICY_COLLECTION_NAME);
			final Collection confCol = service.createCollection("config" + XACMLConstants.POLICY_COLLECTION);
			
			final String confName = XACMLConstants.POLICY_COLLECTION_NAME + ".xconf";
			final XMLResource res = (XMLResource)confCol.createResource(confName, "XMLResource");
			
			in = DatabaseInterface.class.getResourceAsStream(confName);
			if(in == null)
				{LOG.warn("Could not find policy collection configuration file '" + confName + "'");}
			
			final String content = XACMLUtil.toString(in);
			res.setContent(content);
			confCol.storeResource(res);
		}
		catch(final IOException ioe)
		{
			ClientFrame.showErrorMessage("Error setting up XACML editor", ioe);
		}
		catch(final XMLDBException xe)
		{
			ClientFrame.showErrorMessage("Error setting up XACML editor", xe);
		}
		finally
		{
			if(in != null)
			{
				try { in.close(); }
				catch(final IOException ioe) {}
			}
		}
	}
	
	public void writePolicies(RootNode root)
	{
		Set<String> removeDocs;
		try
		{
			removeDocs = new TreeSet<String>(Arrays.asList(policyCollection.listResources()));
		}
		catch(final XMLDBException xe)
		{
			LOG.warn("Could not list policy collection resources", xe);
			removeDocs = null;
		}
		
		final int size = root.getChildCount();
		for(int i = 0; i < size; ++i)
		{
			final AbstractPolicyNode node = (AbstractPolicyNode)root.getChild(i);
			
			String documentName = node.getDocumentName();
			if(documentName != null && removeDocs != null)
				{removeDocs.remove(documentName);}

			if(!node.isModified(true))
				{continue;}
			node.commit(true);

			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			node.create().encode(out);
			try
			{
				XMLResource xres;
				final Resource res = (documentName == null) ? null : policyCollection.getResource(documentName);
				if(res == null)
					{xres = null;}
				else if(res instanceof XMLResource)
					{xres = (XMLResource)res;}
				else
				{
					xres = null;
					policyCollection.removeResource(res);
				}
				
				if(xres == null)
				{
					xres = (XMLResource)policyCollection.createResource(documentName, "XMLResource");
					if(documentName == null)
					{
						documentName = xres.getDocumentId();
						node.setDocumentName(documentName);
					}
				}
				
				xres.setContent(out.toString());
				policyCollection.storeResource(xres);
				node.commit(true);
			}
			catch (final XMLDBException e)
			{
				final StringBuffer message = new StringBuffer();
				message.append("Error saving policy '");
				message.append(node.getId());
				message.append("' ");
				if(documentName != null)
				{
					message.append(" to document '");
					message.append(documentName);
					message.append("' ");
				}
				ClientFrame.showErrorMessage(message.toString(), e);
			}
		}
		if(removeDocs == null)
			{return;}
		for(final String documentName : removeDocs)
		{
			try
			{
				final Resource removeResource = policyCollection.getResource(documentName);
				policyCollection.removeResource(removeResource);
			}
			catch (final XMLDBException xe)
			{
				LOG.warn("Could not remove resource '" + documentName + "'", xe);
			}
		}
	}
	
	public RootNode getPolicies()
	{
		final RootNode root = new RootNode();
		findPolicies(root);
		root.commit(true);
		return root;
	}
	private void findPolicies(RootNode root)
	{
		try
		{
			final String[] resourceIds = policyCollection.listResources();
			for(int i = 0; i < resourceIds.length; ++i)
			{
				final String resourceId = resourceIds[i];
				final Resource resource = policyCollection.getResource(resourceId);
				if(resource != null && resource instanceof XMLResource)
					{handleResource((XMLResource)resource, root);}
			}
		}
		catch (final XMLDBException xe)
		{
			ClientFrame.showErrorMessage("Error scanning for policies", xe);
		}
	}
	private void handleResource(XMLResource xres, RootNode root) throws XMLDBException
	{
		final String documentName = xres.getDocumentId();
		final Node content = xres.getContentAsDOM();
		Element rootElement;
		if(content instanceof Document)
			{rootElement = ((Document)content).getDocumentElement();}
		else if(content instanceof Element)
			{rootElement = (Element)content;}
		else
		{
			LOG.warn("The DOM representation of resource '" + documentName + "' in the policy collection was not a Document or Element node.");
			return;
		}
		
		final String namespace = rootElement.getNamespaceURI();
		final String tagName = rootElement.getTagName();
		
		//sunxacml does not do namespaces, so this part is commented out for now
		if(/*XACMLConstants.XACML_POLICY_NAMESPACE.equals(namespace) && */XACMLConstants.POLICY_ELEMENT_LOCAL_NAME.equals(tagName))
		{
			Policy policy;
			try
			{
				policy = Policy.getInstance(rootElement);
			}
			catch(final ParsingException pe)
			{
				ClientFrame.showErrorMessage("Error parsing policy document '" + documentName +"'", pe);
				return;
			}
			root.add(new PolicyNode(root, documentName, policy));
		}
		else if(/*XACMLConstants.XACML_POLICY_NAMESPACE.equals(namespace) && */XACMLConstants.POLICY_SET_ELEMENT_LOCAL_NAME.equals(tagName))
		{
			PolicySet policySet;
			try
			{
				policySet = PolicySet.getInstance(rootElement);
			}
			catch(final ParsingException pe)
			{
				ClientFrame.showErrorMessage("Error parsing policy set document '" + documentName +"'", pe);
				return;
			}
			root.add(new PolicySetNode(root, documentName, policySet));
		}
		else
			{LOG.warn("Document '" + documentName + "' in policy collection is not a policy: root tag has namespace '" + namespace + "' and name '" + tagName + "'");}
	}
}
