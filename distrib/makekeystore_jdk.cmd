@REM --------------------------------------------------------------------------
@REM Set HOSTNAME to the server's hostname
@REM --------------------------------------------------------------------------
@SET HOSTNAME=localhost
@del serverKeyStore.jks
@keytool -keystore serverKeyStore.jks -alias %HOSTNAME% -genkey -keyalg RSA -dname "CN=%HOSTNAME%, OU=Gitblit, O=Gitblit, L=Some Town, ST=Some State, C=US"