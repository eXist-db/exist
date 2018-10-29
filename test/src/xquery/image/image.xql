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
declare variable $img:bmp := 'https://www.fileformat.info/format/bmp/sample/3636e6411c7e49a59196e6578876dccc/VENUS.BMP';
declare variable $img:gif := 'http://www.fileformat.info/format/gif/sample/7c2f782d415843e0b4c0980ecac4fa83/WFPC01.GIF';

(:~
 : for supported fileytypes in Java8
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
 : the library can't handle CMYK jpgs
 : hence the error, if it could it should be 519 wide
 :
 : @see https://stackoverflow.com/questions/2408613/unable-to-read-jpeg-image-using-imageio-readfile-file#16149142
 :)
declare
    %test:assumeIntenetAccess("https://www.google.com")
    %test:arg('file', '/db/test_image/image.png')
    %test:assertEquals (272)
    %test:arg('file', '/db/test_image/CMYK.jpg')
    %test:assertError('exerr:ERROR')
    %test:arg('file', '/db/test_image/RGB.jpg')
    %test:assertEquals (320)
    %test:arg('file', '/db/test_image/image.bmp')
    %test:assertEquals (300)
    %test:arg('file', '/db/test_image/image.gif')
    %test:assertEquals (640)
    function img:width ($file as xs:string*) as item() {
        let $someIMG := util:binary-doc($file)
        return
            if (util:binary-doc-available($file))
            then (image:get-width($someIMG))
            else (error(QName('http://exist-db.org/xquery/test/image', 'missing-binary'), substring-after($file, '.') || ' binary unavailable'))
};
