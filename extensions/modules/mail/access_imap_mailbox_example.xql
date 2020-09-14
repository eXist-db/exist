(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
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
                     
                     
                     
