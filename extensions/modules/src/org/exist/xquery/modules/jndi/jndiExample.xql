xquery version "1.0";

declare namespace test = "http://exist-db.org/extensions/jndi/test";

declare namespace jndi="http://exist-db.org/xquery/jndi";
declare namespace util="http://exist-db.org/xquery/util";

(: 
	This XQuery shows examples of how to use the eXist JNDI Directory Access extension functions.  
	
	If you don't know what you are doing with JNDI or LDAP Directories the you shouldn't be playing with this stuff!

	This XQuery was tested successfully against an OpenLDAP 2.4.11 installation running on Ubuntu Linux
:)

(: Constants - Change these to suit your LDAP installation :)

declare variable $test:LDAP-URL					:= "ldap://localhost:389";
declare variable $test:LDAP-USER				:= "cn=admin,dc=exist-db,dc=org";
declare variable $test:LDAP-PSWD				:= "XXXXXXXXX";

declare variable $test:ENTRY-ROOT-CONTEXT		:= "ou=accounts,dc=exist-db,dc=org";
declare variable $test:ENTRY-USER-PASSWORD		:= "{{MD5}}{ util:md5( 'test', true() ) }";


(: Connect to the LDAP server :)

let $contextProps :=  
	<properties>  
		<property name="java.naming.factory.initial" 			value="com.sun.jndi.ldap.LdapCtxFactory"/>  
		<property name="java.naming.provider.url" 				value="{ $test:LDAP-URL }"/>  
		<property name="java.naming.security.authentication" 	value="simple"/>  
		<property name="java.naming.security.principal" 		value="{ $test:LDAP-USER }"/>  
		<property name="java.naming.security.credentials" 		value="{ $test:LDAP-PSWD }"/>  
	</properties> 

let $ctx := jndi:get-dir-context( $contextProps )


(: Create a new Entry :)

let $createAttrs :=
	<attributes>  
		<attribute name="objectClass" 			value="top"/>
		<attribute name="objectClass" 			value="person"/>
		<attribute name="objectClass" 			value="posixAccount"/>
		<attribute name="objectClass" 			value="ravenUser"/>
		<attribute name="uid" 					value="test"/>
		<attribute name="uid" 					value="temp"/>
		<attribute name="cn" 					value="test"/>
		<attribute name="sn" 					value="TestUser"/>
		<attribute name="uidNumber" 			value="5000"/>
		<attribute name="gidNumber" 			value="5000"/>
		<attribute name="ravenRole" 			value="physician"/>
		<attribute name="ravenAdminUser" 		value="false"/>
		<attribute name="ravenAdminSubscriber"  value="false"/>
		<attribute name="homeDirectory" 		value="/var/mail/test"/>
		<attribute name="userPassword" 			value="{ $test:ENTRY-USER-PASSWORD }"/>
	</attributes>
	
	
let $create := jndi:create( $ctx, concat( "uid=temp,", $test:ENTRY-ROOT-CONTEXT ), $createAttrs )
	
let $searchAttrs :=
	<attributes>  
		<attribute name="uid" value="test"/>
	</attributes> 

let $searchc := jndi:search( $ctx, $test:ENTRY-ROOT-CONTEXT, $searchAttrs )


(: Rename the Entry :)

let $rename := jndi:rename( $ctx, concat( "uid=temp,", $test:ENTRY-ROOT-CONTEXT ), concat( "uid=test,", $test:ENTRY-ROOT-CONTEXT ) )

let $searchr := jndi:search( $ctx, $test:ENTRY-ROOT-CONTEXT, "(uid=test)", "subtree" )  (: filter search :)


(: Modify the Entry :)

let $modifyAttrs :=
	<attributes>  
		<attribute name="uid" 					operation="add"			value="temp"/>
		<attribute name="uidNumber" 			operation="replace"		value="666"/>
		<attribute name="gidNumber" 			operation="replace"		value="666"/>
		<attribute name="ravenAdminSubscriber"  operation="replace"		value="true"/>
		<attribute name="userPassword"  		operation="remove" 		value="{ $test:ENTRY-USER-PASSWORD }"/>
	</attributes>
	
let $modify := jndi:modify( $ctx, concat( "uid=test,", $test:ENTRY-ROOT-CONTEXT ), $modifyAttrs )

let $searchm := jndi:search( $ctx, $test:ENTRY-ROOT-CONTEXT, $searchAttrs )


(: Delete the Entry :)

let $delete := jndi:delete( $ctx, concat( "uid=test,", $test:ENTRY-ROOT-CONTEXT ) )

let $searchd := jndi:search( $ctx, $test:ENTRY-ROOT-CONTEXT, "(uid=test)", "subtree" )  (: filter search :)


(: Close connection to the LDAP server :)

let $close := jndi:close-context( $ctx )


(: Return all search results :)

return <searchResults pswd="{ $test:ENTRY-USER-PASSWORD }">
			<afterCreate>{ $searchc }</afterCreate>
			<afterRename>{ $searchr }</afterRename>
			<afterModify>{ $searchm }</afterModify>
			<afterDelete>{ $searchd }</afterDelete>
	   </searchResults>