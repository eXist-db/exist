/** Java class "Organ.java" generated from Poseidon for UML.
 *  Poseidon for UML is developed by <A HREF="http://www.gentleware.com">Gentleware</A>.
 *  Generated with <A HREF="http://jakarta.apache.org/velocity/">velocity</A> template engine.
 */
package wwbota.xmldb.core;

import java.util.*;

/**
 * <p>
 * 
 * </p>
 */
public class Organ {
	/**
	 * Constructor Organ.
	 * @param name
	 */
	public Organ(String name) {
		this.name = name;
	}

	///////////////////////////////////////
	// associations

	/**
	 * <p>
	 * 
	 * </p>
	 */
	public Organ container;
	/**
	 * <p>
	 * 
	 * </p>
	 */
	public Organ part;

	private String name;

	///////////////////////////////////////
	// access methods for associations

	public Organ getContainer() {
		return container;
	}
	public void setContainer(Organ organ) {
		if (this.container != organ) {
			this.container = organ;
			if (organ != null)
				organ.setPart(this);
		}
	}
	public Organ getPart() {
		return part;
	}
	public void setPart(Organ organ) {
		if (this.part != organ) {
			this.part = organ;
			if (organ != null)
				organ.setContainer(this);
		}
	}

	/**
	 * Returns the name.
	 * @return String
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name.
	 * @param name The name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

} // end Organ
