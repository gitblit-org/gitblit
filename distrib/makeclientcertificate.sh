#!/bin/sh
java -cp gitblit.jar:$PWD/ext/* com.gitblit.authority.MakeClientCertificate
