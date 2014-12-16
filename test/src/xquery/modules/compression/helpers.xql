xquery version "3.0";

import module namespace compression="http://exist-db.org/xquery/compression";
declare namespace util = "http://exist-db.org/xquery/util";

(: Upload test data in /db/data :)

declare function local:entry-data($path as xs:anyURI, $type as xs:string, $data as item()?, $param as item()*) as item()?
{
    (: TODO - Replace this code with your implementation e.g. you may want to store the data :)

	<entry>
		<path>{$path}</path>
		<type>{$type}</type>
		<data>{$data}</data>
	</entry>
};

declare function local:entry-filter($path as xs:anyURI, $type as xs:string, $param as item()*) as xs:boolean
{
    (: TODO - Replace this code with your implementation :)

	true()
};

(:let :)
(:  $local:collection-name := "unzip-test",:)
(:  $local:collection := "/db/" || $local:collection-name,:)
(:  $local:myFile-name := "myFile.xml",:)
(:  $local:myFile-serialized := "<file/>",:)
(:  $local:remove := xmldb:remove($local:collection),:)
(:  $local:coll := xmldb:create-collection("/db", $local:collection-name),:)
(:  $local:chmod := sm:chmod(xs:anyURI($local:coll), "rwxrwxrwx"),:)
(:  $local:store := xmldb:store($local:collection, $local:myFile-name, util:parse($local:myFile-serialized)),  :)
(:  $local:myZipEntries := (<entry name="{$local:myFile-name}" type="uri">{$local:collection}/{$local:myFile-name}</entry>),:)
(:  $local:myZipContentBase64 := compression:zip($local:myZipEntries, true(), $local:collection),:)
(:  $local:myZip := xmldb:store($local:collection, "myZipUnicode.zip", $local:myZipContentBase64),:)
(:  $local:myZip2 := xmldb:store($local:collection, "myZip2.zip", xs:base64Binary("UEsDBBQACAgIAKm0h0UAAAAAAAAAAAAAAAAKAAAAbXlGaWxlLnhtbLOxr8jNUShLLSrOzM+zVTLUM1BSSM1Lzk/JzEu3VQoNcdO1ULK347JJy8xJ1bcDAFBLBwjBZsvfMAAAAC4AAABQSwECFAAUAAgICACptIdFwWbL3zAAAAAuAAAACgAAAAAAAAAAAAAAAAAAAAAAbXlGaWxlLnhtbFBLBQYAAAAAAQABADgAAABoAAAAAAA=")),:)
(:  $local:myZipEntries2 := (<entry name="ööööööö" type="uri">{$local:collection}/{$local:myFile-name}</entry>),:)
(:  $local:myZipContentBase642 := compression:zip($local:myZipEntries2, true(), $local:collection, "Cp437"), :)
(:  $local:myZip := xmldb:store($local:collection, "myZipCp437.zip", $local:myZipContentBase642),:)
(:  $zip := util:binary-doc("/db/data/O01-20140207164738089.20140206-020.pre.press.request.broken.zip") :)
(:return:)
(:    <res>{compression:unzip($zip, util:function(xs:QName("local:entry-filter"), 3), (), util:function(xs:QName("local:entry-data"), 4), (), "Cp437")}</res>:)
    
    
(: 

special  NBSP / ' "
	
 !"#$%&'()*+,-.:;<=>?@
 ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`
 abcdefghijklmnopqrstuvwxyz{|}~
 ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑ
 ªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀
αßΓπΣσµτΦΘΩδ∞φε∩
≡±≥≤⌠⌡÷≈°∙·√ⁿ²■

:)    
    
    
    
let 
  $local:collection-name := "unzip-test",
  $local:collection := "/db/" || $local:collection-name,
  $local:myFile-name := xmldb:encode("!""#$%'()*+,-.:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~ ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■.xml"),
  $local:myFile-serialized := "<file/>",
  $local:coll := xmldb:create-collection("/db", $local:collection-name),
  $local:chmod := sm:chmod(xs:anyURI($local:coll), "rwxrwxrwx"),
  $local:store := xmldb:store($local:collection, $local:myFile-name, util:parse($local:myFile-serialized)),  
  $local:myZipEntries := (<entry name="{$local:myFile-name}" type="uri">{$local:collection}/{$local:myFile-name}</entry>),
  $local:myZipContentUTF8Base64 := compression:zip($local:myZipEntries, true(), $local:collection, "UTF8"),
  $local:myZipContentCp437Base64 := compression:zip($local:myZipEntries, true(), $local:collection, "Cp437")
return
  <res>
      <unicode>{$local:myZipContentUTF8Base64}</unicode>
      <cp437>{$local:myZipContentCp437Base64}</cp437>
  </res>
    