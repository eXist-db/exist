package wwbota.xmldb.core;

import java.util.*;

/** Currently unused clas, because we use Criterium.getXPathSimple()
    and generate the rest in highlight.xslt.xsp */
public class QueryHelper {

static public String makeHighlightXSLT(final Query q) {
  StringBuffer buf = new StringBuffer();
  Iterator it = q.getCriteriums().iterator();
  while ( it.hasNext() ) {
    Criterium crit = (Criterium)(it.next());
    //Organ organ = crit.getOrgan();
    //Property prop = crit.getProperty();
    buf.append( "<xsl:template match=\"" );
    buf.append( crit.getXPathSimple() );
/*
       if ( ! organ.getName().equals("") ) {
          buf.append( organ.getName() + "/" );
       } else { }
       buf.append( "text()" );
       if ( ! prop.getValue().equals("") ) {
          buf.append(
               "[contains(.,'" +
               prop.getValue() + "')]" );
       }
*/
    buf.append( "\" >\n" );
    buf.append( "  <xsl:apply-templates select='.' mode='highlight' />\n</xsl:template>\n\n");
/*
<xsl:template match="flower/text()[contains(.,'yellow')]" >
 <xsl:apply-templates select='.' mode="highlight" />
</xsl:template>
*/
  }
  return buf.toString();
}

  public static boolean test() {
	Query q = new Query();
        boolean res = Query.test(q);
        System.out.println( QueryHelper.makeHighlightXSLT(q) );
        return res;
  }
	public static void main(String[] args) {
		boolean res = QueryHelper.test();
		System.out.println("QueryHelper.test(): " + res);
	}
}
