@del keystore
@java -cp gitblit.jar;"%CD%\ext\*" com.gitblit.MakeCertificate --alias localhost  --subject "CN=localhost, OU=Gitblit, O=Gitblit, L=Some Town, ST=Some State, C=US"
