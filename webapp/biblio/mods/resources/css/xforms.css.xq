(: wraps all css in a <css> XML element, required by XSLTForms
 : TODO: look into how to set up a print stylesheet, since including 'blueprint/print.css' here 
 :       turns the entire page into the print-ready form on screen 
 : TODO: figure out how to pass util:binary-doc a relative URL, or somehow get the path via controller.xql
 :       so that we don't have to hardcode it here
 :)

declare option exist:serialize "method=xml media-type=text/css indent=yes";

let $path-to-css-files := '/db/org/library/resources/css/'
let $css-files := ('blueprint/screen.css', 'style.css', 'xforms-xsltforms.css')
return
    <css>{
        for $css-file in $css-files 
        return util:binary-to-string(util:binary-doc(concat($path-to-css-files, $css-file)))
    }</css>