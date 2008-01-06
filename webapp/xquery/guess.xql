xquery version "1.0";
(: $Id$ :)

import module namespace request="http://exist-db.org/xquery/request";
import module namespace session="http://exist-db.org/xquery/session";
import module namespace util="http://exist-db.org/xquery/util";

declare function local:random($max as xs:integer) 
as empty()
{
    let $r := ceiling(util:random() * $max) cast as xs:integer
    return (
        session:set-attribute("random", $r),
        session:set-attribute("guesses", 0)
    )
};

declare function local:guess($guess as xs:integer,
$rand as xs:integer) as element()
{
    let $count := session:get-attribute("guesses") + 1
    return (
        session:set-attribute("guesses", $count),
        if ($guess lt $rand) then
            <p>Your number is too small!</p>
        else if ($guess gt $rand) then
            <p>Your number is too large!</p>
        else
            let $newRandom := local:random(100)
            return
                <p>Congratulations! You guessed the right number with
                {$count} tries. Try again!</p>
    )
};

declare function local:main() as node()?
{
    session:create(),
    let $rand := session:get-attribute("random"),
        $guess := xs:integer(request:get-parameter("guess", ()))
    return
		if ($rand) then 
			if ($guess) then
				local:guess($guess, $rand)
			else
				<p>No input!</p>
		else 
		    local:random(100)
};

<html>
    <head><title>Number Guessing</title></head>
    <body>
        <form action="{session:encode-url(request:get-uri())}">
            <table border="0">
                <tr>
                    <th colspan="2">Guess a number</th>
                </tr>
                <tr>
                    <td>Number:</td>
                    <td><input type="text" name="guess" size="3"/></td>
                </tr>
                <tr>
                    <td colspan="2" align="left"><input type="submit"/></td>
                </tr>
            </table> 
        </form>
        { local:main() }
        <p><small>View <a href="guess.xql?_source=yes">source code</a></small></p>
    </body>
</html>
