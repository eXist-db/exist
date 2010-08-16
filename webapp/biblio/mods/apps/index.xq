xquery version "1.0";

import module namespace style = "http://exist-db.org/mods-style" at "../modules/style.xqm";
(: document namespaces declarations :)
declare namespace xrx="http://code.google.com/p/xrx";

let $title := 'Library Management System XRX Demo Applications'

(: if you do not install this in /db/org/library you must change this line :)
let $install-base-collection := '/db/org/library'
let $apps-base := concat($install-base-collection, '/apps')
let $image-base := concat($install-base-collection, '/resources/images')

(: by default we will use the icons view :)
let $view-code := request:get-parameter('view-code', 'icons')

(: by default we will sort by the application name :)
let $sort := request:get-parameter('sort', 'app-name')
let $debug := xs:boolean(request:get-parameter('debug', false()))

(: you can change the number of images per row but the default is six :)
let $images-per-row := xs:positiveInteger(request:get-parameter('images-per-row', 5))


let $apps :=
    for $app-id in xmldb:get-child-collections($apps-base)
    let $app-info-file := concat($apps-base, '/', $app-id, '/app-info.xml')
    let $app-info := doc($app-info-file)/xrx:app-info
    let $sort-priority := number($app-info/xrx:main-menu-order/text())
    order by $app-id
    return $app-info
      
let $app-count := count($apps)

let $apps-info-file := concat($apps-base, '/apps-info.xml')
let $classifiers := doc($apps-info-file)//xrx:app-classifier

let $content := 
    <div class="content">
    <p>
        (View apps in <a href="?view-code=classified">Icons</a>,
        <a href="?view-code=list">List</a>, or
        <a href="?view-code=details">Details</a> mode.  
        {$app-count} apps, 
        {$images-per-row} per row.)
    </p>
        {   
        if ($view-code = 'icons') then 

            <table>{
            (: the number of rows to display in the table :)
            let $rows-count := xs:integer(ceiling($app-count div $images-per-row))
            for $row  in (1 to $rows-count)
            return
                <tr>{
                    for $col in (1 to $images-per-row)
                    let $n := ($row - 1) * $images-per-row + $col               
                    return
                        if ($n <= $app-count) then 
                            let $app := $apps[$n]
                            let $app-name := $app/xrx:app-name/text()
                            let $app-title := normalize-space($app/xrx:app-description-text/text())
                            let $app-base := concat($install-base-collection, '/apps/', $app/xrx:app-id/text())
                            let $app-home := concat($app-base, '/index.xq')
                            let $custom-image:= $app/xrx:app-icon-path/text()
                            
                            (: look for an application specific icon or use a site-wide default application icon. :)
                            let $image :=
                                if (string-length($custom-image) > 0) then 
                                    concat($app-base, '/', $custom-image)
                                else 
                                    concat('/rest', $image-base, '/app-icon.png')
                            return
                                <td align="center" class="app">
                                    <a href="/rest{$app-home}" title="{$app-title}">
                                        <img src="{$image}" height="60px"/>
                                        <br/>
                                        <span class="app-name">{$app-name}</span>
                                    </a>
                                    {if ($debug) then ($app/xrx:app-id/text()) else ()}
                                    {if ($debug) then ($app/xrx:main-menu-order/text()) else ()}
                                </td>
                        else 
                            <td/>
                }</tr>
            }</table>

        else if ($view-code = 'list') then 

            <div class="app-list">{
                for $app in $apps
                let $app-name := $app/xrx:app-name/text()
                let $app-base := concat($apps-base, '/', $app/xrx:app-id/text())
                let $app-home := concat($app-base, '/')
                return
                    <div>
                        <a href="{$app-home}/index.xq">{$app-name}</a>
                    </div>
            }</div>

        else if ($view-code = 'details') then 

            <table class="details">
                <thead>
                    <tr>
                        <th>App Name</th>
                        <th>Description</th>
                        <th>Version</th>
                    </tr>
                </thead>
                {
                for $app in $apps
                let $app-name := $app/xrx:app-name/text()
                let $app-base := concat($apps-base, '/', $app/xrx:app-id/text())
                let $app-home := concat($app-base, '/')
                order by $app-name
                return
                    <tr class="details-row">
                        <td><a href="/rest{$app-home}index.xq">{$app/xrx:app-name/text()}</a></td>
                        <td>{$app/xrx:app-description-text/text()}</td>
                        <td>{$app/xrx:app-version-id/text()}</td>
                    </tr>
                }
            </table>

        else if ($view-code = 'classified') then
            for $classifier in $classifiers
            order by $classifier/@order
            return
                <div class="app-class">
                    <br/>
                    <h3>{$classifier/text()}</h3>
                    <table>
                        <tr>{
                        for $app in $apps[xrx:classifier/text() = $classifier/@id]
                            let $app-id := $app/xrx:app-id/text()
                            let $app-name := $app/xrx:app-name/text()
                            let $app-base := concat($style:web-path-to-site, '/apps/', $app/xrx:app-id/text())
                            let $app-home := concat($app-base, '/')
                            let $custom-image:= $app/xrx:app-icon-path/text()
                            let $image :=
                                if (string-length($custom-image) > 0)
                                then concat($app-id, '/', $custom-image)
                                else concat($app-base, '/images/app-icon.png')
                            order by $app-name
                            return
                                <td class="app" width="100px" align="center">
                                    <a href="{$app-id}/index.xq">
                                    <img src="{$image}" height="60px" width="60px"/>
                                    <br/>
                                    {$app/xrx:app-name/text()}
                                    </a>            
                                </td>
                        }</tr>
                    </table>
                </div>
        else ('Error: Unknown View Mode.  Must be list, icons or details.')
        }

    </div>
return
    style:assemble-page($title, $content)
