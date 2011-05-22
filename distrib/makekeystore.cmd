@del keystore
@java -cp gitblit.jar;"%CD%\ext\*" com.gitblit.MakeCertificate --alias localhost  --subject "CN=localhost, OU=Git:Blit, O=Git:Blit, L=Some Town, ST=Some State, C=US"
