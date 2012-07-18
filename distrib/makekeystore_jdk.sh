#!/bin/sh
# --------------------------------------------------------------------------
# SET HOSTNAME to the server's hostname
# --------------------------------------------------------------------------
HOSTNAME=localhost
rm keystore
keytool -keystore keystore -alias $HOSTNAME -genkey -keyalg RSA -dname "CN=$HOSTNAME, OU=Gitblit, O=Gitblit, L=Some Town, ST=Some State, C=US"