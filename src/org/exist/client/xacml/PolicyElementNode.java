package org.exist.client.xacml;

import java.net.URI;

import org.exist.security.xacml.XACMLUtil;

import com.sun.xacml.PolicyTreeElement;

public abstract class PolicyElementNode extends AbstractNodeContainer implements NodeContainer
{
	private URI originalId;
	private URI id;
	
	private String originalDescription;
	private String description;
	
	private TargetNode target;
	
	protected PolicyElementNode(NodeContainer parent, PolicyTreeElement policyElement)
	{
		super(parent);
		if(policyElement == null)
			throw new NullPointerException("Policy element cannot be null");
		id = policyElement.getId();
		if(id == null)
			throw new NullPointerException("Policy element ID cannot be null");
		description = policyElement.getDescription();
		target = new TargetNode(this, policyElement.getTarget());
		
		originalId = id;
		originalDescription = description;
	}
	
	/**
	 * Creates a <code>PolicyTreeElement</code>
	 * from this object's current state.
	 * 
	 * @return a <code>PolicyTreeElement</code>
	 */
	abstract PolicyTreeElement create();

	/**
	 * Creates a <code>PolicyTreeElement</code>
	 * from this object's current state except
	 * that it uses the specified id.
	 * 
	 * @return a <code>PolicyTreeElement</code>
	 */
	abstract PolicyTreeElement create(URI id);
	
	/**
	 * Retrieves the id of this element.
	 *  
	 * @return the id
	 */
	public URI getId()
	{
		return id;
	}
	
	/**
	 * Provides a string representation appropriate for display
	 * to the user.
	 * 
	 * @return The string representation
	 */
	public String toString()
	{
		return id.toString();
	}
	
	/**
	 * Sets the id of this element.
	 * 
	 * @param id The new id, which cannot be null.
	 */
	void setId(URI id)
	{
		if(id == null)
			throw new NullPointerException("Policy element ID cannot be null");
		this.id = id;
		fireChanged();
	}
	
	/**
	 * Gets the description of the element.
	 * 
	 * @return the description
	 */
	public String getDescription()
	{
		return description;
	}
	
	/**
	 * Sets the description of the element.  May be null.
	 * 
	 * @param description The new description.
	 */
	public void setDescription(String description)
	{
		this.description = description;
		fireChanged();
	}
	
	public boolean isIdModified()
	{
		return !id.equals(originalId);
	}
	public boolean isDescriptionModified()
	{
		if(description == null)
			return originalDescription != null;
		return !description.equals(originalDescription);
	}
	
	public boolean isModified(boolean deep)
	{
		if(super.isModified(deep) || isIdModified() || isDescriptionModified())
			return true;
		if(deep)
		{
			if(target.isModified(deep))
				return true;
		}
		return false;
	}
	public void revert(boolean deep)
	{
		description = originalDescription;
		id = originalId;
		if(deep)
			target.revert(deep);
		super.revert(deep);
	}
	public void commit(boolean deep)
	{
		originalDescription = description;
		originalId = id;
		if(deep)
			target.commit(deep);
		super.commit(deep);
	}
	
	/**
	 * Gets the wrapper around the target for this element.
	 * The returned value will never be null, even if this
	 * element has an empty target.
	 * 
	 * @return a wrapper around this element's target
	 */
	public TargetNode getTarget()
	{
		return target;
	}
	

	public String serialize(boolean indent)
	{
		return XACMLUtil.serialize(create(), indent);
	}
}
