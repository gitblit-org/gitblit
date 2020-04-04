#!/bin/bash
# --------------------------------------------------------------------------
# This is for reindexing Tickets with Lucene.
#
# Since the Tickets feature is undergoing massive churn it may be necessary 
# to reindex tickets due to model or index changes.
#
# usage:
#
#     reindex-tickets.sh <baseFolder>
#
# --------------------------------------------------------------------------

if [ -z $1 ] ; then
    echo "Please specify your baseFolder!";
    echo "";
    echo "usage:";
    echo "    reindex-tickets <baseFolder>";
    echo "";
    exit 1;
fi

java -cp gitblit.jar:./ext/* com.gitblit.ReindexTickets --baseFolder $1

