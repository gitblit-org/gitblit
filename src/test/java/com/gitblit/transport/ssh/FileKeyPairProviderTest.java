package com.gitblit.transport.ssh;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.util.Iterator;

import static org.junit.Assert.*;

public class FileKeyPairProviderTest
{
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    private void generateKeyPair(File file, String algorithm, int keySize) {
        if (file.exists()) {
            file.delete();
        }
        SshDaemon.generateKeyPair(file, algorithm, keySize);
    }

    @Test
    public void loadKeysEddsa() throws IOException
    {
        File file = testFolder.newFile("eddsa.pem");
        generateKeyPair(file, "EdDSA", 0);

        FileKeyPairProvider hostKeyPairProvider = new FileKeyPairProvider();
        hostKeyPairProvider.setFiles(new String [] { file.getPath() });

        Iterable<KeyPair> keyPairs = hostKeyPairProvider.loadKeys();
        Iterator<KeyPair> iterator = keyPairs.iterator();
        assertTrue(iterator.hasNext());
        KeyPair keyPair = iterator.next();
        assertNotNull(keyPair);
        assertEquals("Unexpected key pair type", "EdDSA", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    public void loadKeysEd25519() throws IOException
    {
        File file = testFolder.newFile("ed25519.pem");
        generateKeyPair(file, "ED25519", 0);

        FileKeyPairProvider hostKeyPairProvider = new FileKeyPairProvider();
        hostKeyPairProvider.setFiles(new String [] { file.getPath() });

        Iterable<KeyPair> keyPairs = hostKeyPairProvider.loadKeys();
        Iterator<KeyPair> iterator = keyPairs.iterator();
        assertTrue(iterator.hasNext());
        KeyPair keyPair = iterator.next();
        assertNotNull(keyPair);
        assertEquals("Unexpected key pair type", "Ed25519", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    public void loadKeysECDSA() throws IOException
    {
        File file = testFolder.newFile("ecdsa.pem");
        generateKeyPair(file, "ECDSA", 0);

        FileKeyPairProvider hostKeyPairProvider = new FileKeyPairProvider();
        hostKeyPairProvider.setFiles(new String [] { file.getPath() });

        Iterable<KeyPair> keyPairs = hostKeyPairProvider.loadKeys();
        Iterator<KeyPair> iterator = keyPairs.iterator();
        assertTrue(iterator.hasNext());
        KeyPair keyPair = iterator.next();
        assertNotNull(keyPair);
        assertEquals("Unexpected key pair type", "ECDSA", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    public void loadKeysRSA() throws IOException
    {
        File file = testFolder.newFile("rsa.pem");
        generateKeyPair(file, "RSA", 4096);

        FileKeyPairProvider hostKeyPairProvider = new FileKeyPairProvider();
        hostKeyPairProvider.setFiles(new String [] { file.getPath() });

        Iterable<KeyPair> keyPairs = hostKeyPairProvider.loadKeys();
        Iterator<KeyPair> iterator = keyPairs.iterator();
        assertTrue(iterator.hasNext());
        KeyPair keyPair = iterator.next();
        assertNotNull(keyPair);
        assertEquals("Unexpected key pair type", "RSA", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    public void loadKeysDefault() throws IOException
    {
        File rsa = testFolder.newFile("rsa.pem");
        generateKeyPair(rsa, "RSA", 2048);
        File ecdsa = testFolder.newFile("ecdsa.pem");
        generateKeyPair(ecdsa, "ECDSA", 0);
        File eddsa = testFolder.newFile("eddsa.pem");
        generateKeyPair(eddsa, "EdDSA", 0);
        File ed25519 = testFolder.newFile("ed25519.pem");
        generateKeyPair(ed25519, "ED25519", 0);

        FileKeyPairProvider hostKeyPairProvider = new FileKeyPairProvider();
        hostKeyPairProvider.setFiles(new String [] { ecdsa.getPath(), eddsa.getPath(), rsa.getPath(), ed25519.getPath() });

        Iterable<KeyPair> keyPairs = hostKeyPairProvider.loadKeys();
        Iterator<KeyPair> iterator = keyPairs.iterator();

        assertTrue(iterator.hasNext());
        KeyPair keyPair = iterator.next();
        assertNotNull(keyPair);
        assertEquals("Unexpected key pair type", "ECDSA", keyPair.getPrivate().getAlgorithm());

        assertTrue(iterator.hasNext());
        keyPair = iterator.next();
        assertNotNull(keyPair);
        assertEquals("Unexpected key pair type", "EdDSA", keyPair.getPrivate().getAlgorithm());

        assertTrue(iterator.hasNext());
        keyPair = iterator.next();
        assertNotNull(keyPair);
        assertEquals("Unexpected key pair type", "RSA", keyPair.getPrivate().getAlgorithm());

        assertTrue(iterator.hasNext());
        keyPair = iterator.next();
        assertNotNull(keyPair);
        assertEquals("Unexpected key pair type", "Ed25519", keyPair.getPrivate().getAlgorithm());

        assertFalse(iterator.hasNext());
    }
}
