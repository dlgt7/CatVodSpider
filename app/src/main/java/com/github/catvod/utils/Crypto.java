package com.github.catvod.utils;

import android.util.Base64;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {

    private static final String DEFAULT_CHARSET = "UTF-8";
    
    public static String md5(String src) {
        return md5(src, DEFAULT_CHARSET);
    }

    public static String md5(String src, String charset) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(src.getBytes(charset));
            BigInteger no = new BigInteger(1, messageDigest);
            // 使用 String.format 补齐 32 位更优雅且不易出错
            return String.format("%032x", no);
        } catch (Exception e) {
            return "";
        }
    }

    public static String CBC(String src, String KEY, String IV) {
        return CBC(src, KEY, IV, "PKCS5Padding");
    }
    
    public static String CBC(String src, String KEY, String IV, String padding) {
        try {
            String transformation = "AES/CBC/" + padding;
            Cipher cipher = Cipher.getInstance(transformation);
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(DEFAULT_CHARSET), "AES");
            AlgorithmParameterSpec paramSpec = new IvParameterSpec(IV.getBytes(DEFAULT_CHARSET));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
            byte[] decrypted = cipher.doFinal(Base64.decode(src, Base64.DEFAULT));
            return new String(decrypted, DEFAULT_CHARSET);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String aesEncrypt(String data, String key, String iv) throws Exception {
        return aesEncrypt(data, key, iv, "PKCS5Padding", Base64.NO_PADDING);
    }
    
    public static String aesEncrypt(String data, String key, String iv, String padding, int base64Flags) throws Exception {
        String transformation = "AES/CBC/" + padding;
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(DEFAULT_CHARSET), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(DEFAULT_CHARSET));
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        byte[] encrypted = cipher.doFinal(data.getBytes(DEFAULT_CHARSET));
        return Base64.encodeToString(encrypted, base64Flags);
    }

    public static String rsaEncrypt(String data, String publicKeyPem) throws Exception {
        String publicKeyPEM = publicKeyPem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\s+", "");
        byte[] decoded = Base64.decode(publicKeyPEM, Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(spec);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(data.getBytes(DEFAULT_CHARSET));
        return Base64.encodeToString(encrypted, Base64.DEFAULT);
    }

    public static String rsaDecrypt(String encryptedKey, String privateKeyPem) throws Exception {
        String privateKeyPEM = privateKeyPem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\s", "");
        byte[] privateKeyBytes = Base64.decode(privateKeyPEM, Base64.DEFAULT);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decrypted = cipher.doFinal(Base64.decode(encryptedKey, Base64.DEFAULT));
        return new String(decrypted, DEFAULT_CHARSET);
    }
    
    public static String rsaEncryptLong(String data, String publicKeyPem) throws Exception {
        String publicKeyPEM = publicKeyPem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\s+", "");
        byte[] decoded = Base64.decode(publicKeyPEM, Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(spec);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        
        // 分段加密
        byte[] dataBytes = data.getBytes(DEFAULT_CHARSET);
        int keyLength = publicKey.getModulus().bitLength();
        int blockSize = keyLength / 8 - 11; // PKCS1Padding 需要 11 字节
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < dataBytes.length; i += blockSize) {
            int end = Math.min(i + blockSize, dataBytes.length);
            byte[] block = java.util.Arrays.copyOfRange(dataBytes, i, end);
            byte[] encryptedBlock = cipher.doFinal(block);
            if (i > 0) result.append("|"); // 使用 | 分隔各个加密块
            result.append(Base64.encodeToString(encryptedBlock, Base64.NO_PADDING));
        }
        
        return result.toString();
    }
    
    public static String rsaDecryptLong(String encryptedData, String privateKeyPem) throws Exception {
        String privateKeyPEM = privateKeyPem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\s", "");
        byte[] privateKeyBytes = Base64.decode(privateKeyPEM, Base64.DEFAULT);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        
        // 分段解密
        String[] blocks = encryptedData.split("\\|");
        StringBuilder result = new StringBuilder();
        
        int keyLength = privateKey.getModulus().bitLength();
        int blockSize = keyLength / 8; // RSA 解密块大小
        
        for (String block : blocks) {
            byte[] encryptedBlock = Base64.decode(block, Base64.DEFAULT);
            byte[] decryptedBlock = cipher.doFinal(encryptedBlock);
            result.append(new String(decryptedBlock, DEFAULT_CHARSET));
        }
        
        return result.toString();
    }

    public static String randomKey(int size) {
        StringBuilder key = new StringBuilder();
        String keys = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < size; i++) key.append(keys.charAt(random.nextInt(keys.length())));
        return key.toString();
    }
    
    public static String aesEcbEncrypt(String data, String key) throws Exception {
        return aesEcbEncrypt(data, key, "PKCS5Padding");
    }
    
    public static String aesEcbEncrypt(String data, String key, String padding) throws Exception {
        String transformation = "AES/ECB/" + padding;
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(DEFAULT_CHARSET), "AES");
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(data.getBytes(DEFAULT_CHARSET));
        return Base64.encodeToString(encrypted, Base64.NO_PADDING);
    }
    
    public static String aesEcbDecrypt(String src, String key) throws Exception {
        return aesEcbDecrypt(src, key, "PKCS5Padding");
    }
    
    public static String aesEcbDecrypt(String src, String key, String padding) throws Exception {
        String transformation = "AES/ECB/" + padding;
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(DEFAULT_CHARSET), "AES");
        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decrypted = cipher.doFinal(Base64.decode(src, Base64.DEFAULT));
        return new String(decrypted, DEFAULT_CHARSET);
    }
    
    public static String sha1(String src) {
        return sha1(src, DEFAULT_CHARSET);
    }
    
    public static String sha1(String src, String charset) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA1");
            byte[] messageDigest = sha1.digest(src.getBytes(charset));
            BigInteger no = new BigInteger(1, messageDigest);
            // 使用 String.format 补齐 40 位更优雅且不易出错
            return String.format("%040x", no);
        } catch (Exception e) {
            return "";
        }
    }
}
