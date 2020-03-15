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

import module namespace t = "http://exist-db.org/xquery/testing"
    at "resource:org/exist/xquery/lib/test.xq";

declare variable $doc external;
declare variable $id as xs:string? external;

(: hooks for sending external notifications about test events :)
declare variable $test-ignored-function as (function(xs:string) as empty-sequence())? external;
declare variable $test-started-function as (function(xs:string) as empty-sequence())? external;
declare variable $test-failure-function as (function(xs:string, map(xs:string, item()?), map(xs:string, item()?)?) as empty-sequence())? external;
declare variable $test-assumption-failed-function as (function(xs:string, map(xs:string, item()?)) as empty-sequence())? external;
declare variable $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())? external;
declare variable $test-finished-function as (function(xs:string) as empty-sequence())? external;

let $testSet :=
    if ($doc instance of xs:string) then
    	doc(concat("/db/test/", $doc))/TestSet
    else
    	$doc/TestSet
return
    t:run-testSet($testSet, $id,
            $test-ignored-function, $test-started-function, $test-failure-function,
            $test-assumption-failed-function, $test-error-function, $test-finished-function)
