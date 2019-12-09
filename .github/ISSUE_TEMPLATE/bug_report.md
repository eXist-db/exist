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

```Xquery
xquery version "3.1";

module namespace t="http://exist-db.org/xquery/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

<!--  replace root with your data -->
declare variable $t:XML := document {
<root/>
};

<!--  replace index config if needed -->
declare variable $t:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
    <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <fulltext default="none" attributes="false"/>
    </index>
</collection>;

<!--  collections and indexes can be configured here  -->
declare
    %test:setUp
function t:setup() {
    let $testCol := xmldb:create-collection("/db", "test")
    let $indexCol := xmldb:create-collection("/db/system/config/db", "test")
    return
        (
            xmldb:store("/db/test", "test.xml", $t:XML),
            xmldb:store("/db/system/config/db/test", "collection.xconf", $t:xconf),
            xmldb:reindex("/db/test")
        )
};

declare
    %test:tearDown
function t:tearDown() {
    xmldb:remove("/db/test"),
    xmldb:remove("/db/system/config/db/test")
};

<-- Adjust to your reported issue -->
declare
    %test:assertTrue
function t:test() {
    let $test-data := collection('/db/test')
    for $result in $test-data//root
    return
       count($result) eq 1
};
```

If the above isn't working, please tell us the exact steps you took when you encountered the problem:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Screenshots**
If applicable, add screenshots to help explain your problem.

**Context (please always complete the following information):**
 - OS: [e.g. macOS 10.14.6]
 - eXist-db version: [e.g. 5.1.1]
 - Java Version [e.g. Java8u121]

**Additional context**
- How is eXist-db installed? [e.g. JAR installer, DMG, … ]
- Any custom changes in e.g. `conf.xml`?
