#!/bin/bash
# --------------------------------------------------------------------------
# This is for migrating Tickets from one service to another.
#
# usage:
#
#     migrate-tickets.sh <outputservice> <baseFolder>
#
# --------------------------------------------------------------------------

if [ -z $1 ] || [ -z $2 ]; then
    echo "Please specify the output ticket service and your baseFolder!";
    echo "";
    echo "usage:";
    echo "    migrate-tickets <outputservice> <baseFolder>";
    echo "";
    exit 1;
fi

java -cp gitblit.jar:./ext/* com.gitblit.MigrateTickets $1 --baseFolder $2

