xquery version "3.0";

module namespace z="http://exist-db.org/testsuite/zips";

declare namespace util = "http://exist-db.org/xquery/util";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace compression="http://exist-db.org/xquery/compression";


declare variable $z:collection-name := "unzip-test";
declare variable $z:collection := "/db/" || $z:collection-name;


declare variable $z:myFile-name := "!#$%()*+,-.:;=?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_abcdefghijklmnopqrstuvwxyz{}~ ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿αßΓπΣσµτΦΘΩδ∞φε.xml";
declare variable $z:myFile-serialized := "<file/>";

(: declare UTF8 encoded binary :)
declare variable $z:myStaticUTF8ContentBase64 := xs:base64Binary("UEsDBBQACAgIAOBYl0UAAAAAAAAAAAAAAADCAAAAISMkJSgpKissLS46Oz0/QEFCQ0RFRkdISUpLTE1OT1BRUlNUVVZXWFlaW11eX2FiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e31+IMOHw7zDqcOiw6TDoMOlw6fDqsOrw6jDr8Ouw6zDhMOFw4nDpsOGw7TDtsOyw7vDucO/w5bDnMKiwqPCpeKCp8aSw6HDrcOzw7rDscORwqrCusK/zrHDn86Tz4DOo8+DwrXPhM6mzpjOqc604oiez4bOtS54bWyzsa/IzVEoSy0qzszPs1Uy1DNQUkjNS85PycxLt1UKDXHTtVCyt+OyScvMSdW3AwBQSwcIwWbL3zAAAAAuAAAAUEsBAhQAFAAICAgA4FiXRcFmy98wAAAALgAAAMIAAAAAAAAAAAAAAAAAAAAAACEjJCUoKSorLC0uOjs9P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltdXl9hYmNkZWZnaGlqa2xtbm9wcXJzdHV2d3h5ent9fiDDh8O8w6nDosOkw6DDpcOnw6rDq8Oow6/DrsOsw4TDhcOJw6bDhsO0w7bDssO7w7nDv8OWw5zCosKjwqXigqfGksOhw63Ds8O6w7HDkcKqwrrCv86xw5/Ok8+AzqPPg8K1z4TOps6YzqnOtOKIns+GzrUueG1sUEsFBgAAAAABAAEA8AAAACABAAAAAA==");



declare
    %test:user("guest", "guest")
	%test:assertTrue
function z:fnZipUtf8Content() {
  let $z:myZipEntries := (<entry name="{$z:myFile-name}" type="xml">{$z:myFile-serialized}</entry>),
      $z:myZipContentUTF8Base64 := compression:zip($z:myZipEntries, true(), "", "UTF8")
  return
    ($z:myZipContentUTF8Base64 eq  $z:myStaticUTF8ContentBase64)
};