/** Java class "Property.java" generated from Poseidon for UML.
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
public class Property {

   public Property(String value) {this.value =value; }

   ///////////////////////////////////////
   // associations
   private String value;
/**
 * <p>
 * 
 * </p>
 */
    public Collection criterium = new HashSet(); // of type Criterium


   ///////////////////////////////////////
   // access methods for associations

    public String getValue(){ return value; }

    public Collection getCriteriums() {
        return criterium;
    }
    public void addCriterium(Criterium criterium) {
        if (! this.criterium.contains(criterium)) {
            this.criterium.add(criterium);
            criterium.setProperty(this);
        }
    }
    public void removeCriterium(Criterium criterium) {
        boolean removed = this.criterium.remove(criterium);
        if (removed) criterium.setProperty((Property)null);
    }

} // end Property





