xquery version "1.0";

module namespace style = "http://exist-db.org/mods-style";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace xf="http://www.w3.org/2002/xforms";
declare namespace xrx="http://code.google.com/p/xrx";


declare variable $style:site-home := '/';
declare variable $style:web-path-to-site := '/db/org/library';
declare variable $style:web-path-to-app := style:substring-before-last-slash(style:substring-before-last-slash(substring-after(request:get-uri(), '/rest')));
declare variable $style:db-path-to-site  := concat('xmldb:exist://',  $style:web-path-to-site);
declare variable $style:db-path-to-app  := concat('xmldb:exist://', $style:web-path-to-app) ;
declare variable $style:db-path-to-app-data := concat($style:db-path-to-app, '/data');

declare variable $style:site-resources := concat(request:get-context-path(), '/rest', $style:web-path-to-site, '/resources');
declare variable $style:site-images := concat($style:site-resources, '/images');
declare variable $style:site-css := concat($style:site-resources, '/css');
declare variable $style:site-info-file := concat($style:web-path-to-site, '/site-info.xml');
declare variable $style:site-info := doc($style:site-info-file);
declare variable $style:form-debug-default := true();

declare function style:substring-before-last-slash($arg as xs:string?)  as xs:string {
       
   if (matches($arg, '/'))
   then replace($arg,
            concat('^(.*)', '/','.*'),
            '$1')
   else ''
 } ;
 
declare function style:assemble-page($title as xs:string*, $breadcrumbs as node()*, 
                                     $style as element()*, $content as node()+) 
as element() {
    (
    util:declare-option('exist:serialize', 'method=xhtml media-type=text/html indent=yes')
    ,
    <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
            <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
            <title>{ $title }</title>
            { style:css('xhtml') }
            { $style }
        </head>
        <body>
            <div class="container">
                { style:header() } 
                { $breadcrumbs }
                <div class="inner">
                   <h2>{$title}</h2>
                   { $content }
                </div>
                { style:footer() }
            </div>
        </body>
     </html>
     )
};

declare function style:assemble-page($title as xs:string, $content as node()+) 
as element() {
    style:assemble-page($title, style:breadcrumbs(), (), $content)
};

declare function style:assemble-page($title as xs:string, $style as node(), $content as node()+) 
as element() {
    style:assemble-page($title, style:breadcrumbs(), $style, $content)
};

declare function style:breadcrumbs() as node() {
   <div class="breadcrumbs"></div>
};

declare function style:assemble-form($model as node(), $content as node()+) 
as node()+ {
    style:assemble-form((), (), $model, $content, true())
};

(:~
    An alternate version of style:assemble-form(), allowing debug mode.

    @param $model an XForms model node
    @param $content nodes for the body of the page
    @param $debug boolean to activate XSLTForms debug mode
    @return properly serialized XHTML+XForms page
:)
declare function style:assemble-form($model as node(), $content as node()+, $debug as xs:boolean) 
as node()+ {
    style:assemble-form((), (), $model, $content, $debug)
};

(:~
    A helper function for style:assemble-form(), with all optional parameters.

    @param $dummy-attributes an optional sequence of attributes to add to the HTML element
    @param $style an optional style node containing CDATA-encased CSS definitions
    @param $model an XForms model node
    @param $content nodes for the body of the page
    @return properly serialized XHTML+XForms page
:)
declare function style:assemble-form($title as xs:string, $dummy-attributes as attribute()*, $style as element(style)*, 
                                     $model as element(xf:model), $content as node()+)
as node()+ {
    style:assemble-form($dummy-attributes, $style, $model, $content, $style:form-debug-default)
};

(:~
    A helper function for style:assemble-form(), with all optional parameters.

    @param $title the text node containing the title of the page
    @param $breadcrumbs the element node containing the breadcrumbs
    @param $style an optional style node containing CDATA-encased CSS definitions
    @param $model an XForms model node
    @param $content nodes for the body of the page
    @param $dummy-attributes an optional sequence of attributes to add to the HTML element
    @param $debug boolean to activate XSLTForms debug mode
    @return properly serialized XHTML+XForms page
:)
declare function style:assemble-form(
        $title as xs:string,
        $dummy-attributes as attribute()*,
        $style as node()*, 
        $model as node(),
        $content as node()+, 
        $debug as xs:boolean) 
as node()+ {
    util:declare-option('exist:serialize', 'method=xhtml media-type=text/xml indent=yes process-xsl-pi=no')
    ,
    processing-instruction xml-stylesheet {concat('type="text/xsl" href="', request:get-context-path(), '/rest', '/db/xforms/xsltforms/xsltforms.xsl"')}
    ,
    if ($debug) then 
        processing-instruction xsltforms-options {'debug="yes"'}
    else ()
    ,
    <html 
    xmlns="http://www.w3.org/1999/xhtml" 
    xmlns:xf="http://www.w3.org/2002/xforms" 
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xmlns:mods="http://www.loc.gov/mods/v3"
    >{ $dummy-attributes }
        <head>
            
            <title>{ $title }</title>
            <link rel="stylesheet" type="text/css" href="edit.css"/>
            { style:css('xforms') }
            { $style }
            { $model }
        </head>
        <body>
            <div class="container">
                { style:header() } 
                <div class="inner">
                    <h2>{$title}</h2>
                    { $content }
                </div>
                { style:footer() }
            </div>
        </body>
    </html>
};

declare function style:css($page-type as xs:string) 
as node()+ {
    if ($page-type eq 'xhtml') then 
        (
       
        <link rel="stylesheet" href="{$style:site-css}/blueprint/screen.css" type="text/css" media="screen, projection" />,
        <link rel="stylesheet" href="{$style:site-css}/blueprint/print.css" type="text/css" media="print" />,<!--[if IE ]><link rel="stylesheet" href="{$style:site-css}/blueprint/ie.css" type="text/css" media="screen, projection" /><![endif]-->,
        <link rel="stylesheet" href="{$style:site-css}/styles.css" type="text/css" media="screen, projection" />

    )
    else if ($page-type eq 'xforms') then 
        <link rel="stylesheet" href="{$style:site-css}/xforms.css.xq" type="text/css" />
    else ()
};

declare function style:header() as node()+  {
        <div class="span-24 last">
            Library Managment System XRX Demo Copyright Dan McCreary &amp; Associates, All Rights Reserved.
            <hr/>
        </div>
};



declare function style:footer() 
as node()*  {
    let $footer-message := $style:site-info//xrx:site-footer-message/node()
    return
        <div class="span-24 last">
        <hr/>
            <div class="inner">
                <div class="footer">
                    Library Managment System XRX Demo Copyright Dan McCreary &amp; Associates, All Rights Reserved.
                </div>
            </div>
        </div>
};

