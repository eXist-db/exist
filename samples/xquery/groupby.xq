let $speech := //SPEECH[LINE &= "passion*"]
let $plays := (for $s in $speech return root($s))
for $play in $plays
let $hits := $play//$speech
return
	<play title="{$play/PLAY/TITLE}" hits="{count($hits)}">
		{$hits}
	</play>