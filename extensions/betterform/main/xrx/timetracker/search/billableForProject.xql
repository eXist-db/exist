xquery version "1.0";
(: $Id: timetracker.xql  2009-10-02 11:52:08Z lars $ :)
declare option exist:serialize "method=xhtml media-type=text/html";
import module namespace request="http://exist-db.org/xquery/request";
import module namespace session="http://exist-db.org/xquery/session";
import module namespace util="http://exist-db.org/xquery/util";


declare function local:match($key, $value, $default) as xs:boolean
{
   if ($value = "")
   then $default
   else $key = $value
};

declare function local:tasks() as node()*
{
    session:create(),
    let $projects          := tokenize(xs:string(request:get-parameter("projects", "")), '\s')
    let $billable          := xs:string(request:get-parameter("billable", ""))
    let $worker            := tokenize(xs:string(request:get-parameter("worker", "")), '\s')

    for $t in collection("/db/timetracking/task")//task
    where local:match($t/project, $projects, true()) (: and
          local:match($t/billable, $billable, true()) and
          local:match($t/who, $worker, true())          :)
    return $t
};



declare function local:main() as node()?
{

	let $selectedTasks     := local:tasks()
	
	let $hours             := if(sum($selectedTasks//duration/@hours) > 0)
	                          then sum($selectedTasks//duration/@hours)
	                          else 0
	let $minutes           := sum($selectedTasks//duration/@minutes)
	let $hoursFromMinutes  := $minutes div 60
	let $remainingMinutes  := $minutes mod 60
    let $totalHours        := $hours + $hoursFromMinutes
    let $totalTime         := concat($totalHours,':',$remainingMinutes)
    let $dailyRate         := 850
    let $hourlyRate        := $dailyRate div 8
    let $days              := $totalHours div 8
    let $netAmountDays     := $days * $dailyRate
    let $netAmountMinutes  := $remainingMinutes * $hourlyRate

	return
		<project days="{$days}"
		         totalTime="{$totalTime}"
		         hours="{$hours}"
                 minutes="{$minutes}"
                 hoursFromMinutes="{$hoursFromMinutes}"
                 remainingMinutes="{$remainingMinutes}"
                 netAmountDays="{$netAmountDays}"
                 netAmountMinutes="{$netAmountMinutes}">
            <report></report>
			<billing>
			    <dailyRate>{$dailyRate}</dailyRate>
			    <hourlyRate>{$hourlyRate}</hourlyRate>
			    <bill>€ {$netAmountDays + $netAmountMinutes}</bill>
			</billing> {
			for $z in $selectedTasks
				let $date := $z/date
				order by $date
				return $z
			}</project>
};

<data>
    <test>
        { local:match("a", "a", true() )}
    </test>
	{ local:tasks() }
</data>
