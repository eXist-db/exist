package org.exist.fluent;

public class QueryServiceTest extends DatabaseTestCase {
    public void testAnalyze1() {
        QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("zero-or-one(//blah)");
        assertEquals(QueryService.QueryAnalysis.Cardinality.ZERO_OR_ONE, qa.cardinality());
        assertEquals("item()", qa.returnTypeName());
    }

    public void testAnalyze2() {
        QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("exactly-one(//blah)");
        assertEquals(QueryService.QueryAnalysis.Cardinality.ONE, qa.cardinality());
        assertEquals("item()", qa.returnTypeName());
    }

    public void testAnalyze3() {
        QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("one-or-more(//blah)");
        assertEquals(QueryService.QueryAnalysis.Cardinality.ONE_OR_MORE, qa.cardinality());
        assertEquals("item()", qa.returnTypeName());
    }

    public void testAnalyze4() {
        QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("//blah");
        assertEquals(QueryService.QueryAnalysis.Cardinality.ZERO_OR_MORE, qa.cardinality());
        assertEquals("node()", qa.returnTypeName());
    }

    public void testAnalyze5() {
        QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("$blah");
        assertEquals("[blah]", qa.requiredVariables().toString());
    }
}
