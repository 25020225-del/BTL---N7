package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;

/**
 * Cryptographic security core utility provider.
 * Implements high-performance asymmetric RSA-2048 key-exchanges and symmetric AES-256-GCM payload seals.
 */
public class CryptoUtil {

    private static final Logger log = LoggerFactory.getLogger(CryptoUtil.class);
    private static final String RSA = "RSA";
    private static final String AES_ALGO = "AES";
    private static final String AES_CIPHER_ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    /**
     * Dynamically generates an ephemeral RSA-2048 asymmetric key pair context.
     *
     * @return a valid structural {@link KeyPair} instance
     * @throws RuntimeException if the platform missing default cryptographic provider providers
     */
    public static KeyPair generateRSAKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA);
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            log.error("RSA algorithm not found.");
            throw new RuntimeException("Cannot find RSA algorithm", e);
        }
    }

    /**
     * Generates a bounded secure symmetric AES-256 key block instance.
     *
     * @return a fully populated {@link SecretKey} context
     */
    public static SecretKey generateAESKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGO);
            keyGen.init(256);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            log.error("AES algorithm not found.");
            throw new RuntimeException("Cannot find AES algorithm", e);
        }
    }

    public static String encryptAESKeyWithRSA(SecretKey aesKey, PublicKey rsaPublicKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA);
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
            byte[] encryptedKey = cipher.doFinal(aesKey.getEncoded());
            return Base64.getEncoder().encodeToString(encryptedKey);
        } catch (GeneralSecurityException e) {
            log.error("Error encrypting AES key.");
            throw new RuntimeException("RSA encrypting error", e);
        }
    }

    public static SecretKey decryptAESKeyWithRSA(String encryptedAesKeyBase64, PrivateKey rsaPrivateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA);
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
            byte[] decryptedKey = cipher.doFinal(Base64.getDecoder().decode(encryptedAesKeyBase64));
            return new SecretKeySpec(decryptedKey, 0, decryptedKey.length, AES_ALGO);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("Wrong key format or RSA decrypting error.");
            throw new RuntimeException("RSA decrypting error", e);
        }
    }

    /**
     * Cryptographically seals text records utilizing atomized single-use IV vectors.
     *
     * @param plainText original serialization message targeted for encryption
     * @param secretKey shared AES-256 symmetric cipher block key token reference
     * @return Base64 encoded compound payload text string containing un-encrypted IV header
     */
    public static String encryptAES(String plainText, SecretKey secretKey) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(AES_CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());

            byte[] cipherMessage = new byte[GCM_IV_LENGTH + encryptedBytes.length];
            System.arraycopy(iv, 0, cipherMessage, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedBytes, 0, cipherMessage, GCM_IV_LENGTH, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(cipherMessage);
        } catch (GeneralSecurityException e) {
            log.error("Error encrypting payload with AES-GCM.");
            throw new RuntimeException("Encrypting AES error", e);
        }
    }

    /**
     * Decrypts a compound byte payload by stripping out structural GCM tracking parameters.
     */
    public static String decryptAES(String encryptedText, SecretKey secretKey) {
        try {
            byte[] cipherMessage = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(cipherMessage, 0, iv, 0, GCM_IV_LENGTH);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            int payloadLength = cipherMessage.length - GCM_IV_LENGTH;
            byte[] encryptedBytes = new byte[payloadLength];
            System.arraycopy(cipherMessage, GCM_IV_LENGTH, encryptedBytes, 0, payloadLength);

            Cipher cipher = Cipher.getInstance(AES_CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("AES Decryption failed (Data manipulated or corrupted).");
            throw new RuntimeException("Decrypting AES error", e);
        }
    }

    public static PublicKey getPublicKeyFromBase64(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            return keyFactory.generatePublic(spec);
        } catch (NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException | IllegalArgumentException e) {
            log.error("Server public key generating error.");
            throw new RuntimeException("Public key getting error", e);
        }
    }
}