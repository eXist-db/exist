(::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::)
(:  File:       exist/http-client.xq                                        :)
(:  Author:     F. Georges - fgeorges.org                                   :)
(:  Date:       2009-03-07                                                  :)
(:  Tags:                                                                   :)
(:      Copyright (c) 2009 Florent Georges (see end of file.)               :)
(: -------------------------------------------------------------------------:)

xquery version "1.0";


(:~
 : Implementation for eXist of the EXPath HTTP Client module.
 :
 : @author Florent Georges - fgeorges.org
 : @version 0.1
 : @see http://www.expath.org/modules/http-client/
 :)
module namespace http = "http://www.expath.org/mod/http-client";
declare default function namespace "http://www.w3.org/2005/xpath-functions";

import module namespace xmldb = "http://exist-db.org/xquery/xmldb";
import module namespace hc    = "http://exist-db.org/xquery/httpclient";

declare namespace err = "urn:X-FGeorges:expath:httpclient:exist:err";


(:~
 : Call the eXist HTTP Client extension functions to actually send the request.
 :
 : <p>Send the request through eXist extensions, corresponding to the params.
 : The request is sent to <code>$href</code> (a lexcial <code>xs:anyURI</code>).
 : The exact function used depends on the value of <code>$method</code>.  The
 : headers passed to this function are those in <code>$headers</code>.  If
 : method is POST or PUT, the entity body is given by <code>$content</code>,
 : which must be a node (eXist does only allow to send XML.)</p>
 :
 : @param $href The URI to send the request to.  It must be a valid lexical
 :    <code>xs:anyURI</code> value.
 :
 : @param $method One of the string 'delete', 'get', 'head', 'options', 'post'
 :    or 'put'.  It is case insensitive.
 :
 : @param $headers The <code>headers</code> element, as expected by eXist HTTP
 :    functions.
 :
 : @param $content The XML payload of the request, if any.
 :
 : @return The return value of the eXist extension function actually called.
 :)
declare function http:do-send-request($href as xs:string,
                                      $method as xs:string,
                                      $headers as element(headers),
                                      $content as node()?)
      as element(hc:response)
{
   let $uri := xs:anyURI($href)
   let $m := lower-case($method)
      return
         if ( $m eq 'delete' ) then
            hc:delete($uri, true(), $headers)
         else if ( $m eq 'get' ) then
            hc:get($uri, true(), $headers)
         else if ( $m eq 'head' ) then
            hc:head($uri, true(), $headers)
         else if ( $m eq 'options' ) then
            hc:options($uri, true(), $headers)
         else if ( $m eq 'post' ) then
            hc:post($uri, $content, true(), $headers)
         else if ( $m eq 'put' ) then
            hc:put($uri, $content, true(), $headers)
         else
            error(xs:QName('err:TODO'), concat('Unknown or absent method:', $method))
};

(:~
 : Return a enumeration value from the Content-Type header.
 :
 : <p>The value returned is a string, either 'MULTIPART', 'HTML', 'XML',
 : 'TEXT', or 'BINARY'.</p>
 :)
declare function http:decode-content-type($header as xs:string)
      as xs:string
{
   let $type := normalize-space((substring-before($header, ';'), $header)[. ne ''][1])
      return
         if ( starts-with($type, 'multipart/') ) then
            'MULTIPART'
         else if ( $type eq 'text/html' ) then
            'HTML'
         else if ( ends-with($type, '+xml')
                      or $type eq 'text/xml'
                      or $type eq 'application/xml'
                      or $type eq 'text/xml-external-parsed-entity'
                      or $type eq 'application/xml-external-parsed-entity' ) then
            'XML'
         else if ( starts-with($type, 'text/')
                      or $type eq 'application/x-www-form-urlencoded'
                      or $type eq 'application/xml-dtd' ) then
            'TEXT'
         else
            'BINARY'
};

(:~
 : Return the EXPath HTTP response content items from an eXist HTTP response.
 :)
declare function http:make-content-items($resp as element(hc:response))
      as item()
{
   let $type := http:decode-content-type($resp/hc:body/@mimetype)
      return
         if ( $type eq 'MULTIPART' ) then
            error(xs:QName('err:TODO'), 'Not implemented yet.')
         else if ( $type = ('XML', 'HTML') ) then
            document { $resp/hc:body/* }
         else if ( $type eq 'TEXT' ) then
            xmldb:decode-uri(xs:anyURI($resp/hc:body))
         else if ( $type eq 'BINARY' ) then
            xs:base64Binary($resp/hc:body)
         else
            error(xs:QName('err:TODO'), 'Could not happen.')
};

(:~
 : If <code>$value</code> exists, return an attribute with given name and value.
 :
 : <p>The attribute has the name <code>$name</code> and value <code>$value</code>.
 : If <code>$value</code> is the empty sequence, returns the empty sequence.</p>
 :)
declare function http:make-attribute($name as xs:string, $value as xs:string?)
      as attribute()?
{
   if ( exists($value) ) then
      attribute { $name } { $value }
   else
      ()
};

(:~
 : If <code>$value</code> exists, return an eXist HTTP request header element.
 :
 : <p>The header has the name <code>$name</code> and value <code>$value</code>.
 : If <code>$value</code> is the empty sequence, returns the empty sequence.</p>
 :)
declare function http:make-header($name as xs:string, $value as xs:string?)
      as element(header)?
{
   if ( exists($value) ) then
      <header name="{ $name }" value="{ $value }"/>
   else
      ()
};

(:~
 : Make a Content-Type header element for an eXist HTTP request.
 :
 : <p>Make a header element as expected by eXist HTTP Client extension
 : functions.  It takes encoding and boundary into account, if any.  If
 : <code>$body</code> is the empty sequence, the result itself is the empty
 : sequence.</p>
 :
 : <p>TODO: Clarify the role of <code>@encoding</code>.  It does not correspond
 : to the use here in this function.  It should generate another header
 : instead, IMHO.</p>
 :
 : @param $body The EXPath HTTP request body element to generate a Content-Type
 :    header for.
 :)
declare function http:make-type-header($body as element(http:body)?)
      as element(hc:header)?
{
   $body/
      <header name="Content-Type" value="{
         concat(
            @content-type,
            if ( exists(@encoding) ) then '; encoding=' else '', @encoding,
            if ( exists(@boundary) ) then '; boundary=' else '', @boundary
         )
      }"/>
};

(:~
 : Given an eXist HTTP response, return the value of the asked for header.
 :
 : <p>The caller does not have to care about the case of the header name.  If
 : no header does actually match, the empty sequence is returned.</p>
 :
 : @param $resp The eXist HTTP response containing the headers within which
 :    selecting one particular header.
 :
 : @param $name The name of the header to select.
 :)
declare function http:get-response-header($resp as element(hc:response),
                                          $name as xs:string)
      as xs:string?
{
   (:
      NOTE: The predicate [. ne ''] is needed because a bug of eXist: the step
      string(@value) is evaluated at least once, even if the step before does
      evaluate to the empty sequence, so instead of the empty sequence, an
      empty string would be returned.
   :)
   $resp/hc:headers/hc:header[lower-case(@name) eq lower-case($name)]
      /string(@value)[. ne '']
};

(:~
 : Make the EXPath HTTP response body element from the eXist HTTP response.
 :
 : <p>Multipart responses are not supported.</p>
 :
 : <p>TODO: How can I find the value of @encoding?</p>
 :
 : @param $resp The response element as returned by the eXist HTTP functions.
 :)
declare function http:make-body($resp as element(hc:response))
      as item()
{
   let $type := http:get-response-header($resp, 'Content-Type')
   let $id   := http:get-response-header($resp, 'Content-ID')
   let $desc := http:get-response-header($resp, 'Content-Description')
      return
         if ( http:decode-content-type($type) eq 'MULTIPART' ) then
            <http:multipart> {
               error(xs:QName('err:TODO'), 'Not implemented yet.')
            }
            </http:multipart>
         else
            <http:body content-type="{ $type }"> {
               http:make-attribute('encoding', (: TODO: What?... :) ()),
               http:make-attribute('id', $id),
               http:make-attribute('description', $desc)
            }
            </http:body>
};

(:~
 : Implementation for eXist of the EXPath HTTP Client function.
 : 
 : @see http://www.expath.org/modules/http-client/
 :
 : @param $req The EXPath request element, as defined in the spec.
 :)
declare function http:send-request($req as element(http:request))
      as item()+
{
   http:send-request((), $req, (), ())
(:
   let $body := if ( exists($req/http:multipart) ) then
                   (: FIXME: Does not seem to be evaluated! :)
                   error(xs:QName('err:TODO'), 'Multipart not supported.')
                else
                   $req/zero-or-one(http:body),
       $headers := <headers> {
                      http:make-type-header($body/@content-type, $body/@encoding, $body/@boundary),
                      http:make-header('content-id', $body/@id),
                      http:make-header('content-description', $body/@description),
                      (: TODO: Handle @href... :)
                      $req/http:header/http:make-header(@name, @value)
                   }
                   </headers>,
       $r1 := http:do-send-request($req/@href, $req/@method, $headers, $body/*),
       (: follow at most one redirect :)
       $resp := if ( xs:integer($r1/@statusCode) eq 302 ) then
                   http:do-send-request(http:get-response-header($r1, 'Location'),
                                        $req/@method, $headers, $body/*)
                else
                   $r1
      return (
         (: TODO: Is @message available? :)
         <http:response status="{ $resp/@statusCode }"> {
            for $h in $resp/hc:headers/hc:header return
               $h/<http:header>{ @name, @value }</http:header>,
            http:make-body($resp)
         }
         </http:response>,
         http:make-content-items($resp)
       )
:)
};

(:~
 : Implementation for eXist of the EXPath HTTP Client function.
 : 
 : @see http://www.expath.org/modules/http-client/
 : 
 : @param $uri The URI to send the request to, can override the one in
 :    <code>$req</code>, as defined in the spec.
 :
 : @param $req The EXPath request element, as defined in the spec.
 :)
declare function http:send-request($uri as xs:string?,
                                   $req as element(http:request)?)
      as item()+
{
   http:send-request($uri, $req, (), ())
};

(:~
 : Implementation for eXist of the EXPath HTTP Client function.
 : 
 : @see http://www.expath.org/modules/http-client/
 : 
 : @param $uri The URI to send the request to, can override the one in
 :    <code>$req</code>, as defined in the spec.
 :
 : @param $req The EXPath request element, as defined in the spec.
 :
 : @param $content The content of the request (if POST or PUT.)  eXist does
 :    only allow an XML node to be send.
 :)
declare function http:send-request($uri as xs:string?,
                                   $req as element(http:request)?,
                                   $content as item()?)
      as item()+
{
   http:send-request($uri, $req, $content, ())
};

(:~
 : Implementation for eXist of the EXPath HTTP Client function.
 : 
 : @see http://www.expath.org/modules/http-client/
 : 
 : <p>TODO: Copy of http:send-request($req) for now.  Merge them with other
 : arities as well.</p>
 :
 : <p>TODO: Handle authentication.</p>
 :
 : <p>TODO: Handle @override-content-type.</p>
 :
 : <p>TODO: Handle @follow-redirect.</p>
 :
 : <p>TODO: Handle @status-only.</p>
 :
 : @param $uri The URI to send the request to, can override the one in
 :    <code>$req</code>, as defined in the spec.
 :
 : @param $req The EXPath request element, as defined in the spec.
 :
 : @param $content The content of the request (if POST or PUT.)  eXist does
 :    only allow an XML node to be send.
 :
 : @param $serial The serializer, as defined in the spec.  Not supported in
 :    this implementation, must be the empty sequence.
 :)
declare function http:send-request($uri as xs:string?,
                                   $req as element(http:request)?,
                                   $content as item()?,
                                   $serial as item()?)
      as item()+
{
   if ( exists($serial) ) then
      error(xs:QName('err:TODO'), '$serial not supported')
   else
      let $href := ($uri, $req/@href)[1]
      let $body := if ( exists($req/http:multipart) ) then
                      (: FIXME: Does not seem to be evaluated! :)
                      error(xs:QName('err:TODO'), 'Multipart not supported.')
                   else
                      $req/zero-or-one(http:body)
      let $headers := <headers> {
                         http:make-type-header($body),
                         http:make-header('content-id', $body/@id),
                         http:make-header('content-description', $body/@description),
                         (: TODO: Handle @href... :)
                         for $h in $req/http:header return
                            http:make-header($h/@name, $h/@value)
                      }
                      </headers>
      let $r1 := http:do-send-request($href, $req/@method, $headers, $body/*)
      (: Follow at most one redirect.  TODO: Only 302?  And other 3xx codes? :)
      let $resp := if ( xs:integer($r1/@statusCode) eq 302 ) then
                      http:do-send-request(http:get-response-header($r1, 'Location'),
                                           $req/@method, $headers, $body/*)
                   else
                      $r1
         return (
            (: TODO: Is @message available? :)
            <http:response status="{ $resp/@statusCode }"> {
               for $h in $resp/hc:headers/hc:header return
                  $h/<http:header>{ @name, @value }</http:header>,
               http:make-body($resp)
            }
            </http:response>,
            http:make-content-items($resp)
          )
};


(: ------------------------------------------------------------------------ :)
(:  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS COMMENT.               :)
(:                                                                          :)
(:  The contents of this file are subject to the Mozilla Public License     :)
(:  Version 1.0 (the "License"); you may not use this file except in        :)
(:  compliance with the License. You may obtain a copy of the License at    :)
(:  http://www.mozilla.org/MPL/.                                            :)
(:                                                                          :)
(:  Software distributed under the License is distributed on an "AS IS"     :)
(:  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.  See    :)
(:  the License for the specific language governing rights and limitations  :)
(:  under the License.                                                      :)
(:                                                                          :)
(:  The Original Code is: all this file.                                    :)
(:                                                                          :)
(:  The Initial Developer of the Original Code is Florent Georges.          :)
(:                                                                          :)
(:  Contributor(s): none.                                                   :)
(: ------------------------------------------------------------------------ :)

