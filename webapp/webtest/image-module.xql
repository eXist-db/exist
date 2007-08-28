xquery version "1.0";
(: $Id$ :)

import module namespace xdb="http://exist-db.org/xquery/xmldb";
import module namespace image="http://exist-db.org/xquery/image"
at "java:org.exist.xquery.modules.image.ImageModule";

let $t := (image:thumbnail(xs:anyURI("/db/webtest/images"), xs:anyURI("thumbs"), (), "t1_"),
    image:thumbnail(xs:anyURI("/db/webtest/images"), xs:anyURI("/db/webtest/images/thumbs"), (), "t2_"))
let $thumbs := xdb:get-child-resources("/db/webtest/images/thumbs")
return
    <html>
        <head><title>Testing image module functions</title></head>
        <body>
            <h1>Width/Height of image</h1>
            <p>Height: <span id="height">{xs:string(image:get-height(xs:anyURI("/db/webtest/images/logo.jpg")))}</span></p>
            <p>Width: <span id="width">{xs:string(image:get-width(xs:anyURI("/db/webtest/images/logo.jpg")))}</span></p>
            <h1>Generated thumbnails: <span id="thumbs-count">{count($thumbs)}</span></h1>
            <div>
            {
                for $r in $thumbs
                return
                    <img src="../rest/db/webtest/images/thumbs/{$r}"/>
            }
            </div>
        </body>
    </html>