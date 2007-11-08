package org.exist.fluent;

import static org.junit.Assert.*;

import org.junit.Test;

public class QueryServiceTest extends DatabaseTestCase {
	@Test public void analyze1() {
		QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("zero-or-one(//blah)");
		assertEquals(QueryService.QueryAnalysis.Cardinality.ZERO_OR_ONE, qa.cardinality());
		assertEquals("item()", qa.returnTypeName());
	}

	@Test public void analyze2() {
		QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("exactly-one(//blah)");
		assertEquals(QueryService.QueryAnalysis.Cardinality.ONE, qa.cardinality());
		assertEquals("item()", qa.returnTypeName());
	}

	@Test public void analyze3() {
		QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("one-or-more(//blah)");
		assertEquals(QueryService.QueryAnalysis.Cardinality.ONE_OR_MORE, qa.cardinality());
		assertEquals("item()", qa.returnTypeName());
	}

	@Test public void analyze4() {
		QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("//blah");
		assertEquals(QueryService.QueryAnalysis.Cardinality.ZERO_OR_MORE, qa.cardinality());
		assertEquals("node()", qa.returnTypeName());
	}

	@Test public void analyze5() {
		QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("$blah");
		assertEquals("[$blah]", qa.requiredVariables().toString());
	}
}
