package org.exist.client.xacml;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.exist.util.MimeType;

public class NodeTransferable implements Transferable
{
	private static final String FLAVOR_DESCRIPTION = "XACML Element";
	private static final Map CLASS_TO_FLAVOR = new HashMap();
	
	public static final DataFlavor CONDITION_FLAVOR = createFlavor(ConditionNode.class);
	public static final DataFlavor TARGET_FLAVOR = createFlavor(TargetNode.class);
	public static final DataFlavor RULE_FLAVOR = createFlavor(RuleNode.class);
	public static final DataFlavor POLICY_FLAVOR = createFlavor(PolicyNode.class);
	public static final DataFlavor POLICY_SET_FLAVOR = createFlavor(PolicySetNode.class);
	public static final DataFlavor ABSTRACT_POLICY_FLAVOR = createFlavor(AbstractPolicyNode.class);
	public static final DataFlavor POLICY_ELEMENT_FLAVOR = createFlavor(PolicyElementNode.class);
	public static final DataFlavor ROOT_FLAVOR = createFlavor(RootNode.class);
	public static final DataFlavor ABSTRACT_NODE_FLAVOR = createFlavor(AbstractTreeNode.class);
	public static final DataFlavor NODE_FLAVOR = createFlavor(XACMLTreeNode.class);
	
	public static final DataFlavor TEXT_XML_FLAVOR = new DataFlavor(MimeType.XML_TYPE.getName(), FLAVOR_DESCRIPTION + " (XML)");
	public static final DataFlavor APPLICATION_XML_FLAVOR = new DataFlavor("application/xml", FLAVOR_DESCRIPTION + " (XML)");
	
	
	private static DataFlavor createFlavor(Class c)
	{
		DataFlavor ret = new DataFlavor(c, FLAVOR_DESCRIPTION);
		CLASS_TO_FLAVOR.put(c, ret);
		return ret;
	}
	
	private Set supportedFlavors;
	private XACMLTreeNode node;
	
	public NodeTransferable(XACMLTreeNode node)
	{
		this.node = node;
		
		supportedFlavors = new LinkedHashSet();
		supportedFlavors.add(TEXT_XML_FLAVOR);
		supportedFlavors.add(APPLICATION_XML_FLAVOR);
		supportedFlavors.add(DataFlavor.stringFlavor);

		for(Class c = node.getClass(); c != null; c = c.getSuperclass())
		{
			DataFlavor flavor = (DataFlavor)CLASS_TO_FLAVOR.get(c);
			if(flavor != null)
				supportedFlavors.add(flavor);
		}
	}

	public DataFlavor[] getTransferDataFlavors()
	{
		DataFlavor[] ret = new DataFlavor[supportedFlavors.size()];
		supportedFlavors.toArray(ret);
		return ret;
	}

	public boolean isDataFlavorSupported(DataFlavor flavor)
	{
		return supportedFlavors.contains(flavor);
	}

	public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException
	{
		if(XACMLTreeNode.class.isAssignableFrom(flavor.getRepresentationClass()))
			return node;
		if(DataFlavor.stringFlavor.equals(flavor))
			return node.serialize(true);
		if(TEXT_XML_FLAVOR.equals(flavor))
			return serialize(true);
		if(APPLICATION_XML_FLAVOR.equals(flavor))
			return serialize(false);
		throw new UnsupportedFlavorException(flavor);
	}
	private InputStream serialize(boolean indent)
	{
		String serializedString = node.serialize(true);
		return new ByteArrayInputStream(serializedString.getBytes());
	}

}
