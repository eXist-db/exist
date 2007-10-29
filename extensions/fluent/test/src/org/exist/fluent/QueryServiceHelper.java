package org.exist.fluent;

/**
 * Created by IntelliJ IDEA.
 * User: wessels
 * Date: Oct 29, 2007
 * Time: 8:56:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class QueryServiceHelper extends DatabaseHelper {
    public void testAnalyze1() {
        QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("zero-or-one(//blah)");
        assertEquals(QueryService.QueryAnalysis.Cardinality.ZERO_OR_ONE, qa.cardinality());
        assertEquals("item", qa.returnTypeName());
    }

    public void testAnalyze2() {
        QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("exactly-one(//blah)");
        assertEquals(QueryService.QueryAnalysis.Cardinality.ONE, qa.cardinality());
        assertEquals("item", qa.returnTypeName());
    }

    public void testAnalyze3() {
        QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("one-or-more(//blah)");
        assertEquals(QueryService.QueryAnalysis.Cardinality.ONE_OR_MORE, qa.cardinality());
        assertEquals("item", qa.returnTypeName());
    }

    public void testAnalyze4() {
        QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("//blah");
        assertEquals(QueryService.QueryAnalysis.Cardinality.ZERO_OR_MORE, qa.cardinality());
        assertEquals("node", qa.returnTypeName());
    }

    public void testAnalyze5() {
        QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("$blah");
        assertEquals("[blah]", qa.requiredVariables().toString());
    }
}
