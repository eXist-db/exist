xquery version "1.0";

(:
	Print a list of all files and directories in the current 
	directory, ordered by name.
:)

declare namespace file="java:java.io.File";

<files>
	{
		for $f in file:list-files( file:new(".") )
		let $n := file:get-name($f)
		order by $n
		return
			if (file:is-directory($f)) then
				<directory name="{ $n }"/>
			else
				<file name="{ $n }" size="{ file:length($f) }"/>
	}
</files>
