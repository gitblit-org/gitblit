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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.sshd.common.config.keys.KeyEntryResolver;
import org.apache.sshd.common.io.IoServiceFactoryFactory;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.common.util.io.output.SecureByteArrayOutputStream;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.util.security.bouncycastle.BouncyCastleSecurityProviderRegistrar;
import org.apache.sshd.common.util.security.eddsa.EdDSASecurityProviderRegistrar;
import org.apache.sshd.common.util.security.eddsa.OpenSSHEd25519PrivateKeyEntryDecoder;
import org.apache.sshd.server.SshServer;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.eclipse.jgit.internal.JGitText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants;
import com.gitblit.IStoredSettings;
import com.gitblit.Keys;
import com.gitblit.manager.IGitblit;
import com.gitblit.transport.ssh.commands.SshCommandFactory;
import com.gitblit.utils.JnaUtils;
import com.gitblit.utils.StringUtils;
import com.gitblit.utils.WorkQueue;
import com.google.common.io.Files;

public class SshDaemon {

    private static final Logger log = LoggerFactory.getLogger(SshDaemon.class);

    private static final String AUTH_PUBLICKEY = "publickey";
    private static final String AUTH_PASSWORD = "password";
    private static final String AUTH_KBD_INTERACTIVE = "keyboard-interactive";
    private static final String AUTH_GSSAPI = "gssapi-with-mic";

    public static enum SshSessionBackend {
        MINA, NIO2
    }

    public static final int DEFAULT_PORT = 22;

    private final AtomicBoolean run;
    private final IGitblit gitblit;
    private final SshServer sshd;

    public SshDaemon(IGitblit gitblit, WorkQueue workQueue) {
        this.gitblit = gitblit;

        IStoredSettings settings = gitblit.getSettings();

        SecurityUtils.registerSecurityProvider(new BouncyCastleSecurityProviderRegistrar());
        if (SecurityUtils.isBouncyCastleRegistered()) {
            log.info("BouncyCastle is registered as a JCE provider");
        }
        SecurityUtils.registerSecurityProvider(new EdDSASecurityProviderRegistrar());
        if (SecurityUtils.isProviderRegistered("EdDSA")) {
            log.info("EdDSA is registered as a JCE provider");
        }

        File rsaKeyStore = new File(gitblit.getBaseFolder(), "ssh-rsa-hostkey.pem");
        File dsaKeyStore = new File(gitblit.getBaseFolder(), "ssh-dsa-hostkey.pem");
        File ecdsaKeyStore = new File(gitblit.getBaseFolder(), "ssh-ecdsa-hostkey.pem");
        File eddsaKeyStore = new File(gitblit.getBaseFolder(), "ssh-eddsa-hostkey.pem");
        File ed25519KeyStore = new File(gitblit.getBaseFolder(), "ssh-ed25519-hostkey.pem");
        generateKeyPair(rsaKeyStore, "RSA", 2048);
        generateKeyPair(ecdsaKeyStore, "ECDSA", 256);
        generateKeyPair(eddsaKeyStore, "EdDSA", 0);
        FileKeyPairProvider hostKeyPairProvider = new FileKeyPairProvider();
        hostKeyPairProvider.setFiles(new String[] {
                ecdsaKeyStore.getPath(),
                eddsaKeyStore.getPath(),
                ed25519KeyStore.getPath(),
                rsaKeyStore.getPath(),
                dsaKeyStore.getPath()
        });

        String sshBackendStr = settings.getString(Keys.git.sshBackend, SshSessionBackend.NIO2.name());
        SshSessionBackend backend = SshSessionBackend.valueOf(sshBackendStr);
        if (backend == SshSessionBackend.MINA) {
            log.warn("MINA SSH backend is no longer supported in the patched image; forcing NIO2");
            backend = SshSessionBackend.NIO2;
            sshBackendStr = backend.name();
        }
        System.setProperty(IoServiceFactoryFactory.class.getName(), Nio2ServiceFactoryFactory.class.getName());

        int port = settings.getInteger(Keys.git.sshPort, 0);
        String bindInterface = settings.getString(Keys.git.sshBindInterface, "");
        InetSocketAddress addr;
        if (StringUtils.isEmpty(bindInterface)) {
            addr = new InetSocketAddress(port);
        } else {
            addr = new InetSocketAddress(bindInterface, port);
        }

        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(addr.getPort());
        sshd.setHost(addr.getHostName());
        sshd.setKeyPairProvider(hostKeyPairProvider);

        List<String> authMethods = settings.getStrings(Keys.git.sshAuthenticationMethods);
        if (authMethods.isEmpty()) {
            authMethods.add(AUTH_PUBLICKEY);
            authMethods.add(AUTH_PASSWORD);
        }
        if (settings.getBoolean("git.sshWithKrb5", false) && !authMethods.contains(AUTH_GSSAPI)) {
            authMethods.add(AUTH_GSSAPI);
            log.warn("git.sshWithKrb5 is obsolete!");
            log.warn("Please add {} to {} in gitblit.properties!", AUTH_GSSAPI, Keys.git.sshAuthenticationMethods);
            settings.overrideSetting(Keys.git.sshAuthenticationMethods,
                    settings.getString(Keys.git.sshAuthenticationMethods, AUTH_PUBLICKEY + " " + AUTH_PASSWORD) + " " + AUTH_GSSAPI);
        }
        if (authMethods.contains(AUTH_PUBLICKEY)) {
            SshKeyAuthenticator keyAuthenticator = new SshKeyAuthenticator(gitblit.getPublicKeyManager(), gitblit);
            sshd.setPublickeyAuthenticator(keyAuthenticator);
            log.info("SSH: adding public key authentication method.");
        }
        if (authMethods.contains(AUTH_PASSWORD) || authMethods.contains(AUTH_KBD_INTERACTIVE)) {
            sshd.setPasswordAuthenticator(new UsernamePasswordAuthenticator(gitblit));
            log.info("SSH: adding password authentication method.");
        }
        if (authMethods.contains(AUTH_GSSAPI)) {
            sshd.setGSSAuthenticator(new SshKrbAuthenticator(settings, gitblit));
            log.info("SSH: adding GSSAPI authentication method.");
        }

        sshd.setSessionFactory(new SshServerSessionFactory(sshd));
        sshd.setKexExtensionHandler(new ServerSigAlgsExtensionHandler());
        sshd.setFileSystemFactory(new DisabledFilesystemFactory());
        sshd.setForwardingFilter(new NonForwardingFilter());
        sshd.setCommandFactory(new SshCommandFactory(gitblit, workQueue));
        sshd.setShellFactory(new WelcomeShell(gitblit));

        String version = String.format("%s (%s-%s)", Constants.getGitBlitVersion().replace(' ', '_'),
                sshd.getVersion(), sshBackendStr);
        CoreModuleProperties.SERVER_IDENTIFICATION.set(sshd, version);

        run = new AtomicBoolean(false);
    }

    public String formatUrl(String gituser, String servername, String repository) {
        IStoredSettings settings = gitblit.getSettings();

        int port = sshd.getPort();
        int displayPort = settings.getInteger(Keys.git.sshAdvertisedPort, port);
        String displayServername = settings.getString(Keys.git.sshAdvertisedHost, "");
        if (displayServername.isEmpty()) {
            displayServername = servername;
        }
        if (displayPort == DEFAULT_PORT) {
            return MessageFormat.format("ssh://{0}@{1}/{2}", gituser, displayServername, repository);
        }
        return MessageFormat.format("ssh://{0}@{1}:{2,number,0}/{3}",
                gituser, displayServername, displayPort, repository);
    }

    public synchronized void start() throws IOException {
        if (run.get()) {
            throw new IllegalStateException(JGitText.get().daemonAlreadyRunning);
        }

        sshd.start();
        run.set(true);

        String sshBackendStr = gitblit.getSettings().getString(Keys.git.sshBackend, SshSessionBackend.NIO2.name());
        log.info(MessageFormat.format("SSH Daemon ({0}) is listening on {1}:{2,number,0}",
                sshBackendStr, sshd.getHost(), sshd.getPort()));
    }

    public boolean isRunning() {
        return run.get();
    }

    public synchronized void stop() {
        if (run.get()) {
            log.info("SSH Daemon stopping...");
            run.set(false);

            try {
                ((SshCommandFactory) sshd.getCommandFactory()).stop();
                sshd.stop();
            } catch (IOException e) {
                log.error("SSH Daemon stop interrupted", e);
            }
        }
    }

    static void generateKeyPair(File file, String algorithm, int keySize) {
        if (file.exists()) {
            return;
        }
        try {
            KeyPairGenerator generator = SecurityUtils.getKeyPairGenerator(algorithm);
            if (keySize != 0) {
                generator.initialize(keySize);
                log.info("Generating {}-{} SSH host keypair...", algorithm, Integer.valueOf(keySize));
            } else {
                log.info("Generating {} SSH host keypair...", algorithm);
            }
            KeyPair kp = generator.generateKeyPair();

            Files.touch(file);
            try {
                JnaUtils.setFilemode(file, JnaUtils.S_IRUSR | JnaUtils.S_IWUSR);
            } catch (UnsatisfiedLinkError | UnsupportedOperationException e) {
                // Unsupported platform-specific chmod path.
            }

            try (FileOutputStream os = new FileOutputStream(file)) {
                try (PemWriter w = new PemWriter(new OutputStreamWriter(os))) {
                    if ("ED25519".equals(algorithm)) {
                        AsymmetricKeyParameter keyParam = PrivateKeyFactory.createKey(kp.getPrivate().getEncoded());
                        byte[] encKey = OpenSSHPrivateKeyUtil.encodePrivateKey(keyParam);
                        w.writeObject(new PemObject("OPENSSH PRIVATE KEY", encKey));
                    } else if ("EdDSA".equals(algorithm)) {
                        PrivateKey privateKey = kp.getPrivate();
                        if (privateKey instanceof EdDSAPrivateKey) {
                            OpenSSHEd25519PrivateKeyEntryDecoder encoder =
                                    (OpenSSHEd25519PrivateKeyEntryDecoder) SecurityUtils.getOpenSSHEDDSAPrivateKeyEntryDecoder();
                            EdDSAPrivateKey dsaPrivateKey = (EdDSAPrivateKey) privateKey;
                            EdDSAPublicKey dsaPublicKey = encoder.recoverPublicKey(dsaPrivateKey);
                            SecureByteArrayOutputStream encos = new SecureByteArrayOutputStream();
                            String type = encoder.encodePrivateKey(encos, dsaPrivateKey, dsaPublicKey);
                            SecureByteArrayOutputStream bos = new SecureByteArrayOutputStream();
                            KeyEntryResolver.encodeString(bos, type);
                            encos.writeTo(bos);
                            w.writeObject(new PemObject("EDDSA PRIVATE KEY", bos.toByteArray()));
                        } else {
                            log.warn("Unable to encode EdDSA key, got key type {}", privateKey.getClass().getCanonicalName());
                        }
                    } else {
                        w.writeObject(new JcaMiscPEMGenerator(kp));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Unable to generate " + algorithm + " keypair", e);
        }
    }
}
