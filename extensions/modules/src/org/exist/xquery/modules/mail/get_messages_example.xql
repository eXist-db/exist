xquery version "1.0";

(:~
: User: alisterpillow
: Date: 2/09/2014
: Time: 8:39 AM
:)

let $imap-props := 	<properties>
    <property name="mail.debug" 		 value="false"/>
    <property name="mail.store.protocol" value="imap"/>
    <property name="mail.imap.host" 	 value="xxx"/>
    <property name="mail.imap.user" 	 value="xxx"/>
    <property name="mail.imap.password"  value="xxx"/>
</properties>

let $pop-props := <properties>
    <property name="mail.debug" 		 value="false"/>
    <property name="mail.mime.address.strict" value="false" /> (: avoid problems with bad addresses :)
    <property name="mail.store.protocol" value="pop3"/>
    <property name="mail.pop3.host" 	 value="xxx"/>
    <property name="mail.pop3.user" 	 value="xxx"/>
    <property name="mail.pop3.password"  value="xxx"/>
</properties>


let $session 	:= mail:get-mail-session( $pop-props )
let $store   	:= mail:get-mail-store( $session )
let $inbox   	:= mail:get-mail-folder( $store, "INBOX" )
let $msgList 	:= mail:get-message-list( $inbox )

let $terms   	:=  <searchTerm type="and">
    <searchTerm type="flag" flag="seen" value="false"/>
</searchTerm>


(: Be careful when retrieving messages - they can be very large and the process is slow.
:)
let $srchList	:= mail:search-message-list( $inbox, $terms )


let $msgs    	:= mail:get-message-list-as-xml( $srchList, false() )//mail:message
(:let $seen := for $m in $msgs return $m[.//mail:flag/@type eq "seen"]:)
(:let $unseen := $msgs except $seen:)


let $results   	:= mail:get-messages($inbox, $msgs/@number)

let $closet  	:= mail:close-message-list( $srchList )
let $closem  	:= mail:close-message-list( $msgList )
let $closef  	:= mail:close-mail-folder( $inbox, false() ) ()
let $closes  	:= mail:close-mail-store( $store )

return $results

(:  Some messages containing html will cause an error if you attempt to save
    to the database. As an example, an Outlook message, forwarded from Apple Mail,
    can contain <o:p/> elements (which are the paragraph markers from Word).
    For some unknown reason, the namespace is not declared and so an error occurs
    when storing the file. It should be possible to filter out these elements before
    storing in the /db.
:)

(:for $m in $results :)
(:let $file-name := concat("message-",$m/@number,".xml"):)
(:return xmldb:store('/db/test',$file-name, $m):)