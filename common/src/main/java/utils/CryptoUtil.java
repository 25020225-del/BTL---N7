package utils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Base64;

import static utils.ConsoleColors.*;

/**
 * Utility class for cryptographic operations.
 * Implements secure RSA-2048 for key exchange and AES-256-GCM for payload encryption.
 */
public class CryptoUtil {

    private static final String RSA = "RSA";
    private static final String AES_ALGO = "AES";

    // GCM (Galois/Counter Mode) with NoPadding prevents ECB vulnerabilities and provides data authenticity
    private static final String AES_CIPHER_ALGO = "AES/GCM/NoPadding";

    private static final int GCM_TAG_LENGTH = 128; // Authentication tag length in bits
    private static final int GCM_IV_LENGTH = 12;   // Initialization Vector length in bytes (96 bits is optimal for GCM)

    /**
     * Generates an RSA-2048 key pair for the server.
     */
    public static KeyPair generateRSAKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA);
            keyGen.initialize(2048);
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            System.out.println("[Crypto]: Error: " + RED + "RSA algorithm not found." + RESET);
            throw new RuntimeException("Cannot find RSA algorithm", e);
        }
    }

    /**
     * Generates a secure AES-256 symmetric key for the client.
     */
    public static SecretKey generateAESKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(AES_ALGO);
            keyGen.init(256);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            System.out.println("[Crypto]: " + RED + "AES algorithm not found." + RESET);
            throw new RuntimeException("Cannot find AES algorithm", e);
        }
    }

    /**
     * Encrypts the AES key using the Server's RSA Public Key.
     */
    public static String encryptAESKeyWithRSA(SecretKey aesKey, PublicKey rsaPublicKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA);
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);
            byte[] encryptedKey = cipher.doFinal(aesKey.getEncoded());
            return Base64.getEncoder().encodeToString(encryptedKey);
        } catch (GeneralSecurityException e) {
            System.out.println("[Crypto]: " + RED + "Error encrypting AES key." + RESET);
            throw new RuntimeException("RSA encrypting error", e);
        }
    }

    /**
     * Decrypts the AES key using the Server's RSA Private Key.
     */
    public static SecretKey decryptAESKeyWithRSA(String encryptedAesKeyBase64, PrivateKey rsaPrivateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA);
            cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey);
            // An IllegalArgumentException may occur if the Base64 string is corrupted or the data is lost.
            byte[] decryptedKey = cipher.doFinal(Base64.getDecoder().decode(encryptedAesKeyBase64));
            return new SecretKeySpec(decryptedKey, 0, decryptedKey.length, AES_ALGO);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            System.out.println("[Crypto]: " + RED + "Wrong key format or RSA decrypting error." + RESET);
            throw new RuntimeException("RSA decrypting error", e);
        }
    }

    /**
     * Encrypts the JSON payload using AES-256-GCM.
     * The randomly generated IV is prepended to the final encrypted byte array.
     */
    public static String encryptAES(String plainText, SecretKey secretKey) {
        try {
            // Generate a secure random Initialization Vector (IV) for each encryption
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(AES_CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());

            // Prepend the IV to the encrypted payload so the decryptor can extract it
            byte[] cipherMessage = new byte[GCM_IV_LENGTH + encryptedBytes.length];
            System.arraycopy(iv, 0, cipherMessage, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedBytes, 0, cipherMessage, GCM_IV_LENGTH, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(cipherMessage);
        } catch (GeneralSecurityException e) {
            System.out.println("[Crypto]: " + RED + "Error encrypting payload with AES-GCM." + RESET);
            throw new RuntimeException("Encrypting AES error", e);
        }
    }

    /**
     * Decrypts the JSON payload using AES-256-GCM.
     * Extracts the IV from the beginning of the byte array before decrypting.
     */
    public static String decryptAES(String encryptedText, SecretKey secretKey) {
        try {
            byte[] cipherMessage = Base64.getDecoder().decode(encryptedText);

            // Extract the IV from the first 12 bytes
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(cipherMessage, 0, iv, 0, GCM_IV_LENGTH);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            // Extract the actual encrypted payload
            int payloadLength = cipherMessage.length - GCM_IV_LENGTH;
            byte[] encryptedBytes = new byte[payloadLength];
            System.arraycopy(cipherMessage, GCM_IV_LENGTH, encryptedBytes, 0, payloadLength);

            Cipher cipher = Cipher.getInstance(AES_CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            System.out.println("[Crypto]: " + RED + "AES Decryption failed (Data manipulated or corrupted)." + RESET);
            throw new RuntimeException("Decrypting AES error", e);
        }
    }
    // Reconstruct the PublicKey from a Base64 string (used by the client to read the server's key)
    public static PublicKey getPublicKeyFromBase64(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA);
            return keyFactory.generatePublic(spec);
        } catch (NoSuchAlgorithmException | java.security.spec.InvalidKeySpecException | IllegalArgumentException e) {
            System.out.println("[Crypto]: " + RED + "Server public key generating error." + RESET);
            throw new RuntimeException("Public key getting error", e);
        }
    }
}