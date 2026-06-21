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

module namespace tests="http://exist-db.org/test/http-client-examples";

declare namespace hc="http://expath.org/ns/http-client";
declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace util="http://exist-db.org/xquery/util";

declare variable $tests:port := util:system-property("wiremock.port");
declare variable $tests:base-url := "http://localhost:" || $tests:port;

declare
    %test:name("hello")
    %test:assertEquals("Hello world!")
function tests:hello() {
    let $request := <hc:request method="get" href="{$tests:base-url}/hello"/>
    let $response := hc:send-request($request)
    return $response[2]
};

declare
    %test:name("json")
    %test:assertXPath("parse-json($result)?message = 'Hello JSON'")
function tests:json() {
    let $request := <hc:request method="get" href="{$tests:base-url}/json"/>
    let $response := hc:send-request($request)
    return $response[2]
};

declare
    %test:name("xml")
    %test:assertXPath("$result/root/item = 'Hello XML'")
function tests:xml() {
    let $request := <hc:request method="get" href="{$tests:base-url}/xml"/>
    let $response := hc:send-request($request)
    return $response[2]
};

declare
    %test:name("post")
    %test:assertEquals("ACK")
function tests:post() {
    let $request := <hc:request method="post" href="{$tests:base-url}/post">
                        <hc:body media-type="text/plain">echo</hc:body>
                    </hc:request>
    let $response := hc:send-request($request)
    return $response[2]
};

declare
    %test:name("headers")
    %test:assertXPath("$result//*:header[@name = 'x-custom-header']/@value = 'X-Value'")
function tests:headers() {
    let $request := <hc:request method="get" href="{$tests:base-url}/headers"/>
    let $response := hc:send-request($request)
    return $response[1]
};
