xquery version "1.0";

declare variable $x { //SPEECH[SPEAKER="HAMLET"] };

for $s in $x
let $t := $s/ancestor::SCENE/TITLE
return
	<scene title="{$t}">
		{$s}
	</scene>
