declare namespace rb="http://kjc-fs1.kjc.uni-heidelberg.de/refbase"; 
declare namespace sql="http://exist-db.org/xquery/sql"; 
 
declare option exist:serialize "method=xml media-type=text/xml omit-xml-declaration=yes indent=yes";
declare variable $scope  := doc('/db/scripts/refbase/conf.xml');
declare variable $driver as xs:string := $scope/repository/driver/name/string();
declare variable $database as xs:string := $scope/repository/driver/database/string();
declare variable $db-server := $scope/repository/driver/db-server/string();
declare variable $user as xs:string := $scope/repository/driver/user/string();
declare variable $password as xs:string := $scope/repository/driver/password/string();
declare variable $db as xs:string := concat("jdbc:mysql://",concat($db-server,"/",$database),"?characterEncoding=UTF-8");
declare variable $connection := sql:get-connection($driver, $db, $user, $password); 

declare function local:get-repository($q1) 
{
  if ($q1='refbase')
    then
     (:    let $mysql_query := "select serial from refs where serial < 50":) 
      let $mysql_query := "select serial from refs"
      let $result:= sql:execute($connection, $mysql_query, fn:true())
        return $result
  else -2
};

declare function local:get-record($q1,$q2) 
{
 
  let $mysql_query := concat('select * from refs where serial=',$q2,';')
  let $result:= sql:execute($connection, $mysql_query, fn:true())
  return <rb:id>{$result}</rb:id>

};


let $q1 := request:get-parameter('repository', '')
let $q2 := request:get-parameter('serial', '')


return 
<rb:results>
{
if ($q1='')
  then -1
else 
  if ($q2='')
  then
  (
    <rb:repository>{local:get-repository($q1) }</rb:repository>
  )
  else
  (
    <rb:repository>{local:get-record($q1,$q2) }</rb:repository>
  )
}
</rb:results>