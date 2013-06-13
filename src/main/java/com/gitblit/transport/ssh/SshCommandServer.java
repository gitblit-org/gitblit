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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Eric Myhre
 *
 */
public class SshCommandServer extends SshServer {

	private static final Logger log = LoggerFactory.getLogger(SshCommandServer.class);

	public SshCommandServer() {
		setSessionFactory(new SessionFactory() {
			@Override
			protected ServerSession createSession(final IoSession io) throws Exception {
				log.info("connection accepted on " + io);

				if (io.getConfig() instanceof SocketSessionConfig) {
					final SocketSessionConfig c = (SocketSessionConfig) io.getConfig();
					c.setKeepAlive(true);
				}

				final ServerSession s = (ServerSession) super.createSession(io);
				s.setAttribute(SshDaemonClient.ATTR_KEY, new SshDaemonClient());

				io.getCloseFuture().addListener(new IoFutureListener<IoFuture>() {
					@Override
					public void operationComplete(IoFuture future) {
						log.info("connection closed on " + io);
					}
				});
				return s;
			}
		});
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
