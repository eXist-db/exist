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
	
	@Test(expected = DatabaseException.class) public void let4() {
		Folder f = db.getFolder("/");
		assertEquals("foo", f.query().let("$a", "foo").single("$a").value());
		f.query().single("$a");
	}
	
	@Test(expected = DatabaseException.class) public void let5() {
		Folder f = db.getFolder("/");
		assertEquals("foo", f.query().single("$_1", "foo").value());
		f.query().single("$_1");
	}

	@Test public void importModule1() {
		Folder f = db.getFolder("/");
		Document doc = f.documents().load(Name.create(db,"module1"), Source.blob(
				"module namespace ex = 'http://example.com';\n" +
				"declare function ex:foo() { 'foo' };\n"
		));
		assertEquals("foo", f.query().importModule(doc).single("ex:foo()").value());
	}
	
	@Test public void importModule2() {
		Folder f = db.createFolder("/top/next");
		Document doc = f.documents().load(Name.create(db,"module1"), Source.blob(
				"\n\nmodule  namespace  _123=\"http://example.com?a=1-2&amp;b=4\" ;\n" +
				"declare function _123:foo() { 'foo' };\n"
		));
		assertEquals("foo", f.query().importModule(doc).single("_123:foo()").value());
	}
	
	@Test public void importModule3() {
		Folder f = db.getFolder("/");
		Document doc1 = f.documents().load(Name.create(db,"module1"), Source.blob(
				"module namespace ex = 'http://example.com';\n" +
				"declare function ex:foo() { 'foo' };\n"
		));
		Document doc2 = f.documents().load(Name.create(db,"module2"), Source.blob(
				"module namespace ex = 'http://example.com';\n" +
				"declare function ex:foo() { 'bar' };\n"
		));
		assertEquals("foo", f.query().importModule(doc1).single("ex:foo()").value());
		assertEquals("bar", f.query().importModule(doc2).single("ex:foo()").value());
	}
	
	@Test(expected = DatabaseException.class) public void importModule4() {
		Folder f = db.getFolder("/");
		Document doc1 = f.documents().load(Name.create(db,"module1"), Source.blob(
				"module namespace ex = 'http://example.com';\n" +
				"declare function ex:foo() { 'foo' };\n"
		));
		Document doc2 = f.documents().load(Name.create(db,"module2"), Source.blob(
				"module namespace ex = 'http://example.com/other';\n" +
				"declare function ex:foo() { 'bar' };\n"
		));
		f.query().importModule(doc1).importModule(doc2).single("ex:foo()");
	}
	
	@Test public void importModule5() {
		Folder f = db.getFolder("/");
		Document module = f.documents().load(Name.create(db,"module"), Source.blob(
				"module namespace ex = 'http://example.com';\n" +
				"declare function ex:root() { / };\n"
		));
		XMLDocument doc1 = f.documents().load(Name.create(db,"doc1"), Source.xml("<foo/>"));
		f.documents().load(Name.create(db,"doc2"), Source.xml("<foo/>"));
		QueryService qs = db.query(doc1).importModule(module);
		assertEquals(1, qs.all("/").size());
		assertEquals(1, qs.all("ex:root()").size());
	}
	
	@Test public void limitRootDocuments1() {
		Folder f = db.getFolder("/");
		XMLDocument doc1 = f.documents().load(Name.create(db,"doc1"), Source.xml("<foo/>"));
		f.documents().load(Name.create(db,"doc2"), Source.xml("<foo/>"));
		assertEquals(2, f.query().all("/foo").size());
		assertEquals(1, f.query().limitRootDocuments(doc1).all("/foo").size());
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
