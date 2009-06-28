xquery version "1.0" encoding "UTF-8";
(: ------------------------------------------------------------------------------------- 

DEPRECATED

	xproc.xq - entry point for command-line invocation. 

---------------------------------------------------------------------------------------- :)

declare copy-namespaces no-preserve, no-inherit;
declare base-uri "file:///Users/jimfuller/Source/Webcomposite/xprocxq/main/";

(: XProc Namespace Declaration :)
declare namespace p="http://www.w3.org/ns/xproc";
declare namespace c="http://www.w3.org/ns/xproc-step";
declare namespace err="http://www.w3.org/ns/xproc-error";
declare namespace fn ="http://www.w3.org/TR/xpath-functions/";

(: Module Imports :)
import module namespace const = "http://xproc.net/xproc/const";
import module namespace u = "http://xproc.net/xproc/util";
import module namespace xproc = "http://xproc.net/xproc";
import module namespace std = "http://xproc.net/xproc/std";
import module namespace opt = "http://xproc.net/xproc/opt";
import module namespace ext = "http://xproc.net/xproc/ext";

(: -------------------------------------------------------------------------- :)

(: Module Vars :)
declare variable $flag external;
declare variable $xproc as item() external;
declare variable $stdin as item() external;
declare variable $bindings as xs:string external;
declare variable $_bindings := if ($bindings) then $bindings else ();
declare variable $options as xs:string external;
declare variable $dflag as item() external;
declare variable $tflag as item() external;
declare variable $oval as item() external;
declare variable $ival as item() external;

declare option exist:serialize "expand-xincludes=no";

(: -------------------------------------------------------------------------- :)

(: TODO:  will have to refactor stdin versus stdin2 at some point :)
declare variable $stdin2 := document{.};

(: -------------------------------------------------------------------------- :)
(: XProc Processing :)

    xproc:run($xproc,$stdin,$dflag,$tflag,tokenize($_bindings,','),tokenize($options,','))

(: -------------------------------------------------------------------------- :)


