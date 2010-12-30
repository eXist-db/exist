xquery version "1.0";

if ($exist:path eq '/') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
		<redirect url="index.xml"/>
	</dispatch>

(: 
	jQuery module demo: tags in the jquery namespace are expanded
	by style-jquery.xql
:)
else if ($exist:resource eq 'jquery.xml') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <view>
            <forward url="style-jquery.xql"/>
    	</view>
    </dispatch>

(:
	Error handling: faulty.xql will trigger an XQuery error which
	will be handled by error-handler.xql
:)
else if ($exist:resource eq 'faulty.xql') then
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <view>
            <forward url="style.xql"/>
    	</view>
        <error-handler>
            <forward url="error-handler.xql"/>
            <forward url="style.xql"/>
        </error-handler>
    </dispatch>
    
else
    (: everything else is passed through :)
    <dispatch xmlns="http://exist.sourceforge.net/NS/exist">
        <cache-control cache="yes"/>
    </dispatch>
