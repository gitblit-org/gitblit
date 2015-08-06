# How to configure fail2ban for Gitblit-SSH

This procedure is based on a Debian installation of fail2ban.

1. Create a new filter file `gitblit.conf`. Here an example:

	[Definition]
	failregex = could not authenticate .*? \(/<HOST>:[0-9]*\) for SSH using the supplied password$
	ignoreregex =

2. Edit `jail.conf`; for example:

	[gitblit]
	enabled = true
	port = 22
	protocol = tcp
	filter = gitblit
	logpath = /var/log/gitblit.log

Restart fail2ban.