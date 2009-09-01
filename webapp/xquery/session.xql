xquery version "1.0";
(: $Id$ :)

declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";

declare function local:show-attribute($name as xs:string, $pos as xs:integer) as element()
{
    let $value := session:get-attribute($name),
        $color := if($pos mod 2 = 0) then "#EEEEEE"
            else "#CCCCCC"
    return
        <tr>
            <td align="left" bgcolor="{$color}">{$pos}</td>
            <td align="left" bgcolor="{$color}">{$name}</td>
            <td align="left" bgcolor="{$color}">{$value}</td>
        </tr>
};

declare function local:add-attribute() as empty()
{
    let $name := request:get-parameter("name", ()),
        $value := request:get-parameter("value", ())
    return
        if ($name) then
            session:set-attribute($name, $value)
        else
            ()
};

local:add-attribute(),
<html>
    <head>
        <title>Session Dump</title>
        <link rel="stylesheet" type="text/css" href="login.css"/>
    </head>

    <body>
        <h1>Session Dump</h1>

        <p>The following attributes are defined within the current HTTP
        session:</p>

        <table border="1" cellpadding="5" cellspacing="0">
            <tr class="top">
                <th></th>
                <th align="left">Name</th>
                <th align="left">Value</th>
            </tr>
            {
                for $param at $pos in session:get-attribute-names() 
                return
                    local:show-attribute($param, $pos)
            }
        </table>

        <form action="{request:get-uri()}">
            <p></p>
            <p>Add an attribute to the session:</p>
            <table border="0" cellpadding="4">
                <tr>
                    <td>Name:</td>
                    <td><input type="text" name="name" size="20"/></td>
                </tr>
                <tr>
                    <td>Value:</td>
                    <td><input type="text" name="value" size="20"/></td>
                </tr>
                <tr><td colspan="2"><input type="submit"/></td></tr>
            </table>
        </form>
    </body>
</html>
