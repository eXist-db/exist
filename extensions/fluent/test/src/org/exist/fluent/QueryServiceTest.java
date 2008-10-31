package org.exist.fluent;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.junit.Test;

public class QueryServiceTest extends DatabaseTestCase {
	@Test public void let1() {
		assertEquals("foo", db.getFolder("/").query().let("$a", "foo").single("$a").value());
	}
	
	@Test public void let2() {
		assertEquals("foo", db.getFolder("/").query().let("a", "foo").single("$a").value());
	}
	
	@Test public void let3() {
		Folder f = db.getFolder("/");
		f.namespaceBindings().put("", "http://example.com");
		assertEquals("foo", f.query().let("$a", "foo").single("$a").value());
	}
	
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
		assertEquals(1, qa.requiredVariables().size());
		assertThat(qa.requiredVariables(), hasItems(new QName(null, "blah", null)));
	}
	
	@Test public void analyze6() {
		QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("declare namespace bar = 'http://example.com'; $bar:blah");
		assertEquals(1, qa.requiredVariables().size());
		assertThat(qa.requiredVariables(), hasItems(new QName("http://example.com", "blah", "bar")));
	}
	
	@Test public void analyze7() {
		QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("local:foo()");
		assertThat(qa.requiredFunctions(), hasItems(new QName("http://www.w3.org/2005/xquery-local-functions", "foo", "local")));
	}

	@Test public void analyze8() {
		QueryService.QueryAnalysis qa = db.getFolder("/").query().analyze("declare namespace bar = 'http://example.com'; bar:foo()");
		assertThat(qa.requiredFunctions(), hasItems(new QName("http://example.com", "foo", "bar")));
	}
}
