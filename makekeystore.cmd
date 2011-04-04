@del keystore
@keytool -keystore keystore -alias localhost -genkey -keyalg RSA -dname "CN=localhost, OU=Git:Blit, O=Git:Blit, L=Some Town, ST=Some State, C=US"