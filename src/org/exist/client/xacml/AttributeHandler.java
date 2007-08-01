package org.exist.client.xacml;

import java.util.Set;

import com.sun.xacml.ParsingException;
import com.sun.xacml.attr.AttributeDesignator;
import com.sun.xacml.attr.AttributeValue;

/**
 * This interface defines methods for restricting
 * the functions or values a user is allowed to
 * enter for a given attribute.
 */
public interface AttributeHandler
{
	/**
	 * Removes functions that should not be available for the user to
	 * apply to an attribute.
	 * 
	 * @param functions The <code>Set</code> of functions to modify in place.
	 * @param attribute The relevant attribute 
	 */
	void filterFunctions(Set functions, AttributeDesignator attribute);
	
	/**
	 * Determines which values the user may select for an attribute.  If the user
	 * may enter values not in this set, this function should return true.
	 * 
	 * @param values The <code>Set</code> to which allowed values should be added.
	 * @param attribute The relevant attribute
	 * @return true if the user is not restricted to the values in the set,
	 * 	false otherwise 
	 */
	boolean getAllowedValues(Set values, AttributeDesignator attribute);
	
	/**
	 * Determines if the user entered value is valid.
	 *  
	 * @param value The value to check
	 * @param attribute The relevant attribute
	 * @throws ParsingException if the user entered value is invalid
	 */
	void checkUserValue(AttributeValue value, AttributeDesignator attribute) throws ParsingException;
}
