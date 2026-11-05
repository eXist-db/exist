(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.1";

(:~
 : An index that folds case (case="no") and has a collation: it keeps the keys in lower case, so it cannot answer a
 : comparison in a default collation that tells upper from lower case apart. It declines, and the comparison is evaluated
 : as written. With tertiary strength 'Apple', 'apple' and 'APPLE' are three different strings.
 :)
module namespace rccn="http://exist-db.org/xquery/range/collation-case-no/test";

declare default collation "http://www.w3.org/2013/collation/UCA?lang=en;strength=tertiary";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $rccn:DATA := <doc><w>Apple</w><w>apple</w><w>APPLE</w></doc>;

(: collection name -> index configuration; "none" has no configuration :)
declare variable $rccn:CONFIGS := map {
    "rccn-collation": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create qname="w" type="xs:string"
            collation="http://www.w3.org/2013/collation/UCA?lang=en;strength=tertiary"/></range></index>
    </collection>,
    "rccn-collation-case-no": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create qname="w" type="xs:string"
            collation="http://www.w3.org/2013/collation/UCA?lang=en;strength=tertiary" case="no"/></range></index>
    </collection>,
    "rccn-none": ()
};

declare
    %test:setUp
function rccn:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    for $name in map:keys($rccn:CONFIGS)
    return (
        if (exists($rccn:CONFIGS($name))) then (
            xmldb:create-collection("/db/system/config/db", $name),
            xmldb:store("/db/system/config/db/" || $name, "collection.xconf", $rccn:CONFIGS($name))
        ) else (),
        xmldb:create-collection("/db", $name),
        xmldb:store("/db/" || $name, "d.xml", $rccn:DATA)
    )
};

declare
    %test:tearDown
function rccn:teardown() {
    for $name in map:keys($rccn:CONFIGS)
    return (
        xmldb:remove("/db/" || $name),
        if (xmldb:collection-available("/db/system/config/db/" || $name)) then xmldb:remove("/db/system/config/db/" || $name) else ()
    )
};

declare %test:assertEquals(1) function rccn:no-index() {
    count(collection("/db/rccn-none")//w[. = "APPLE"])
};

declare %test:assertEquals(1) function rccn:collation() {
    count(collection("/db/rccn-collation")//w[. = "APPLE"])
};

(: the keys are in lower case: the index must not answer, or 'Apple' and 'apple' match as well :)
declare %test:assertEquals(1) function rccn:collation-and-case-no() {
    count(collection("/db/rccn-collation-case-no")//w[. = "APPLE"])
};
