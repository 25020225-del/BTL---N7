package utils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;

import static utils.ConsoleColors.*;

public class CryptoUtil {

    private static final String RSA = "RSA";
    private static final String AES = "AES";

    // 1. Generate an RSA key pair (for server use)
    public static KeyPair generateRSAKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA);
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            System.out.println("[Crypto]: Error: " + RED + "RSA algorithm error" + RESET);
            throw new RuntimeException("Cannot find RSA algorithm", e);
        }
    }

    // 2. AES symmetric key (Client-side)
    public static SecretKey generateAESKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(AES);
            keyGen.init(128);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            System.out.println("[Crypto]: " + RED + "AES algorithm error" + RESET);
            throw new RuntimeException("Cannot find AES algorithm", e);
        }
    }

    // 3. The client encrypts the AES key using the server's public key
    public static String encryptAESKeyWithRSA(SecretKey aesKey, PublicKey rsaPublicKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA);
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
            byte[] encryptedKey = cipher.doFinal(aesKey.getEncoded());
            return Base64.getEncoder().encodeToString(encryptedKey);
        } catch (GeneralSecurityException e) {
            System.out.println("[Crypto]: " + RED + "Encrypting AES key error" + RESET);
            throw new RuntimeException("RSA encrypting error", e);
        }
    }

    // 4. Server decrypts to retrieve the AES key using the private key
    public static SecretKey decryptAESKeyWithRSA(String encryptedAesKeyBase64, PrivateKey rsaPrivateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA);
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
            // An IllegalArgumentException may occur if the Base64 string is corrupted or the data is lost.
            byte[] decryptedKey = cipher.doFinal(Base64.getDecoder().decode(encryptedAesKeyBase64));
            return new SecretKeySpec(decryptedKey, 0, decryptedKey.length, AES);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            System.out.println("[Crypto]: " + RED + "Wrong key format or RSA decrypting error" + RESET);
            throw new RuntimeException("RSA decrypting error", e);
        }
    }

    // 5. Encrypt the JSON payload using AES
    public static String encryptAES(String plainText, SecretKey secretKey) {
        try {
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (GeneralSecurityException e) {
            System.out.println("[Crypto]: " + RED + "Encrypting payload JSON error" + RESET);
            throw new RuntimeException("Encrypting AES error", e);
        }
    }

    // 6. Decrypt the JSON payload using AES
    public static String decryptAES(String encryptedText, SecretKey secretKey) {
        try {
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            System.out.println("[Crypto]: " + RED + "Decrypting payload JSON error " +
                    "(Data package is manipulated or Lost connection): " + RESET);
            throw new RuntimeException("Decrypting AES error", e);
        }
    }
    // 7. Reconstruct the PublicKey from a Base64 string (used by the client to read the server's key)
    public static PublicKey getPublicKeyFromBase64(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            return keyFactory.generatePublic(spec);
        } catch (NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException | IllegalArgumentException e) {
            System.out.println("[Crypto]: " + RED + "Server public key generating error" + RESET);
            throw new RuntimeException("Public key getting error", e);
        }
    }
}