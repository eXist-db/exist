/** Java class "Criterium.java" generated from Poseidon for UML.
 *  Poseidon for UML is developed by <A HREF="http://www.gentleware.com">Gentleware</A>.
 *  Generated with <A HREF="http://jakarta.apache.org/velocity/">velocity</A> template engine.
 */
package wwbota.xmldb.core;


/**
 * <p>
 * 
 * </p>
 */
public class Criterium {
	/**
	 * Constructor Criterium.
	 * @param org
	 * @param contains
	 */
	public Criterium(Organ org, String contains) {
		this.setOrgan(org);
		this.contains = contains;
	        setProperty(new Property(contains));
	}

	///////////////////////////////////////
	// attributes

	/**
	 * <p>
	 * A String or word required for the search.
	 * </p>
	 */
	private String contains;

	///////////////////////////////////////
	// associations

	/**
	 * <p>
	 * 
	 * </p>
	 */
	public Query query;
	/**
	 * <p>
	 * 
	 * </p>
	 */
	public Organ organ;
	/**
	 * <p>
	 * 
	 * </p>
	 */
	public Property property;
	/**
	 * <p>
	 * 
	 * </p>
	 */
	public Species results;
	private String rubricXPath = ""; // "tr[@*[local-name()='source']] / td / *";
	private boolean usingWildcards = true;

	///////////////////////////////////////
	// access methods for associations

	public Query getQuery() {
		return query;
	}
	public void setQuery(Query query) {
		if (this.query != query) {
			if (this.query != null)
				this.query.removeCriterium(this);
			this.query = query;
			if (query != null)
				query.addCriterium(this);
		}
	}
	public Organ getOrgan() {
		return organ;
	}
	public void setOrgan(Organ organ) {
		this.organ = organ;
	}
	public Property getProperty() {
		return property;
	}
	public void setProperty(Property property) {
		if (this.property != property) {
			if (this.property != null)
				this.property.removeCriterium(this);
			this.property = property;
			if (property != null)
				property.addCriterium(this);
		}
	}
	public Species getResults() {
		return results;
	}
	public void setResults(Species species) {
		this.results = species;
	}

	/**
	 * @return an XPath constrain expression, suitable to append to a composite XPath query, e.g.
		  [.//stem[contains(.,'compact')]]
	 */
	public String getXPath() {
		String returnValue = "[.//";
		returnValue += getXPathSimple();
		returnValue += "]";
		return returnValue;
	}

	/**
	 * @return an relative XPath expression, suitable to be a match for an XSLT template, e.g.
		  stem[contains(.,'compact')]
	 * Used in XML:DB search.
	 */
	public String getXPathSimple() {
		String returnValue = "";
		if ( isGlobal() ) {
			returnValue += "*";
		} else {
			returnValue += "" + getOrgan().getName();
		}
		if (!contains.equals("")) {
			// TODO: contains should be "XMLencoded" in returnValue
			if ( usingWildcards ) {
				returnValue += "[.&='*" + contains + "*']";
			} else {
				returnValue += "[.&='" + contains + "']";
				// eXist query too slow: returnValue += "[contains(.,'" + contains + "')]";
			}
		}
		return returnValue;
	}

	/**
	 * @return an XPath global match expression, suitable to be a match for an XSLT template, e.g.
		stem[contains(.,'compact')]
		or :
		flora/species/*[contains(.,'compact')]
	 * Used in highlight of XML:DB search result, with XSLT stylesheets.
	 */
	public String getXPathGlobalMatch() {
		String returnValue = "";
		if ( isGlobal() ) {
			returnValue += getRubricXPath();
		} else {
			returnValue += "" + getOrgan().getName();
		}
		if (!contains.equals(""))
			// TODO: contains should be "XMLencoded" in returnValue
			returnValue += "[contains(.,'" + contains + "')]";
		return returnValue;
	}

	/** This property is true <==> the Criterium applies to any rubric or Organ.
	*/
	public boolean isGlobal() {
		boolean returnValue = false;
		String organName = getOrgan().getName();
		if ( organName.equals("") ||
		     organName.equals("*") )
		  returnValue = true;
		return returnValue;
	}

	/** This property is the relative XPath match for a rubric or Organ, e.g.
		species
		or :
		flora/*
	 * Used in highlight of XML:DB search result.
	*/
	public void setRubricXPath(String s) {
		this.rubricXPath = s;
	}
	public String getRubricXPath() {
		return rubricXPath;
	}

	/** This property tells wether eXist XML:DB search should use wildcards for search terms.
	*/
	public void isUsingWildcards(boolean b) {
		this.usingWildcards = b;
	}
	public boolean getUsingWildcards() {
		return usingWildcards;
	}
	/** A human-readable account of the Criterium.
	*/
	public String getExplanation() {
		String returnValue = "";
		if ( isGlobal() ) {
			returnValue += "\"" + contains + "\"";
		} else {
			returnValue += "\"" + contains + "\"" + " in " + getOrgan().getName();
		}
		return returnValue;
	}

} // end Criterium
