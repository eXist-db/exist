xquery version "1.0";

import module namespace util  = "http://exist-db.org/xquery/util";
import module namespace xmpp  = "http://exist-db.org/xquery/xmpp";
import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare variable $local:listener := util:function(xs:QName("local:store-message"), 3);

declare function local:store-message($chat, $message, $param){
    xmldb:store($param, (), $message),
    xmpp:send-message($chat, "One more, please")
};

let $config     :=  <properties>
                        <property name="xmpp.service" value="gmail.com"/>
                        <!--
                            An additional properties:
                            
                            <property name="xmpp.host" value="localhost"/>
                            <property name="xmpp.port" value="5222"/>
                            
                            <property name="proxy.type" value="http"/>
                            <property name="proxy.host" value="localhost"/>
                            <property name="proxy.port" value="80"/>
                            <property name="proxy.user" value="john"/>
                            <property name="proxy.password" value="doe"/>
                            
                        -->
                    </properties>
let $connection :=  xmpp:get-xmpp-connection($config)
return if (xmpp:connect($connection))
         then if (xmpp:login($connection, "user", "password", "resource"))
                 then let $chat := xmpp:create-chat($connection, "evgeny@stkurier.ru", $local:listener, "/db/tmp")
                      return xmpp:send-message($chat, "Hello! tell me anything, please")
                 else "Not logined!"
         else "Not connected!"
