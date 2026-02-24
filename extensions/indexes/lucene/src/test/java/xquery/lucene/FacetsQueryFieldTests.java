/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * Minimal test runner for facets query-field tests.
 * mvn test -Dtest=FacetsQueryFieldTests -pl extensions/indexes/lucene -DfailIfNoTests=false
 */
package xquery.lucene;

import org.exist.test.runner.XSuite;
import org.junit.runner.RunWith;

@RunWith(XSuite.class)
@XSuite.XSuiteFiles({
    "src/test/xquery/lucene/facets.xql"
})
public class FacetsQueryFieldTests {
}
