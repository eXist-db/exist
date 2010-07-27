import module namespace http = "http://www.expath.org/mod/http-client" at "/db/scripts/http-client/http-client.xq";
declare namespace rb="http://kjc-fs1.kjc.uni-heidelberg.de/refbase"; 

(: credentials :)
declare variable $scope  := doc('/db/scripts/refbase/conf.xml');
declare variable $repository-name as xs:string := $scope/repositroy/name/string();
declare variable $database as xs:string := $scope/repositroy/driver/database/string();
declare variable $repository := concat($repository-name,"?repository=",$database,"&amp;");
declare variable $username as xs:string := $scope/repositroy/driver/user/string();
declare variable $password as xs:string := $scope/repositroy/driver/password/string();
declare variable $collection := 'xmldb:exist:///db/mods/refbase/';
declare variable $file-name := 'refbase-metadata.xml';

(: the transform rule :)
declare function local:transform-serials($doc as document-node()) as document-node() {
 document {
   <modsCollection>
   {
     for $serial in $doc//rb:repository/sql:result/sql:row/sql:serial/string()
       return  local:transform-entries(local:get-in($serial))
   }
   </modsCollection>
 }
};

(:author (; nachnanem,vorname) title<titleInfo:title> year<originInfo:dateIssued> publisher<originInfo:c> place (;) <place:placeTerm>  isbn  <identifier type="isbn"> cluster_publication(note type="cp") :)
declare function local:transform-entries($doc as document-node()) as document-node() {
 document {
   
     for $serial in $doc//rb:repository/rb:id/sql:result/sql:row
      return  
        <mods xmlns="http://www.loc.gov/mods/v3" version="3.4" ID="{$serial/sql:serial/string()}">
        <typeOfResource>text</typeOfResource> 
        <titleInfo><title>{$serial/sql:title/string()}</title></titleInfo>
       
        {
        let $names := fn:tokenize($serial/sql:author/string(),';')
        for $name in $names
            return
             <name type="personal">
            <namePart type="given">{$name}</namePart>
        <role><roleTerm authority="marcrelator" type="text">author</roleTerm></role>
        </name>
            
        }
   (:use : retrieve mods functions , if avavailable:)   
        <originInfo><dateIssued>{$serial/sql:year/string()}</dateIssued></originInfo>
        <originInfo><publisher>{$serial/sql:publisher/string()}</publisher></originInfo>
        <place><placeTerm>{$serial/sql:place/string()}</placeTerm></place>
        <identifier type="isbn">{$serial/sql:isbn/string()}</identifier>
        
   </mods>
   }
  
 
};


(: retrieve the input doc :)
declare function local:get-in($serial) as document-node() {
let $serial_id := concat('serial=',$serial)
 let $req := <http:request href="{$repository}{$serial_id}"
                           method="get"
                           username="{ $username }"
                           password="{ $password }"
                           auth-method="basic"
                           send-authorization="true"/>
   return
     (: error checking is left as an exercise :)
     http:send-request($req)[2]
};

(: save the document to database:)
declare function local:save-file($doc as document-node()) {
  let $login := xmldb:login($collection, $username, $password)
  let $store-return-status := xmldb:store($collection, $file-name, $doc)
  return 1
};


(: retrieve a doc, transform it and upload the result :) 

let $serial  :=  xs:string(request:get-parameter('serial', ''))
return
<modsCollection>
{
if (not($serial))
 then (
  (:local:transform-serials(local:get-in($serial)) :)
  system:as-user('admin','SP1WS22-cb',local:save-file(local:transform-serials(local:get-in($serial))))
 )
 else (
  local:transform-entries(local:get-in($serial))
 )
 
}
</modsCollection>

