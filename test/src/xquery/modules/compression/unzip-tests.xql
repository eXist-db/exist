xquery version "3.0";

module namespace uz="http://exist-db.org/testsuite/unzips";

declare namespace util = "http://exist-db.org/xquery/util";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace compression="http://exist-db.org/xquery/compression";


declare variable $uz:collection-name := "unzip-test";
declare variable $uz:collection := "/db/" || $uz:collection-name;


declare variable $uz:myFile-name := "!#$%()*+,-.:;=?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_abcdefghijklmnopqrstuvwxyz{}~ ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿αßΓπΣσµτΦΘΩδ∞φε.xml";
declare variable $uz:myFile-serialized := "<file/>";

(: declare UTF8 encoded binary :)
declare variable $uz:myStaticUTF8ContentBase64 := xs:base64Binary("UEsDBBQACAgIAOBYl0UAAAAAAAAAAAAAAADCAAAAISMkJSgpKissLS46Oz0/QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW11eX2FiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e31+IMOHw7zDqcOiw6TDoMOlw6fDqsOrw6jDr8Ouw6zDhMOFw4nDpsOGw7TDtsOyw7vDucO/w5bDnMKiwqPCpeKCp8aSw6HDrcOzw7rDscORwqrCusK/zrHDn86Tz4DOo8+DwrXPhM6mzpjOqc604oiez4bOtS54bWyzsa/IzVEoSy0qzszPs1Uy1DNQUkjNS85PycxLt1UKDXHTtVCyt+OyScvMSdW3AwBQSwcIwWbL3zAAAAAuAAAAUEsBAhQAFAAICAgA4FiXRcFmy98wAAAALgAAAMIAAAAAAAAAAAAAAAAAAAAAACEjJCUoKSorLC0uOjs9P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltdXl9hYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ent9fiDDh8O8w6nDosOkw6DDpcOnw6rDq8Oow6/DrsOsw4TDhcOJw6bDhsO0w7bDssO7w7nDv8OWw5zCosKjwqXigqfGksOhw63Ds8O6w7HDkcKqwrrCv86xw5/Ok8+AzqPPg8K1z4TOps6YzqnOtOKIns+GzrUueG1sUEsFBgAAAAABAAEA8AAAACABAAAAAA==");

(: declare cp437 encoded binary :)
declare variable $uz:myStaticCP437ContentBase64 := xs:base64Binary("UEsDBBQACAAIAOBYl0UAAAAAAAAAAAAAAACIAAAAISMkJSgpKissLS46Oz0/QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW11eX2FiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e31+IICBgoOEhYaHiImKi4yNjo+QkZKTlJWWl5iZmpucnZ6foKGio6Slpqeo4OHi4+Tl5ufo6err7O3uLnhtbLOxr8jNUShLLSrOzM+zVTLUM1BSSM1Lzk/JzEu3VQoNcdO1ULK347JJy8xJ1bcDAFBLBwjBZsvfMAAAAC4AAABQSwECFAAUAAgACADgWJdFwWbL3zAAAAAuAAAAiAAAAAAAAAAAAAAAAAAAAAAAISMkJSgpKissLS46Oz0/QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW11eX2FiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e31+IICBgoOEhYaHiImKi4yNjo+QkZKTlJWWl5iZmpucnZ6foKGio6Slpqeo4OHi4+Tl5ufo6err7O3uLnhtbFBLBQYAAAAAAQABALYAAADmAAAAAAA=");


(: declare helper functions :)
declare function local:entry-data($path as xs:anyURI, $type as xs:string, $data as item()?, $param as item()*) as item()?
{
    <entry>
        <path>{$path}</path>
        <type>{$type}</type>
        <data>{$data}</data>
    </entry>
};

(: Process every Zip Collections and Resources  :)
declare function local:entry-filter($path as xs:anyURI, $type as xs:string, $param as item()*) as xs:boolean
{
    true()
};

declare
    %test:user("guest", "guest")
	%test:assertEquals("<entry><path>!#$%()*+,-.:;=?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_abcdefghijklmnopqrstuvwxyz{}~ ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿αßΓπΣσµτΦΘΩδ∞φε.xml</path><type>resource</type><data><file/></data></entry>")
function uz:fnUzipUtf8Content() {
    compression:unzip($uz:myStaticUTF8ContentBase64, util:function(xs:QName("local:entry-filter"), 3), (), util:function(xs:QName("local:entry-data"), 4), (), "UTF8")
};

declare
    %test:user("guest", "guest")
	%test:assertEquals("<entry><path>!#$%()*+,-.:;=?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_abcdefghijklmnopqrstuvwxyz{}~ ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿αßΓπΣσµτΦΘΩδ∞φε.xml</path><type>resource</type><data><file/></data></entry>")
function uz:fnUzipCp437Content() {
    compression:unzip($uz:myStaticCP437ContentBase64, util:function(xs:QName("local:entry-filter"), 3), (), util:function(xs:QName("local:entry-data"), 4), (), "Cp437")
};

declare
    %test:user("guest", "guest")
	%test:assertEquals("<entry><path>!#$%()*+,-.:;=?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_abcdefghijklmnopqrstuvwxyz{}~ ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿αßΓπΣσµτΦΘΩδ∞φε.xml</path><type>resource</type><data><file/></data></entry>")
function uz:fnUzipUtf8ContentWrongEncoding() {
    (: This case is working due to the selected cp437 character in the filename :)
    compression:unzip($uz:myStaticUTF8ContentBase64, util:function(xs:QName("local:entry-filter"), 3), (), util:function(xs:QName("local:entry-data"), 4), (), "Cp437")
};

declare
    %test:user("guest", "guest")
	%test:assertError("(?:MALFORMED)|(?:malformed)")
function uz:fnUzipCp437ContentWrongEncoding() {
    (: This case is not working because the Unicode extended filename table is not present in non unicode encoded Zip :)
    compression:unzip($uz:myStaticCP437ContentBase64, util:function(xs:QName("local:entry-filter"), 3), (), util:function(xs:QName("local:entry-data"), 4), (), "UTF8")
};
