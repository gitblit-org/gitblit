@REM --------------------------------------------------------------------------
@REM Set HOSTNAME to the server's hostname
@REM --------------------------------------------------------------------------
@SET HOSTNAME=localhost
@del keystore
@keytool -keystore keystore -alias %HOSTNAME% -genkey -keyalg RSA -dname "CN=%HOSTNAME%, OU=Gitblit, O=Gitblit, L=Some Town, ST=Some State, C=US"