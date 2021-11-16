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

module namespace fixtures="http://exist-db.org/xquery/test/util/fixtures";
import module namespace util="http://exist-db.org/xquery/util";

(: file contents :)

declare variable $fixtures:XML := document {<foo><bar/></foo>};
declare variable $fixtures:TXT :=
``[12 12
This is just a Text
]``
;
declare variable $fixtures:XQY := "xquery version ""3.1""; 0 to 9";
declare variable $fixtures:BIN := "To bin or not to bin...";

(: modification dates :)

declare variable $fixtures:now := current-dateTime();
declare variable $fixtures:mod-date := $fixtures:now + xs:dayTimeDuration('PT1H');
declare variable $fixtures:mod-date-2 := $fixtures:now + xs:dayTimeDuration('PT2H');

(: collections :)

declare variable $fixtures:collection-name := "file-module-test";
declare variable $fixtures:child-collection-name := "data";
declare variable $fixtures:collection := "/db/" || $fixtures:collection-name;
declare variable $fixtures:child-collection := $fixtures:collection || "/" || $fixtures:child-collection-name;

(: file sync results :)

declare variable $fixtures:ALL-UPDATED := ("test-text.txt", "test-query.xq", "bin", "test-data.xml");

declare variable $fixtures:ROOT-FS := ("bin", "test-text.txt", "test-query.xq", "data");

declare variable $fixtures:EXTRA-DATA := ("test", ".env");

declare variable $fixtures:ROOT-FS-EXTRA := ("test", "bin", ".env", "test-text.txt", "test-query.xq", "data");
