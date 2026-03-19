---
name: Bug report
about: Thank you for reporting your issue and helping us to improve!
title: "[BUG]"
labels: ''
assignees: ''

---

> To be able to better understand you problem, please add as much information as possible to this ticket. Always test your bugs against the latest stable release of exist. We cannot provide support for older versions here on GitHub. If the version of eXist that is experiencing the issue is more than 1 major version behind the most recent release, please consider posting a question on our mailing list. 


**Describe the bug**
A clear and concise description of what the bug is.

**Expected behavior**
A clear and concise description of what you expected to happen.

**To Reproduce**
> The *best* way is to provide an [SSCCE (Short, Self Contained, Correct (Compilable), Example)](http://sscce.org/). One type of SSCCE could be a small test which reproduces the issue and can be run without dependencies. The [XQSuite - Annotation-based Test Framework for XQuery](http://exist-db.org/exist/apps/doc/xqsuite.xml) makes it very easy for you to create tests. These tests can be executed from the [eXide editor](http://exist-db.org/exist/apps/eXide/index.html) (XQuery - Run as Test)

```xquery
xquery version "3.1";

module namespace t="http://exist-db.org/xquery/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

(:~
 : Replace with minimal data that reproduces your issue.
 :)
declare variable $t:XML := document {
<root/>
};

(:~
 : Replace index config if needed for the reported bug.
 :)
declare variable $t:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
    <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <fulltext default="none" attributes="false"/>
    </index>
</collection>;

(:~
 : Use a unique collection name to avoid collisions with other test runs.
 : Keep tests self-contained and avoid mutating shared collections.
 :)
declare variable $t:COLL := "/db/test-" || translate(string(current-dateTime()), "-:TZ.+", "");
declare variable $t:CONF_COLL := "/db/system/config/db/" || substring-after($t:COLL, "/db/");

(:~
 : setUp should be idempotent and safe to run repeatedly.
 :)
declare
    %test:setUp
function t:setup() {
    let $_ := xmldb:create-collection("/db/system", "config")
    let $_ := xmldb:create-collection("/db/system/config", "db")
    let $_ := xmldb:create-collection("/db", substring-after($t:COLL, "/db/"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($t:COLL, "/db/"))
    return
        (
            xmldb:store($t:COLL, "test.xml", $t:XML),
            xmldb:store($t:CONF_COLL, "collection.xconf", $t:xconf),
            xmldb:reindex($t:COLL)
        )
};

(:~
 : tearDown should be idempotent: guard removals so partial setup does not fail cleanup.
 :)
declare
    %test:tearDown
function t:tearDown() {
    if (xmldb:collection-available($t:COLL)) then xmldb:remove($t:COLL) else (),
    if (xmldb:collection-available($t:CONF_COLL)) then xmldb:remove($t:CONF_COLL) else ()
};

(:~
 : Adjust this test body to your reported issue.
 : Prefer exact assertions (assertEquals/assertEqualsPermutation) over broad assertTrue when possible.
 :)
declare
    %test:assertEquals(1)
function t:test() {
    count(collection($t:COLL)//root)
};
```

If the above isn't working, please tell us the exact steps you took when you encountered the problem:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Screenshots**
If applicable, add screenshots to help explain your problem.

**Context (please always complete the following information)**
One option is to use [xst](https://www.npmjs.com/package/@existdb/xst), and copy and paste the output produced by running `xst info` here:**

 - Build: [eXist-6.4.1]
 - Java: [11.0.30+7]
 - OS: [Mac OS X 26.3.1]

**Additional context**
- How is eXist-db installed? [e.g. JAR installer, DMG, … ]
- Any custom changes in e.g. `conf.xml`?
