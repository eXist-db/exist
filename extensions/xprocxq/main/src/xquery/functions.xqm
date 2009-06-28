xquery version "1.0" encoding "UTF-8";
module namespace func = "http://xproc.net/xproc/functions";
(: ------------------------------------------------------------------------------------- 
 
	functions.xqm - defines xproc xpath extensions.
	
---------------------------------------------------------------------------------------- :)

declare namespace p="http://www.w3.org/ns/xproc";
declare namespace c="http://www.w3.org/ns/xproc-step";
declare namespace err="http://www.w3.org/ns/xproc-error";
declare namespace xproc = "http://xproc.net/xproc";
declare namespace xsl="http://www.w3.org/1999/XSL/Transform";

(: XProc Namespaces :)
import module namespace const = "http://xproc.net/xproc/const";

(: -------------------------------------------------------------------------- :)

declare function func:system-property($property){

if ($property eq 'p:version') then
    $const:version
else if ($property eq 'p:episode') then
    $const:episode
else if ($property eq 'p:language') then
    $const:language
else if ($property eq 'p:product-name') then
    $const:product-name
else if ($property eq 'p:product-version') then
    $const:product-version
else if ($property eq 'p:vendor-uri') then
    $const:vendor-uri
else if ($property eq 'p:vendor') then
    $const:vendor
else if ($property eq 'p:xpath-version') then
    $const:xpath-version
else if ($property eq 'p:psvi-supported') then
    $const:psvi-supported
else
	()
	(:
	should throw a u:dynamicError('err:XD0015',"")
	:)
};