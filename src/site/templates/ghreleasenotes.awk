#! /usr/bin/env awk -f

BEGIN { on=0 ; skip=1 ; block=0 ; section=""}

/^[[:blank:]]*id:/ { relId = $NF }

/r[0-9]+: *{/ { on=1 ; next }
/^[[:blank:]]*}[[:blank:]]*$/ { if (on) {
                                	print "[Full release notes on gitblit.com](http://www.gitblit.com/releases.html#" relId ")"
                                	exit 0
                                }
                              }


on==1 && /^[[:blank:]]*[[:alnum:]]+:[[:blank:]]*(''|~)?$/ {
															if (!block) {
																skip=1
																if (section == "fixes:" || section == "changes:" || section == "additions:") { printf "\n</details>\n"}
																if (section != "") print ""
																if (section == "note:") print "----------"
																section = ""
																if ($NF == "~") next
															}
															else {
																printSection()
																next
															}
															if ($NF == "''") {
																block = !block
															}
														}
on==1 && /^[[:blank:]]*note:/ { skip=0 ; section=$1; print "### Update Note" ; printSingleLineSection() ; next }
on==1 && /^[[:blank:]]*text:/ { skip=0 ; section=$1; printf "\n\n"; printSingleLineSection() ; next }
on==1 && /^[[:blank:]]*security:/ { skip=0 ; section=$1; print "### *Security*" ; next }
on==1 && /^[[:blank:]]*fixes:/ { skip=0 ; section=$1; printf "<details><summary>Fixes</summary>\n\n### Fixes\n" ; next}
on==1 && /^[[:blank:]]*changes:/ { skip=0 ; section=$1; printf "<details><summary>Changes</summary>\n\n### Changes\n" ; next}
on==1 && /^[[:blank:]]*additions:/ { skip=0 ; section=$1; printf "<details><summary>Additions</summary>\n\n### Additions\n" ; next}

on==1 {
 	if ($1 == "''") {
 		block = !block
 		next
 	}
 	if ((block || !skip))  {
 		printSection()
 	}
 }

function printSingleLineSection()
{
	if (NF>1 && $2 != "''" && $2 != "~") {
		if (protect) gsub(/'/, "'\\''")
		for (i=2; i<= NF; i++) printf "%s ", $i
		print ""
	}
}

function printSection()
{
 	if (section != "text:") sub(/[[:blank:]]+/, "")
 	gsub(/pr-/, "PR #")
 	gsub(/issue-/, "issue #")
 	gsub(/commit-/, "commit ")
 	if (protect) gsub(/'/, "'\\''")
 	print $0	
}