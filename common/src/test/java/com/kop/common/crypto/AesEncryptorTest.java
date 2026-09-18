package com.kop.common.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AesEncryptorTest {

    AesEncryptor aesEncryptor = new AesEncryptor(
        "local-dev-only-change-me-0123456789abcdef", "d1b1d1e1f1a1b1c1d1e1f1a1b1c1d1e1"
    );

    @Test
    void encrypt한_값을_decrypt하면_원문이_나온다() {
        String ciphertext = aesEncryptor.encrypt("110-1234-567890");

        assertThat(ciphertext).isNotEqualTo("110-1234-567890");
        assertThat(aesEncryptor.decrypt(ciphertext)).isEqualTo("110-1234-567890");
    }

    @Test
    void null을_encrypt하면_null을_반환한다() {
        assertThat(aesEncryptor.encrypt(null)).isNull();
    }

    @Test
    void null을_decrypt하면_null을_반환한다() {
        assertThat(aesEncryptor.decrypt(null)).isNull();
    }

    @Test
    void 같은_평문도_매번_다른_암호문을_만든다() {
        String first = aesEncryptor.encrypt("110-1234-567890");
        String second = aesEncryptor.encrypt("110-1234-567890");

        assertThat(first).isNotEqualTo(second);
    }
}
