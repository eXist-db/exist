/*
 * Copyright © 2001, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

/**
 * Test for XQuery Library Modules that import themselves, i.e. a self circular dependency.
 * See issue <a href="https://github.com/eXist-db/exist/issues/3448#issue-640018884">#3448</a>.
 */
public class SelfImportCircularDependencyIntegrationTest extends AbstractClassIntegrationTest {

    private static String TEST_COLLECTION = "/db/restxq/self-import-circular-dependency-integration-test";

    private static String XQUERY_FILENAME = "mod1.xqm";

    private static String STAGE1_XQUERY =
            "xquery version \"3.1\";\n" +
            "\n" +
            "module namespace test = \"test\";\n" +
            "\n" +
            "declare function test:f1() {\n" +
            "    <f1/>\n" +
            "};";

    private static String STAGE2_XQUERY =
            "xquery version \"3.1\";\n" +
            "\n" +
            "module namespace test = \"test\";\n" +
            "\n" +
            "import module namespace test = \"test\" at \"" + XQUERY_FILENAME + "\";\n" +
            "\n" +
            "declare function test:f1() {\n" +
            "    <f1/>\n" +
            "};";

    private static String STAGE3_XQUERY = STAGE1_XQUERY;

    @BeforeClass
    public static void storeResourceFunctions() throws IOException {
        enableRestXqTrigger(TEST_COLLECTION);
    }

    @Test
    public void storeSelfDependentXqueryLibraryModule() throws IOException {
        storeXquery(TEST_COLLECTION, XQUERY_FILENAME, STAGE1_XQUERY);
        storeXquery(TEST_COLLECTION, XQUERY_FILENAME, STAGE2_XQUERY);
        storeXquery(TEST_COLLECTION, XQUERY_FILENAME, STAGE3_XQUERY);
    }
}
