xquery version "1.0";

declare namespace util="http://exist-db.org/xquery/util";
declare namespace mail="http://exist-db.org/xquery/mail";

let $props 		:= 	<properties>
						<property name="mail.debug" 		 value="false"/>
						<property name="mail.store.protocol" value="imap"/>
						<property name="mail.imap.host" 	 value="localhost"/>
						<property name="mail.imap.user" 	 value="user@domain.com"/>
						<property name="mail.imap.password"  value="password"/>
					</properties>
				
let $session 	:= mail:get-mail-session( $props )
let $store   	:= mail:get-mail-store( $session )
let $inbox   	:= mail:get-mail-folder( $store, "INBOX" )
let $msgList 	:= mail:get-message-list( $inbox )

let $msgs    	:= mail:get-message-list-as-xml( $msgList, false() )

let $terms   	:=  <searchTerm type="and">
						<searchTerm type="flag" flag="answered" value="false"/>
						<searchTerm type="flag" flag="seen" value="false"/>
						<searchTerm type="flag" flag="deleted" value="false"/>
					</searchTerm>
				
let $srchList	:= mail:search-message-list( $inbox, $terms )

let $results   	:= mail:get-message-list-as-xml( $srchList, false() )

let $closet  	:= mail:close-message-list( $srchList )
let $closem  	:= mail:close-message-list( $msgList )
let $closef  	:= mail:close-mail-folder( $inbox, false() )
let $closes  	:= mail:close-mail-store( $store )

return $results
                     
                     
                     
