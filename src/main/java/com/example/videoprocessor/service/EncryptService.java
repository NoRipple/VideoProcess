package com.example.videoprocessor.service;

import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Classname: Encrypt
 * Package: com.example.videoprocessor.service
 * Description:
 *
 * @Author: No_Ripple(吴波)
 * @Creat： - 16:01
 * @Version: v1.0
 */
@Service
public class EncryptService {
    public byte[] encryptBytes(byte[] chunk) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey secretKey = keyGenerator.generateKey(); // 获取秘钥
        // TODO 指定秘钥而非随机生成
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(chunk);
    }
    public byte[] decryptBytes(byte[] encryptChunk, SecretKey secretKey) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(encryptChunk);
    }

}
