/** Java class "Species.java" generated from Poseidon for UML.
 *  Poseidon for UML is developed by <A HREF="http://www.gentleware.com">Gentleware</A>.
 *  Generated with <A HREF="http://jakarta.apache.org/velocity/">velocity</A> template engine.
 */
package wwbota.xmldb.core;

import java.util.*;
import org.w3c.dom.Element;

/**
 * <p>
 * 
 * </p>
 */
public class Species {

  ///////////////////////////////////////
  // attributes


/**
 * <p>
 * Represents ...
 * </p>
 */
    private Element description; 

   ///////////////////////////////////////
   // associations

/**
 * <p>
 * 
 * </p>
 */
    public Collection part = new HashSet(); // of type Organ


   ///////////////////////////////////////
   // access methods for associations

    public Collection getParts() {
        return part;
    }
    public void addPart(Organ organ) {
        if (! this.part.contains(organ)) this.part.add(organ);
    }
    public void removePart(Organ organ) {
        this.part.remove(organ);
    }

} // end Species





