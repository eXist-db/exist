/*
 *  $Header$
 * Copyright J.M. Vanel 2003 - under GNU Public Licence.
 */
package wwbota.xmldb.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

/**
 * <p>
 * Utility to translate a simple Query � la Google into an XPath constraints string.
 * Modelizes a Query on an XML database.
 * </p>
 * <p>
 * Basic use:
 * </p>
 * <pre>
 * import wwbota.xmldb.core.*;
 * Query q = Database.createQuery();
 * StringBuffer comment = new StringBuffer();
 * q.setPlainQuery(&quot;leaves:glabrous petals:5,yellow&quot;, comment);
 * q.setXPathPrefix( "/ * / *" );
 * q.setRubricXPath( " * [@*[local-name()='source']] / * / *" );
 * String xpath = q.getXPath();
 * // launch xpath query to XML database
 * // add comment to the response page
 * </pre>
 * <p>
 * </p>
 */
public class Query {

	///////////////////////////////////////
	// attributes

	/**
	 * <p>
	 * Represents ...
	 * </p>
	 */
	private String XPath;

	/**
	 * <p>
	 * A query in extended google style, e.g.:
	 * </p>
	 * <p>
	 * &lt;tt&gt;family:Rosaceae basal_leaves:glabrous petals:5,yellow&lt;/tt&gt;
	 * </p>
	 * <p>
	 * where &quot;petals&quot; is an XML tag name, and &quot;5&quot; and &quot;yellow&quot; are strings
	 * wanted inside of &lt;petal&gt; . &quot;basal_leaves:glabrous&quot; means :
	 * </p>
	 * <p>
	 * tag &lt;basal&gt; or attribute &quot;basal&quot; present, or attribute
	 * value &quot;basal&quot;, contained in tag &lt;leaf&gt;, and containing
	 * string &quot;glabrous&quot;.
	 * </p>
	 * 
	 */
	private String plainQuery;
	private String XPathPrefix = "/*/*";
	private String XPathConstraints = "";
	private String rubricXPath = "tr[@*[local-name()='source']] / td / *";

	///////////////////////////////////////
	// associations

	/**
	 * <p>
	 * {@link Criterium} objects.
	 * </p>
	 */
	public Collection criterium = new HashSet(); // of type Criterium
	/**
	 * <p>
	 * {@link Species} objects (unused now).
	 * </p>
	 */
	public Collection results = new HashSet(); // of type Species
	/**
	 * <p>
	 * 
	 * </p>
	 */
	public Database database;


	///////////////////////////////////////
	// access methods for associations

	 /** {@link Criterium} objects */
	public Collection getCriteriums() {
		return criterium;
	}
	public void addCriterium(Criterium criterium) {
		if (!this.criterium.contains(criterium)) {
			this.criterium.add(criterium);
			criterium.setQuery(this);
		}
	}
	public void removeCriterium(Criterium criterium) {
		boolean removed = this.criterium.remove(criterium);
		if (removed)
			criterium.setQuery((Query) null);
	}
	public Collection getResultss() {
		return results;
	}
	public void addResults(Species species) {
		if (!this.results.contains(species))
			this.results.add(species);
	}
	public void removeResults(Species species) {
		this.results.remove(species);
	}
	public Database getDatabase() {
		return database;
	}
	public void setDatabase(Database database) {
		this.database = database;
	}

	///////////////////////////////////////
	// operations

	/**
	 * <p>
	 * Decodes a plain text query (Google style) such as:
	 * "family:Rosaceae basal_leaves:glabrous petals:5,yellow",
	 * and updates accordingly the XPath query.
	 * It is possible to call setPlainQuery() and setXPathPrefix() in any order, before calling getXPath() or getXPathConstraints().
	 * TODO: "basal_leaves:glabrous"
	 * </p><p>
	 * @param comment ...
	 * </p><p>
	 * 
	 * </p>
	 */
	public void setPlainQuery( String plainQuery, java.lang.StringBuffer comment) {
                this.plainQuery = plainQuery;
                XPathConstraints = "";
		// XPath = "/flora/table/tr[//stem[contains(.,'compact')]]";

		// suppress initial blank(s):
                plainQuery = plainQuery.replaceFirst( "^ +"/*regex*/, ""/*replacement*/); 
		String[] criteriaStrings = plainQuery.split(" +");
		for (int i = 0; i < criteriaStrings.length; i++) {
			String crit = criteriaStrings[i];
			Criterium[] criteria = makeCriteria(crit);
			for (int j = 0; j < criteria.length; j++) {
				Criterium criter = criteria[j];
				XPathConstraints += criter.getXPath();
				criter.setRubricXPath(getRubricXPath());
				addCriterium(criter);
			}
		}
		// TODO: check the existence of the Organs in the database
		comment.append("\"" + plainQuery + "\" was a very beautiful query!");
	} // end setPlainQuery

	/**      * @param crit something without spaces like "petal:5,yellow" or just "petal:",  or just "edible". */
	private Criterium[] makeCriteria(String crit) {
		String[] s = crit.split(":");
		String tagName = "*"; // just "edible"
		String contains = "";
		if ( crit.indexOf(':') > -1 )
                  tagName = s[0]; // "petal:yellow" or just "petal:"
                else
		  contains = crit;
		Organ org = Metadata.createOrgan(tagName);
		Criterium[] returnValue = new Criterium[1];

		// We can have a plain Organ with no ":" after or nothing after the ":"
		if (s.length > 1) {
			String constraintsString = s[1];
			// We really have something after the ":"
			contains = constraintsString;
			// Do we have something like "petals:5,yellow" ?
			String[] constraints = constraintsString.split(",");
			if (constraints.length > 1)
				returnValue = new Criterium[constraints.length];
			for (int i = 0; i < constraints.length; i++) {
				returnValue[i] = new Criterium(org, constraints[i]);
			}
		} else {
			Criterium c = new Criterium(org, contains);
			returnValue[0] = c;
		}
		return returnValue;
	}

	public static final String testSimpleQuery = "family:Rosaceae  petals:5,yellow spine: edible ";
	public static boolean test(Query q) {
		if (q == null) q = new Query();
		StringBuffer comment = new StringBuffer();
		q.setPlainQuery(testSimpleQuery, comment);

	        String correctXPathQuery = q.getXPathPrefix() + "[.//family[contains(.,'Rosaceae')]][.//petals[contains(.,'5')]][.//petals[contains(.,'yellow')]][.//spine][.//*[contains(.,'edible')]]";
	        boolean res = q.getXPath().equals(correctXPathQuery);
		if (!res) {
			System.out.println("Query.q.getXPath(): " + q.getXPath() );
                System.out.println("     correct: " + correctXPathQuery );
        }
        		return res;
	}

	/**
	 * get absolute XPath ( == XPathPrefix + XPathConstraints ).
	 */
	public String getXPath() {
          return XPathPrefix + XPathConstraints;
	}
	public String getPlainQuery() { return plainQuery; }

	public void setXPathPrefix(String s) { this.XPathPrefix = s;}
	public String getXPathPrefix() {
          return XPathPrefix;
        }
	public String getXPathConstraints() {
          return XPathConstraints;
        }
	public static void main(String[] args) {
		boolean res = Query.test(new Query());
		System.out.println("Query.test(): " + res);
	}
	/** This property is the relative XPath match for a rubric or Organ, e.g.
		species
		or :
		flora/*
	 * Used in highlight of XML:DB search result; CAUTION: must be called before setPlainQuery() .
	*/
	public void setRubricXPath(String s) {
		this.rubricXPath = s;
	}
	public String getRubricXPath() {
		return rubricXPath;
	}
	/** A human-readable account of the Query;
        *   application should prepend something like "You have asked for a taxon containing " .
	*/
	public String getExplanation() {
        String returnValue = "";
        Iterator it = getCriteriums().iterator();
        while ( it.hasNext() ) {
          Criterium crit = (Criterium)(it.next());
          returnValue += crit.getExplanation();
          if ( it.hasNext() )
            returnValue += ", ";
        }
	return returnValue;
	}
} // end Query
