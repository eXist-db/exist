xquery version "1.0";

for $speech in //SPEECH[LINE &= 'love'] 
order by $speech/SPEAKER, $speech/LINE[1]
return
	$speech
