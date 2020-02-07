package com.aevi.android.rxmessenger.service.websocket;

import android.content.Context;
import android.util.Log;

import org.spongycastle.asn1.x500.X500Name;
import org.spongycastle.cert.X509CertificateHolder;
import org.spongycastle.cert.X509v3CertificateBuilder;
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter;
import org.spongycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.operator.ContentSigner;
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;

public final class SelfSignedCertificate {

    private static final String TAG = SelfSignedCertificate.class.getSimpleName();

    /**
     * Current time minus 1 year, just in case software clock goes back due to time synchronization
     */
    private static final Date DEFAULT_NOT_BEFORE = new Date(System.currentTimeMillis() - 86400000L * 365);

    /**
     * The maximum possible value in X.509 specification: 9999-12-31 23:59:59
     */
    private static final Date DEFAULT_NOT_AFTER = new Date(253402300799000L);

    /**
     * FIPS 140-2 encryption requires the key length to be 2048 bits or greater.
     */
    private static final int DEFAULT_KEY_LENGTH_BITS = 2048;

    private static final Provider provider = new BouncyCastleProvider();

    private KeyStore keystore;
    private KeyManagerFactory keyManagerFactory;


    public SelfSignedCertificate(Context context) throws CertificateException {
        this(context, DEFAULT_NOT_BEFORE, DEFAULT_NOT_AFTER);
    }

    public SelfSignedCertificate(Context context, Date notBefore, Date notAfter)
            throws CertificateException {
        // Generate an RSA key pair.
        try {
            generateKeystore(context, notBefore, notAfter);
        } catch (Exception e) {
            throw new CertificateException("Failed to generate certificate for secure websocket", e);
        }
    }

    private KeyPair generateKey(SecureRandom random) {
        final KeyPair keypair;
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(DEFAULT_KEY_LENGTH_BITS, random);
            keypair = keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            // Should not reach here because every Java implementation must have RSA key pair generator.
            throw new Error(e);
        }
        return keypair;
    }

    private X509Certificate generateCertificate(String fqdn, KeyPair keypair, SecureRandom random, Date notBefore, Date notAfter)
            throws Exception {
        PrivateKey key = keypair.getPrivate();

        // Prepare the information required for generating an X.509 certificate.
        X500Name owner = new X500Name("CN=" + fqdn);
        X509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(owner, new BigInteger(64, random), notBefore, notAfter, owner, keypair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(key);
        X509CertificateHolder certHolder = builder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter().setProvider(provider).getCertificate(certHolder);
        cert.verify(keypair.getPublic());
        return cert;
    }

    private void generateKeystore(Context context, Date notBefore, Date notAfter) throws Exception {
        keystore = KeyStore.getInstance("AndroidKeyStore");
        keystore.load(null);
        String fqdn = context.getPackageName();
        if (!keystore.containsAlias(fqdn)) {
            Log.d(TAG, "Adding new key and to keystore");
            try {
                SecureRandom random = new SecureRandom();
                KeyPair keypair = generateKey(random);
                X509Certificate cert = generateCertificate(context.getPackageName(), keypair, random, notBefore, notAfter);
                X509Certificate[] certificateChain = {cert};
                keystore.setKeyEntry(fqdn, keypair.getPrivate(), null, certificateChain);
            } catch (Throwable t2) {
                Log.d(TAG, "Failed to generate a self-signed X.509 certificate using Bouncy Castle:", t2);
                throw new CertificateException("No provider succeeded to generate a self-signed certificate. See debug log for the root cause.", t2);
            }
        } else {
            Log.d(TAG, "Keys already setup");
        }

        keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keystore, null);
    }

    KeyStore getKeystore() {
        return keystore;
    }

    KeyManagerFactory getKeyManagerFactory() {
        return keyManagerFactory;
    }
}
