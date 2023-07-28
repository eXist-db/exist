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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.util.MapUtil.hashMap;
import static org.junit.Assert.assertEquals;

/**
 * Test for XQuery Library Modules that depend on each other, i.e. a circular dependency.
 * See issue <a href="https://github.com/eXist-db/exist/issues/1010#issue-154689867">#1010</a>.
 *
 * mod1.xqm contains the Resource Functions, and depends on mod2.xqm, mod2.xqm depends on mod3.xqm which in turn depends on mod2.xqm.
 */
@RunWith(Parameterized.class)
public class LibraryCircularDependencyIntegrationTest extends AbstractInstanceIntegrationTest {

    /**
     * All possibilities for the order that the modules could be stored to the database in.
     */
    @Parameterized.Parameters(name = "{0} {1} {2}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { XQUERY_MOD1_FILENAME, XQUERY_MOD2_FILENAME, XQUERY_MOD3_FILENAME }
        });
    }

    private static String TEST_COLLECTION = "/db/restxq/library-circular-dependency-integration-test";

    private static String XQUERY_MOD1_FILENAME = "mod1.xqm";
    private static String XQUERY_MOD2_FILENAME = "mod2.xqm";
    private static String XQUERY_MOD3_FILENAME = "mod3.xqm";

    private static String MOD1_XQUERY =
            "xquery version \"3.1\";\n" +
            "\n" +
            "module namespace mod1 = \"mod1\";\n" +
            "import module namespace mod2 = \"mod2\" at \"" + XQUERY_MOD2_FILENAME + "\";\n" +
            "\n" +
            "declare namespace rest = \"http://exquery.org/ns/restxq\";\n" +
            "\n" +
            "declare\n" +
            "    %rest:GET\n" +
            "    %rest:path(\"/mod1\")\n" +
            "function mod1:f1() {\n" +
            "    <mod1>{mod2:f2()}</mod1>\n" +
            "};";

    private static String MOD2_XQUERY =
            "xquery version \"3.1\";\n" +
            "\n" +
            "module namespace mod2 = \"mod2\";\n" +
            "import module namespace mod3 = \"mod3\" at \"" + XQUERY_MOD3_FILENAME + "\";\n" +
            "\n" +
            "declare function mod2:f2() {\n" +
            "    <mod2>{mod3:f3()}</mod2>\n" +
            "};" +
            "\n" +
            "declare function mod2:other() {\n" +
            "    <mod2>other</mod2>\n" +
            "};";

    private static String MOD3_XQUERY =
            "xquery version \"3.1\";\n" +
            "\n" +
            "module namespace mod3 = \"mod3\";\n" +
            "import module namespace mod2 = \"mod2\" at \"" + XQUERY_MOD2_FILENAME + "\";\n" +
            "\n" +
            "declare function mod3:f3() {\n" +
            "    <mod3>{mod2:other()}</mod3>\n" +
            "};";

    private static Map<String, String> filenameToXQuery = hashMap(
            Tuple(XQUERY_MOD1_FILENAME, MOD1_XQUERY),
            Tuple(XQUERY_MOD2_FILENAME, MOD2_XQUERY),
            Tuple(XQUERY_MOD3_FILENAME, MOD3_XQUERY)
    );

    private static final int MAX_WAIT_PERIOD = 10 * 1000;  // 10 seconds
    private static final int WAIT_INTERVAL = 1000;  // 1 second

    @Parameterized.Parameter(0)
    public String storeFirstModuleFilename;
    @Parameterized.Parameter(1)
    public String storeSecondModuleFilename;
    @Parameterized.Parameter(2)
    public String storeThirdModuleFilename;

    @Before
    public void enableRestXq() throws IOException {
        enableRestXqTrigger(TEST_COLLECTION);
    }

    @After
    public void removeResourceFunctions() throws IOException, InterruptedException {
        removeXquery(TEST_COLLECTION, storeThirdModuleFilename);
        removeXquery(TEST_COLLECTION, storeSecondModuleFilename);
        removeXquery(TEST_COLLECTION, storeFirstModuleFilename);

        assertRestXqResourceFunctionsCount(0);
    }

    @Test
    public void storeCircularXqueryLibraryModules() throws IOException, InterruptedException {
        final String firstModuleContent = filenameToXQuery.get(storeFirstModuleFilename);
        storeXquery(TEST_COLLECTION, storeFirstModuleFilename, firstModuleContent);
        assertRestXqResourceFunctionsCount(0);

        final String secondModuleContent = filenameToXQuery.get(storeSecondModuleFilename);
        storeXquery(TEST_COLLECTION, storeSecondModuleFilename, secondModuleContent);
        assertRestXqResourceFunctionsCount(0);

        final String thirdModuleContent = filenameToXQuery.get(storeThirdModuleFilename);
        storeXquery(TEST_COLLECTION, storeThirdModuleFilename, thirdModuleContent);

        // RESTXQ is eventually consistent in its approach to try and connect the dependencies for compilation, so we need to give it a little time to do so...
        int restXqResourceFunctionsCount = 0;
        long timeSlept = 0;
        while (timeSlept < MAX_WAIT_PERIOD) {
            restXqResourceFunctionsCount = getRestXqResourceFunctions().getLength();
            if (restXqResourceFunctionsCount == 1) {
                break;
            }

            Thread.sleep(WAIT_INTERVAL);
            timeSlept += WAIT_INTERVAL;
        }
        assertEquals(1, restXqResourceFunctionsCount);
    }
}
