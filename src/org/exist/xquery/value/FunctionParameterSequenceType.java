/*
 * 
 */
package org.exist.xquery.value;

/**
 * This class is used to specify the name and description of an XQuery function parameter.
 * @author lcahlander
 * @version 1.3
 *
 */
public class FunctionParameterSequenceType extends SequenceType {
	
	private String attributeName = null;
	private String description = null;

	/**
	 * @param attributeName	The name of the parameter in the <strong>FunctionSignature</strong>.
	 * @param primaryType	The <strong>Type</strong> of the parameter.
	 * @param cardinality	The <strong>Cardinality</strong> of the parameter.
	 * @param description	A description of the parameter in the <strong>FunctionSignature</strong>.
	 * @see FunctionSignature, Type, Cardinality
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
