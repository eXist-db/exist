xquery version "1.0" encoding "UTF-8";
module namespace const = "http://xproc.net/xproc/const";
(: ------------------------------------------------------------------------------------- 
 
	const.xqm - contains all constants used by xprocxq.
	
---------------------------------------------------------------------------------------- :)

(: -------------------------------------------------------------------------- :)
(: XProc Namespace Declaration :)
(: -------------------------------------------------------------------------- :)
declare namespace p="http://www.w3.org/ns/xproc";
declare namespace c="http://www.w3.org/ns/xproc-step";
declare namespace err="http://www.w3.org/ns/xproc-error";
declare namespace xproc = "http://xproc.net/xproc";
declare namespace xsl="http://www.w3.org/1999/XSL/Transform";


(: -------------------------------------------------------------------------- :)
(: XProc Namespace Constants :)
(: -------------------------------------------------------------------------- :)
declare variable $const:NS_XPROC := "http://www.w3.org/ns/xproc";
declare variable $const:NS_XPROC_STEP := "http://www.w3.org/ns/xproc-step";
declare variable $const:NS_XPROC_ERR := "http://www.w3.org/ns/xproc-error";


(: -------------------------------------------------------------------------- :)
(: Serialization Constants :)
(: -------------------------------------------------------------------------- :)
declare variable $const:DEFAULT_SERIALIZE := 'method=xml indent=yes';
declare variable $const:TRACE_SERIALIZE := 'method=xml';
declare variable $const:XINCLUDE_SERIALIZE := 'expand-xincludes=yes';
declare variable $const:TEXT_SERIALIZE := 'method=text';
declare variable $const:ESCAPE_SERIALIZE := 'method=xml indent=no';


(: -------------------------------------------------------------------------- :)
(: XProc Extension Namespaces :)
(: -------------------------------------------------------------------------- :)
declare variable $const:NS_XPROC_EXT := "http://xproc.net/ns/xproc/ex";
declare variable $const:NS_XPROC_ERR_EXT := "http://xproc.net/ns/errors";


(: -------------------------------------------------------------------------- :)
(: Error Dictionary lookup :)
(: -------------------------------------------------------------------------- :)
declare variable $const:error := doc("resource:net/xproc/xprocxq/etc/error-list.xml");
declare variable  $const:xprocxq-error := doc("resource:net/xproc/xprocxq/etc/xproc-error-list.xml");


(: -------------------------------------------------------------------------- :)
(: Step Definition lookup :)
(: -------------------------------------------------------------------------- :)
declare variable $const:ext-steps := doc("resource:net/xproc/xprocxq/etc/pipeline-extension.xml")/p:library;
declare variable $const:std-steps := doc("resource:net/xproc/xprocxq/etc/pipeline-standard.xml")/p:library;
declare variable $const:opt-steps := doc("resource:net/xproc/xprocxq/etc/pipeline-optional.xml")/p:library;
declare variable $const:comp-steps := doc("resource:net/xproc/xprocxq/etc/xproc-component.xml")/xproc:components;


(: -------------------------------------------------------------------------- :)
(: System Property :)
(: -------------------------------------------------------------------------- :)
declare variable $const:version :="0.5";
declare variable $const:product-version :="0.5";
declare variable $const:product-name :="xproc.xq";
declare variable $const:vendor :="James Fuller";
declare variable $const:language :="en";
declare variable $const:vendor-uri :="http://www.xproc.net/xproc.xq";
declare variable $const:xpath-version :="2.0";
declare variable $const:psvi-supported :="false";
declare variable $const:episode :="somerandomnumber";


(: -------------------------------------------------------------------------- :)
(: XProc default naming prefix :)
(: -------------------------------------------------------------------------- :)
declare variable $const:init_unique_id :="!1";


(: -------------------------------------------------------------------------- :)
(: Default imports for eval-step :)
(: -------------------------------------------------------------------------- :)
declare variable $const:default-imports :='

     import module namespace xproc = "http://xproc.net/xproc";
     import module namespace const = "http://xproc.net/xproc/const" at "resource:net/xproc/xprocxq/src/xquery/const.xqm";
     import module namespace u = "http://xproc.net/xproc/util" at "resource:net/xproc/xprocxq/src/xquery/util.xqm";
     import module namespace opt = "http://xproc.net/xproc/opt" at "resource:net/xproc/xprocxq/src/xquery/opt.xqm";
     import module namespace std = "http://xproc.net/xproc/std" at "resource:net/xproc/xprocxq/src/xquery/std.xqm";
     import module namespace ext = "http://xproc.net/xproc/ext" at "resource:net/xproc/xprocxq/src/xquery/ext.xqm";

     declare namespace xsl="http://www.w3.org/1999/XSL/Transform";
	 declare option exist:serialize "expand-xincludes=no";

';

declare variable $const:xpath-imports :='
    declare copy-namespaces preserve, inherit;
';


(: -------------------------------------------------------------------------- :)
(: Default imports for XProc xpath extension functions :)
(: -------------------------------------------------------------------------- :)
declare variable $const:alt-imports :=' declare copy-namespaces no-preserve, no-inherit; import module namespace p = "http://xproc.net/xproc/functions";';


(: -------------------------------------------------------------------------- :)
(: Mime types :)
(: -------------------------------------------------------------------------- :)
declare variable $const:pdf-mimetype := 'application/pdf';


(: -------------------------------------------------------------------------- :)
(: XSLT to transform eXist specific file listing :)
(: -------------------------------------------------------------------------- :)
declare variable $const:directory-list-xslt := 'resource:net/xproc/xprocxq/etc/directory-list.xsl';
