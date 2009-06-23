/**
 * 
 */
package org.exist.xquery.value;

import org.exist.dom.QName;

/**
 * @author lcahlander
 *
 */
public class FunctionParameterSequenceType extends SequenceType {
	
	private String attributeName = null;
	private String description = null;

	/**
	 * @param nodeName
	 * @param primaryType
	 * @param cardinality
	 * @param description
	 */
	public FunctionParameterSequenceType(String attributeName, int primaryType, int cardinality, String description) {
		super(primaryType, cardinality);
		this.attributeName = attributeName;
		this.description = description;
	}

	/**
	 * @return the attributeName
	 */
	public String getAttributeName() {
		return attributeName;
	}

	/**
	 * @param attributeName the attributeName to set
	 */
	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @param description the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	
}
