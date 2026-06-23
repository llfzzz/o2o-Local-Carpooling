package com.o2o.carpooling.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
class FieldEncryptionService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec keySpec;

    FieldEncryptionService(@Value("${security.field-encryption-key-base64}") String keyBase64) {
        byte[] key = Base64.getDecoder().decode(keyBase64);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException("field encryption key must decode to 16, 24, or 32 bytes");
        }
        this.keySpec = new SecretKeySpec(key, "AES");
    }

    byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(iv, IV_LENGTH + encrypted.length);
            System.arraycopy(encrypted, 0, payload, IV_LENGTH, encrypted.length);
            return payload;
        } catch (Exception exception) {
            throw new IllegalStateException("failed to encrypt field", exception);
        }
    }

    String decrypt(byte[] payload) {
        try {
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("failed to decrypt field", exception);
        }
    }
}
