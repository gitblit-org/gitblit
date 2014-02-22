/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.transport.ssh;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.InvalidKeyException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.Channel;
import org.apache.sshd.common.Cipher;
import org.apache.sshd.common.Compression;
import org.apache.sshd.common.KeyExchange;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.Mac;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.Signature;
import org.apache.sshd.common.cipher.AES128CBC;
import org.apache.sshd.common.cipher.AES192CBC;
import org.apache.sshd.common.cipher.AES256CBC;
import org.apache.sshd.common.cipher.BlowfishCBC;
import org.apache.sshd.common.cipher.TripleDESCBC;
import org.apache.sshd.common.compression.CompressionNone;
import org.apache.sshd.common.mac.HMACMD5;
import org.apache.sshd.common.mac.HMACMD596;
import org.apache.sshd.common.mac.HMACSHA1;
import org.apache.sshd.common.mac.HMACSHA196;
import org.apache.sshd.common.random.BouncyCastleRandom;
import org.apache.sshd.common.random.SingletonRandomFactory;
import org.apache.sshd.common.signature.SignatureDSA;
import org.apache.sshd.common.signature.SignatureRSA;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.FileSystemFactory;
import org.apache.sshd.server.FileSystemView;
import org.apache.sshd.server.ForwardingFilter;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.SshFile;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthPublicKey;
import org.apache.sshd.server.channel.ChannelDirectTcpip;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.kex.DHG1;
import org.apache.sshd.server.kex.DHG14;
import org.apache.sshd.server.keyprovider.PEMGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.SessionFactory;
import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.utils.IdGenerator;
import com.gitblit.utils.StringUtils;

/**
 * Manager for the ssh transport. Roughly analogous to the
 * {@link com.gitblit.git.GitDaemon} class.
 *
 * @author Eric Myhre
 *
 */
public class SshDaemon extends SshServer {

	private final Logger log = LoggerFactory.getLogger(SshDaemon.class);

	/**
	 * 22: IANA assigned port number for ssh. Note that this is a distinct concept
	 * from gitblit's default conf for ssh port -- this "default" is what the git
	 * protocol itself defaults to if it sees and ssh url without a port.
	 */
	public static final int DEFAULT_PORT = 22;

	private static final String HOST_KEY_STORE = "sshKeyStore.pem";

	private InetSocketAddress myAddress;

	private AtomicBoolean run;

	@SuppressWarnings("unused")
    private IGitblit gitblit;

	/**
	 * Construct the Gitblit SSH daemon.
	 *
	 * @param gitblit
	 */
	@Inject
	SshDaemon(IGitblit gitblit, IdGenerator idGenerator, SshCommandFactory factory) {
	    this.gitblit = gitblit;
		IStoredSettings settings = gitblit.getSettings();
		int port = settings.getInteger(Keys.git.sshPort, 0);
		String bindInterface = settings.getString(Keys.git.sshBindInterface, "localhost");

		if (StringUtils.isEmpty(bindInterface)) {
			myAddress = new InetSocketAddress(port);
		} else {
			myAddress = new InetSocketAddress(bindInterface, port);
		}

		setPort(myAddress.getPort());
		setHost(myAddress.getHostName());
		setup();
		setKeyPairProvider(new PEMGeneratorHostKeyProvider(
		    new File(gitblit.getBaseFolder(), HOST_KEY_STORE).getPath()));
		setPublickeyAuthenticator(new SshKeyAuthenticator(gitblit));

		run = new AtomicBoolean(false);
		setCommandFactory(factory);
		setSessionFactory(newSessionFactory(idGenerator));
	}

	SessionFactory newSessionFactory(final IdGenerator idGenerator) {
	  return new SessionFactory() {
        @Override
        protected ServerSession createSession(final IoSession io) throws Exception {
            log.info("connection accepted on " + io);
            if (io.getConfig() instanceof SocketSessionConfig) {
                final SocketSessionConfig c = (SocketSessionConfig) io.getConfig();
                c.setKeepAlive(true);
            }
            ServerSession s = (ServerSession) super.createSession(io);
            SocketAddress peer = io.getRemoteAddress();
            SshSession session = new SshSession(idGenerator.next(), peer);
            s.setAttribute(SshSession.KEY, session);
            io.getCloseFuture().addListener(new IoFutureListener<IoFuture>() {
                @Override
                public void operationComplete(IoFuture future) {
                    log.info("connection closed on " + io);
                }
            });
            return s;
          }
        };
	}

	public int getPort() {
		return myAddress.getPort();
	}

	public String formatUrl(String gituser, String servername, String repository) {
		if (getPort() == DEFAULT_PORT) {
			// standard port
			return MessageFormat.format("{0}@{1}/{2}", gituser, servername, repository);
		} else {
			// non-standard port
			return MessageFormat.format("ssh://{0}@{1}:{2,number,0}/{3}", gituser, servername, getPort(), repository);
		}
	}

	/**
	 * Start this daemon on a background thread.
	 *
	 * @throws IOException
	 *             the server socket could not be opened.
	 * @throws IllegalStateException
	 *             the daemon is already running.
	 */
	public synchronized void start() throws IOException {
		if (run.get()) {
			throw new IllegalStateException(JGitText.get().daemonAlreadyRunning);
		}

		super.start();
		run.set(true);

		log.info(MessageFormat.format("SSH Daemon is listening on {0}:{1,number,0}",
				myAddress.getAddress().getHostAddress(), myAddress.getPort()));
	}

	/** @return true if this daemon is receiving connections. */
	public boolean isRunning() {
		return run.get();
	}

	/** Stop this daemon. */
	public synchronized void stop() {
		if (run.get()) {
			log.info("SSH Daemon stopping...");
			run.set(false);

			try {
				super.stop();
			} catch (InterruptedException e) {
				log.error("SSH Daemon stop interrupted", e);
			}
		}
	}

	   /**
     * Performs most of default configuration (setup random sources, setup ciphers,
     * etc; also, support for forwarding and filesystem is explicitly disallowed).
     *
     * {@link #setKeyPairProvider(KeyPairProvider)} and
     * {@link #setPublickeyAuthenticator(PublickeyAuthenticator)} are left for you.
     * And applying {@link #setCommandFactory(CommandFactory)} is probably wise if you
     * want something to actually happen when users do successfully authenticate.
     */
    @SuppressWarnings("unchecked")
    public void setup() {
        if (!SecurityUtils.isBouncyCastleRegistered())
            throw new RuntimeException("BC crypto not available");

        setKeyExchangeFactories(Arrays.<NamedFactory<KeyExchange>>asList(
                new DHG14.Factory(),
                new DHG1.Factory())
        );

        setRandomFactory(new SingletonRandomFactory(new BouncyCastleRandom.Factory()));

        setupCiphers();

        setCompressionFactories(Arrays.<NamedFactory<Compression>>asList(
                new CompressionNone.Factory())
        );

        setMacFactories(Arrays.<NamedFactory<Mac>>asList(
                new HMACMD5.Factory(),
                new HMACSHA1.Factory(),
                new HMACMD596.Factory(),
                new HMACSHA196.Factory())
        );

        setChannelFactories(Arrays.<NamedFactory<Channel>>asList(
                new ChannelSession.Factory(),
                new ChannelDirectTcpip.Factory())
        );

        setSignatureFactories(Arrays.<NamedFactory<Signature>>asList(
                new SignatureDSA.Factory(),
                new SignatureRSA.Factory())
        );

        setFileSystemFactory(new FileSystemFactory() {
            @Override
            public FileSystemView createFileSystemView(Session session) throws IOException {
                return new FileSystemView() {
                    @Override
                    public SshFile getFile(SshFile baseDir, String file) {
                        return null;
                    }

                    @Override
                    public SshFile getFile(String file) {
                        return null;
                    }
                };
            }
        });

        setForwardingFilter(new ForwardingFilter() {
            @Override
            public boolean canForwardAgent(ServerSession session) {
                return false;
            }

            @Override
            public boolean canForwardX11(ServerSession session) {
                return false;
            }

            @Override
            public boolean canConnect(InetSocketAddress address, ServerSession session) {
                return false;
            }

            @Override
            public boolean canListen(InetSocketAddress address, ServerSession session) {
                return false;
            }
        });

        setUserAuthFactories(Arrays.<NamedFactory<UserAuth>>asList(
                new UserAuthPublicKey.Factory())
        );
    }

    protected void setupCiphers() {
        List<NamedFactory<Cipher>> avail = new LinkedList<NamedFactory<Cipher>>();
        avail.add(new AES128CBC.Factory());
        avail.add(new TripleDESCBC.Factory());
        avail.add(new BlowfishCBC.Factory());
        avail.add(new AES192CBC.Factory());
        avail.add(new AES256CBC.Factory());

        for (Iterator<NamedFactory<Cipher>> i = avail.iterator(); i.hasNext();) {
            final NamedFactory<Cipher> f = i.next();
            try {
                final Cipher c = f.create();
                final byte[] key = new byte[c.getBlockSize()];
                final byte[] iv = new byte[c.getIVSize()];
                c.init(Cipher.Mode.Encrypt, key, iv);
            } catch (InvalidKeyException e) {
                i.remove();
            } catch (Exception e) {
                i.remove();
            }
        }
        setCipherFactories(avail);
    }
}
