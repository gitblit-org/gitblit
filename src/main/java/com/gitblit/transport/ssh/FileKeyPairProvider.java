/*
 * Copyright 2014 gitblit.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.gitblit.transport.ssh;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.PrivateKeyEntryDecoder;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil;
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil;
import org.bouncycastle.jcajce.spec.OpenSSHPrivateKeySpec;
import org.bouncycastle.jcajce.spec.OpenSSHPublicKeySpec;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

public class FileKeyPairProvider extends AbstractKeyPairProvider {

    private String[] files;

    public FileKeyPairProvider() {
    }

    public FileKeyPairProvider(String[] files) {
        this.files = files;
    }

    public String[] getFiles() {
        return files;
    }

    public void setFiles(String[] files) {
        this.files = files;
    }

    public Iterable<KeyPair> loadKeys() {
        return loadKeys(null);
    }

    @Override
    public Iterable<KeyPair> loadKeys(SessionContext session) {
        if (!SecurityUtils.isBouncyCastleRegistered()) {
            throw new IllegalStateException("BouncyCastle must be registered as a JCE provider");
        }
        return new Iterable<KeyPair>() {
            @Override
            public Iterator<KeyPair> iterator() {
                return new Iterator<KeyPair>() {
                    private final Iterator<String> iterator = Arrays.asList(files).iterator();
                    private KeyPair nextKeyPair;
                    private boolean nextKeyPairSet;

                    @Override
                    public boolean hasNext() {
                        return nextKeyPairSet || setNextObject();
                    }

                    @Override
                    public KeyPair next() {
                        if (!nextKeyPairSet && !setNextObject()) {
                            throw new NoSuchElementException();
                        }
                        nextKeyPairSet = false;
                        return nextKeyPair;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    private boolean setNextObject() {
                        while (iterator.hasNext()) {
                            String file = iterator.next();
                            File f = new File(file);
                            if (!f.isFile()) {
                                log.debug("File does not exist, skipping {}", file);
                                continue;
                            }
                            nextKeyPair = doLoadKey(file);
                            if (nextKeyPair != null) {
                                nextKeyPairSet = true;
                                return true;
                            }
                        }
                        return false;
                    }
                };
            }
        };
    }

    private KeyPair doLoadKey(String file) {
        try {
            try (PemReader r = new PemReader(new InputStreamReader(new FileInputStream(file)))) {
                PemObject pemObject = r.readPemObject();
                if (pemObject != null && "OPENSSH PRIVATE KEY".equals(pemObject.getType())) {
                    try {
                        byte[] privateKeyContent = pemObject.getContent();
                        AsymmetricKeyParameter privateKeyParameters = OpenSSHPrivateKeyUtil.parsePrivateKeyBlob(privateKeyContent);
                        if (privateKeyParameters instanceof Ed25519PrivateKeyParameters) {
                            OpenSSHPrivateKeySpec privkeySpec = new OpenSSHPrivateKeySpec(privateKeyContent);
                            Ed25519PublicKeyParameters publicKeyParameters =
                                    ((Ed25519PrivateKeyParameters) privateKeyParameters).generatePublicKey();
                            OpenSSHPublicKeySpec pubKeySpec =
                                    new OpenSSHPublicKeySpec(OpenSSHPublicKeyUtil.encodePublicKey(publicKeyParameters));
                            KeyFactory kf = KeyFactory.getInstance("Ed25519", "BC");
                            PrivateKey privateKey = kf.generatePrivate(privkeySpec);
                            PublicKey publicKey = kf.generatePublic(pubKeySpec);
                            return new KeyPair(publicKey, privateKey);
                        }
                        log.warn("OpenSSH format is only supported for Ed25519 key type. Unable to read key {}", file);
                    } catch (Exception e) {
                        log.warn("Unable to read key {}", file, e);
                    }
                    return null;
                }

                if (pemObject != null && "EDDSA PRIVATE KEY".equals(pemObject.getType())) {
                    byte[] privateKeyContent = pemObject.getContent();
                    PrivateKeyEntryDecoder<? extends PublicKey, ? extends PrivateKey> decoder =
                            SecurityUtils.getOpenSSHEDDSAPrivateKeyEntryDecoder();
                    PrivateKey privateKey = decoder.decodePrivateKey(
                            null, FilePasswordProvider.EMPTY, privateKeyContent, 0, privateKeyContent.length);
                    PublicKey publicKey = SecurityUtils.recoverEDDSAPublicKey(privateKey);
                    return new KeyPair(publicKey, privateKey);
                }
            }

            try (PEMParser r = new PEMParser(new InputStreamReader(new FileInputStream(file)))) {
                Object o = r.readObject();
                JcaPEMKeyConverter pemConverter = new JcaPEMKeyConverter();
                pemConverter.setProvider("BC");
                if (o instanceof PEMKeyPair) {
                    return pemConverter.getKeyPair((PEMKeyPair) o);
                }
                if (o instanceof KeyPair) {
                    return (KeyPair) o;
                }
                if (o != null) {
                    log.warn("Cannot read unsupported PEM object of type: {}", o.getClass().getCanonicalName());
                }
            }
        } catch (Exception e) {
            log.warn("Unable to read key {}", file, e);
        }
        return null;
    }
}
