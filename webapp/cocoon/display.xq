xquery version "1.0";
(: $Id: display.xq 6434 2007-08-28 18:59:23Z ellefj $ :)

module namespace display="http://exist-db.org/biblio/display";

import module namespace config="http://exist-db.org/modules/mods-config" at "config.xqm";

declare function display:record-short($num as xs:int, 
    $rec as element(), $expanded as xs:boolean) as element() {
    <tr class="record-short" id="r_{$num}">
        <td><input type="checkbox" name="r" value="{$num}"/></td>
        <td class="names">{config:display-names($rec)}</td>
        <td class="year">{config:display-year($rec)}</td> 
        <td>{config:display-titles($rec)}</td>
        <td>
        {
            if ($expanded) then
                <a id="a_{$num}" href="javascript:hideDetails('{$num}')">
                    <img src="images/up.png" border="0"/>
                </a>
            else
                <a id="a_{$num}" href="javascript:loadDetails('{$num}')">
                    <img src="images/down.png" border="0"/>
                </a>
        }
        </td>
    </tr>
};

declare function display:record-full-preload($num as xs:int, $rec as element(),
    $visible as xs:boolean) as element() {
    <tr class="{if($visible) then () else 'hidden'}" id="f_{$num}">
        <td colspan="5">{config:display-record-full($rec)}</td>
    </tr>
};

declare function display:navigation($hits as xs:int, $start as xs:int, $next as xs:int,
    $max as xs:int, $preload as xs:boolean) as element() {
    <div id="navigation">
        <h3>Showing hits {$start} to {$next - 1} of {$hits}</h3>
            <ul>
                <li>
                {
                    if ($preload) then
                        <a href="javascript:expandAll({$start},{$next})">
                            <img src="images/down.png" border="0"/>
                            Expand all
                        </a>
                    else
                        <a href="?start={$start}&amp;howmany={$max}&amp;expand">
                            <img src="images/down.png" border="0"/>
                            Expand all
                        </a>
                }
                </li>
                <li>
                    <a href="javascript:collapseAll({$start},{$next})">
                        <img src="images/up.png" border="0"/>
                        Collapse all
                    </a>
                </li>
                <li>
                    <label for="howmany">Display: </label>
                    <select name="howmany" onChange="form.submit()">
                    {
                        for $i in (10, 50, 100)
                        return
                            element option {
                                if ($i eq $max) then
                                    attribute selected { "true" }
                                else
                                    (),
                                $i
                            }
                    }
                    </select>
                </li>
                <li>
                    <a href="javascript:toggleCheckboxes()">Mark all</a>
                </li>
            </ul>
            <ul>
                <li>
                    <input type="submit" name="action" value="Remove"/></li>
                <li>
                    <input type="submit" onClick="exportData()" name="action" value="Export"/>
                    <label for="format">Format: </label>
                    <select id="format" name="format">
                        <option>MODS</option>
                    </select>
                </li>
            </ul>
            {
                if ($start > 1) then
                    <a id="link-prev" href="?start={$start - $max}&amp;howmany={$max}">
                        &lt;&lt; previous
                    </a>
                else ()
            }
            {
                if ($next <= $hits) then
                    <a id="link-next" href="?start={$next}&amp;howmany={$max}">
                        more &gt;&gt;
                    </a>
                else
                    ()
            }
            <input type="hidden" name="start" value="{$start}"/>
    </div>
};