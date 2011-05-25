@del keystore
@keytool -keystore keystore -alias localhost -genkey -keyalg RSA -dname "CN=localhost, OU=Gitblit, O=Gitblit, L=Some Town, ST=Some State, C=US"