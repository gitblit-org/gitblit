#!/bin/sh
# SET HOSTNAME to the server's hostname
HOSTNAME=localhost
rm keystore
java -cp gitblit.jar:$PWD/ext/* com.gitblit.MakeCertificate --hostname $HOSTNAME --subject "CN=$HOSTNAME, OU=Gitblit, O=Gitblit, L=Some Town, ST=Some State, C=US"
