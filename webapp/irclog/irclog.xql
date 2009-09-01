xquery version "1.0";
(: $Id$ :)

declare namespace irc="http://exist-db.org/xquery/irclog";
declare namespace request="http://exist-db.org/xquery/request";

declare function irc:display-page($channel as xs:string) as element() {
    util:declare-option("exist:serialize", "media-type=text/html method=xhtml"),
    <html>
        <head>
            <title>IRC Log</title>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
            <link type="text/css" href="styles/irclog.css" rel="stylesheet"/>
            <script language="Javascript" type="text/javascript" src="scripts/prototype.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/behaviour.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/calendar.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/calendar-en.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/calendar-setup.js"/>
            <script language="Javascript" type="text/javascript" src="scripts/irclog.js"/>
        </head>
        <body>
            <div id="header">
                <ul id="menu">
                    <li><a href="../index.xml">Home</a></li>
                    <li><a href="../download.xml">Download</a></li>
                    <li><a href="http://atomic.exist-db.org">Wiki</a></li>
                    <li><a href="../examples.xml">Demo</a></li>
                </ul>
                <h1>IRC Log for Channel {$channel}</h1>
            </div>
            
            <div id="content">
                <div id="start-chat">Chat in this channel: <a href="irc://irc.freenode.net/existdb">IRC</a> , <a href="http://webchat.freenode.net/?randomnick=1&amp;channels=existdb&amp;prompt=1">HTML</a>
                </div>
                <div id="query-panel">
                    <input type="text" id="query"/>
                    <button type="button" id="send-query">Send</button>
                </div>
                <div id="query-result">
                    <a href="#" id="query-close">Close</a>
                    <h3>Query Results</h3>
                    <div id="query-output"/>
                </div>
                <div id="errors"></div>
                <div id="output">
                    <div id="navbar">
                        <a id="previous" href="#">&lt;&lt;</a>
                        <a id="next" href="#">&gt;&gt;</a>
                        <div id="current">
                            <input readonly="readonly" type="text" id="current-date"/>
                            <button id="set-date" type="button">Change Date</button>
                            <label>Refresh:</label>
                            <select id="refresh">
                                <option>off</option>
                                <option value="1" selected="selected">1 minute</option>
                                <option value="2">2 minutes</option>
                                <option value="5">5 minutes</option>
                                <option value="10">10 minutes</option>
                            </select>
                        </div>
                    </div>
                    <div id="log-output"/>
                </div>
            </div>
        </body>
    </html>
};

declare function irc:format-number($num as xs:integer) as xs:string {
    if ($num lt 10) then
        concat("0", $num)
    else
        xs:string($num)
};

declare function irc:format-time($time as xs:time) as xs:string {
    concat(
        irc:format-number(hours-from-time($time)), ':',
        irc:format-number(minutes-from-time($time)), ':',
        irc:format-number(seconds-from-time($time))
    )
};

declare function irc:display-event($event as element()) as element() {
    if ($event instance of element(message)) then
        <tr>
            <td class="time">{irc:format-time($event/@time)}</td>
            <td class="nick">{xs:string($event/@nick)}</td>
            <td class="message">
            {
                let $cb := util:function(xs:QName("irc:highlight"), 3)
                for $node in $event/node()
                return
                	if ($node instance of text()) then
                    	text:highlight-matches($node, $cb, ())
                    else
                    	$node
            }
            </td>
        </tr>
    else if ($event instance of element(join)) then
        <tr>
            <td colspan="2" class="time">{irc:format-time($event/@time)}</td>
            <td class="action">{xs:string($event/@nick)} has joined the channel</td>
        </tr>
    else if ($event instance of element(part)) then
        <tr>
            <td colspan="2" class="time">{irc:format-time($event/@time)}</td>
            <td class="action">{xs:string($event/@nick)} has left the channel.</td>
        </tr>
    else
        ()
};

declare function irc:display-date($date as xs:date, $query as xs:string?) as element()* {
    util:declare-option("exist:serialize", "media-type=text/xml omit-xml-declaration=no"),
    let $log := 
        if ($query) then
            /xlog[@date = $date][message &= $query]
        else
            /xlog[@date = $date]
    return
        <table>
        {
            for $event in $log/*
            return
                irc:display-event($event)
        }
        </table>
};

declare function irc:highlight($term as xs:string, $node as text(), $args as item()+) as element() {
    <span class="hi">{$term}</span>
};

declare function irc:query($query as xs:string) as element() {
    util:declare-option("exist:serialize", "media-type=text/xml omit-xml-declaration=no"),
    let $cb := util:function(xs:QName('irc:highlight'), 3)
    let $hits := /xlog/message[. &= $query]
    return
        <table>
        {
            for $event in $hits
            let $date := xs:string($event/parent::xlog/@date)
            return
                <tr>
                    <td class="time"><span class="date">{$date}</span> |
                        <span class="time">{irc:format-time($event/@time)}</span></td>
                    <td class="nick">{xs:string($event/@nick)}</td>
                    <td class="message">
                        <a href="#" onclick="showQueryResult('{$date}', '{$query}')">
                            {text:kwic-display($event/text(), 80, $cb, ())}
                        </a>
                    </td>
                </tr>
        }
        </table>
};

declare function irc:events-since($date as xs:date, $timestamp as xs:time) {
    util:log("DEBUG", ("Time: ", $timestamp, " Date: ", $date)),
    util:declare-option("exist:serialize", "media-type=text/xml omit-xml-declaration=no"),
    <table>
    {
        for $event in /xlog[@date = $date]/*[@time > $timestamp]
        return
            irc:display-event($event)
    }
    </table>
};

let $channel := request:get-parameter("channel", "#existdb")
let $date := request:get-parameter("date", ())
let $query := request:get-parameter("query", ())
let $timestamp := request:get-parameter("since", ())
return
    if ($timestamp) then
        irc:events-since($date, $timestamp)
    else if ($date) then
        irc:display-date($date, $query)
    else if ($query) then
        irc:query($query)
    else
        irc:display-page($channel)
