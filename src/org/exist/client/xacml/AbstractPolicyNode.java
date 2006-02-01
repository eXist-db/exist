package org.exist.client.xacml;

import com.sun.xacml.AbstractPolicy;
import com.sun.xacml.combine.CombiningAlgorithm;

public abstract class AbstractPolicyNode extends PolicyElementNode implements PolicyElementContainer
{
	private String documentName;
	
	private CombiningAlgorithm originalAlgorithm;
	private CombiningAlgorithm algorithm;

	public AbstractPolicyNode(NodeContainer parent, AbstractPolicy policy)
	{
		this(parent, null, policy);
	}
	public AbstractPolicyNode(NodeContainer parent, String documentName, AbstractPolicy policy)
	{
		super(parent, policy);
		
		this.documentName = documentName;
		this.algorithm = policy.getCombiningAlg();
		this.originalAlgorithm = this.algorithm;
	}
	
	/**
	 * Returns the name of the document represented by this
	 * element if it is the top-level policy element of the
	 * document.
	 * 
	 * @return The document name, or null if this element
	 *  is not a top-level element
	 */
	public String getDocumentName()
	{
		return documentName;
	}
	
	public CombiningAlgorithm getCombiningAlgorithm()
	{
		return algorithm;	
	}
	public void setCombiningAlgorithm(CombiningAlgorithm algorithm)
	{
		if(algorithm == null)
			throw new NullPointerException("Combining algorithm cannot be null");
		this.algorithm = algorithm;
		fireChanged();
	}
	
	public boolean isModified(boolean deep)
	{
		return super.isModified(deep) || isAlgorithmModified();
	}
	public boolean isAlgorithmModified()
	{
		return !algorithm.getIdentifier().equals(originalAlgorithm.getIdentifier());
	}
	public void commit(boolean deep)
	{
		originalAlgorithm = algorithm;
		super.commit(deep);
	}
	public void revert(boolean deep)
	{
		algorithm = originalAlgorithm;
		super.revert(deep);
	}
	public void setDocumentName(String documentName)
	{
		if(this.documentName == null)
			this.documentName = documentName;
		else
			throw new IllegalStateException("Document name has already been set");
	}
}
