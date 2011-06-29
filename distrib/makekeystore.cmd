@REM --------------------------------------------------------------------------
@REM Set HOSTNAME to the server's hostname
@REM --------------------------------------------------------------------------
@SET HOSTNAME=localhost
@del keystore
@java -cp gitblit.jar;"%CD%\ext\*" com.gitblit.MakeCertificate --hostname %HOSTNAME% --subject "CN=%HOSTNAME%, OU=Gitblit, O=Gitblit, L=Some Town, ST=Some State, C=US"
