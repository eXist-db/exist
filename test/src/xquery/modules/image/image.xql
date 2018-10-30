xquery version "3.1";

module namespace img="http://exist-db.org/xquery/test/image";

declare namespace test="http://exist-db.org/xquery/xqsuite";
import module namespace image="http://exist-db.org/xquery/image";

(:~
: testsuite for the image module http://exist-db.org/xquery/image.
:
: @see https://github.com/eXist-db/exist/issues/2242
: @see https://github.com/eXist-db/exist/issues/1456
:
: @author Duncan Paterson
:)
declare variable $img:testCol := xmldb:create-collection("/db", "test_image");
declare variable $img:png := 'https://www.google.com/images/branding/googlelogo/1x/googlelogo_color_272x92dp.png';
declare variable $img:bad-jpg := 'https://commons.wikimedia.org/wiki/File:Felis_silvestris_silvestris.jpg';
declare variable $img:good-jpg := 'https://i.imgur.com/AD5NOgEm.jpg';
declare variable $img:bmp := 'http://www.hlevkin.com/TestImages/baboon.bmp';
declare variable $img:gif := 'https://www-eng-x.llnl.gov/documents/a_image.gif';

(:~
 : for supported fileytypes in Java8
 :
 : @see https://docs.oracle.com/javase/8/docs/api/index.html?javax/imageio/ImageIO.html
 : :)
declare
%test:setUp
    function img:setup() {
    ( xmldb:store(
        $img:testCol,
        'image.png',
        hc:send-request(<hc:request href="{$img:png}" method="get"/>)[2],
        'image/png'),
    xmldb:store(
        $img:testCol,
        'CMYK.jpg',
        hc:send-request(<hc:request href="{$img:bad-jpg}" method="get"/>)[2],
        'image/jpeg'),
    xmldb:store(
        $img:testCol,
        'RGB.jpg',
        hc:send-request(<hc:request href="{$img:good-jpg}" method="get"/>)[2],
        'image/jpeg'),
    xmldb:store(
        $img:testCol,
        'image.bmp',
        hc:send-request(<hc:request href="{$img:bmp}" method="get"/>)[2],
        'image/bmp'),
    xmldb:store(
        $img:testCol,
        'image.gif',
        hc:send-request(<hc:request href="{$img:gif}" method="get"/>)[2],
        'image/gif')
    )
};


declare
    %test:tearDown
    function img:teardown () {
        xmldb:remove($img:testCol)
};

(:~
 : the library can't handle CMYK jpgs hence the empty sequence,
 : if it could it should be 519 wide
 :
 : @see https://stackoverflow.com/questions/2408613/unable-to-read-jpeg-image-using-imageio-readfile-file#16149142
 :)
declare
    %test:name('get image width for supported filetypes')
    %test:assumeIntenetAccess("https://www.google.com")
    %test:arg('file', '/db/test_image/image.png')
    %test:assertEquals (272)
    %test:arg('file', '/db/test_image/CMYK.jpg')
    %test:assertEmpty
    %test:arg('file', '/db/test_image/RGB.jpg')
    %test:assertEquals (320)
    %test:arg('file', '/db/test_image/image.bmp')
    %test:assertEquals (500)
    %test:arg('file', '/db/test_image/image.gif')
    %test:assertEquals (256)
    function img:width ($file as xs:string*) as item()? {
        let $someIMG := util:binary-doc($file)
        return
            if (util:binary-doc-available($file))
            then (image:get-width($someIMG))
            else (error(QName('http://exist-db.org/xquery/test/image', 'missing-binary'), substring-after($file, '.') || ' binary unavailable'))
};

declare
    %test:name('get image height for supported filetypes')
    %test:assumeIntenetAccess("https://www.google.com")
    %test:arg('file', '/db/test_image/image.png')
    %test:assertEquals (92)
    %test:arg('file', '/db/test_image/CMYK.jpg')
    %test:assertEmpty
    %test:arg('file', '/db/test_image/RGB.jpg')
    %test:assertEquals (240)
    %test:arg('file', '/db/test_image/image.bmp')
    %test:assertEquals (480)
    %test:arg('file', '/db/test_image/image.gif')
    %test:assertEquals (24)
    function img:height ($file as xs:string*) as item()? {
        let $someIMG := util:binary-doc($file)
        return
            if (util:binary-doc-available($file))
            then (image:get-height($someIMG))
            else (error(QName('http://exist-db.org/xquery/test/image', 'missing-binary'), substring-after($file, '.') || ' binary unavailable'))
};

declare
    %test:name('scale images to default width')
    %test:assumeIntenetAccess("https://www.google.com")
    %test:args('/db/test_image/image.png', 'image/png')
    %test:assertEquals (100)
    %test:args('/db/test_image/CMYK.jpg', 'image/jpeg')
    %test:assertError('.')
    %test:args('/db/test_image/RGB.jpg', 'image/jpeg')
    %test:assertEquals (100)
    %test:args('/db/test_image/image.bmp', 'image/bmp')
    %test:assertEquals (100)
    %test:args('/db/test_image/image.gif', 'image/gif')
    %test:assertEquals (100)
    function img:scale ($file as xs:string*, $mime as xs:string) as item()? {
        let $testScaled := xmldb:create-collection($img:testCol, '/scaled')
        let $someIMG := util:binary-doc($file)
        let $ending := substring-after($file, '.')
        let $scaled := xmldb:store($testScaled, 'scaled.' || $ending ,image:scale($someIMG, (), $mime))

        return
            if (util:binary-doc-available($file))
            then (image:get-width(util:binary-doc($testScaled || '/scaled.' || $ending)))
            else (error(QName('http://exist-db.org/xquery/test/image', 'missing-binary'), substring-after($file, '.') || ' binary unavailable'))
};

(: TODO the empty sequence does not result in the use of default dimensions :)
declare
    %test:name('crop images to specified  dimmension')
    %test:assumeIntenetAccess("https://www.google.com")
    %test:args('/db/test_image/image.png', 20,'image/png')
    %test:assertEquals (20)
    %test:args('/db/test_image/CMYK.jpg', 50,'image/jpeg')
    %test:assertError('.')
    %test:args('/db/test_image/RGB.jpg', 50, 'image/jpeg')
    %test:assertEquals (50)
    %test:args('/db/test_image/image.bmp', 100, 'image/bmp')
    %test:assertEquals (100)
    %test:args('/db/test_image/image.gif', 20, 'image/gif')
    %test:assertEquals (20)
    function img:crop ($file as xs:string*, $height as xs:integer, $mime as xs:string) as item()? {
        let $testCrop := xmldb:create-collection($img:testCol, '/cropped')
        let $someIMG := util:binary-doc($file)
        let $ending := substring-after($file, '.')
        let $scaled := xmldb:store($testCrop, 'cropped.' || $ending ,image:crop($someIMG, (0,0,50,$height), $mime))

        return
            if (util:binary-doc-available($file))
            then (image:get-height(util:binary-doc($testCrop || '/cropped.' || $ending)))
            else (error(QName('http://exist-db.org/xquery/test/image', 'missing-binary'), substring-after($file, '.') || ' binary unavailable'))
};

(: TODO test image:thumbnail :)
(: TODO order of dimensions should be widthxheight, we consistently do it the other way around :)
(: TODO scaling and cropping can't deal with PNG's alpha channels :)
