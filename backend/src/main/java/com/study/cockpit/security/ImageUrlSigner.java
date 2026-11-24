package com.study.cockpit.security;

import com.study.cockpit.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 图片临时访问地址签名（安全要求 10）。
 *
 * 地址形如 /api/images/{id}/raw?exp=<unix秒>&sig=<HMAC>，无状态、不落库。
 * exp 由服务端生成，sig = HMAC-SHA256(FILE_TOKEN_SECRET, imageId|userId|exp)。
 * 图片记录被删除或 15 天到期后，后端查库失败即 404，旧地址自然失效。
 * 校验使用 MessageDigest.isEqual 做常量时间比较，防时序侧信道。
 */
@Component
@RequiredArgsConstructor
public class ImageUrlSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final AppProperties props;

    public long nextExpSeconds() {
        return Instant.now().getEpochSecond() + props.getFileTokenTtlSeconds();
    }

    public String sign(long imageId, long userId, long expSeconds) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(imageId + "|" + userId + "|" + expSeconds));
    }

    public boolean verify(long imageId, long userId, long expSeconds, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        // 过期直接拒绝，避免对过期签名做无谓计算
        if (expSeconds < Instant.now().getEpochSecond()) {
            return false;
        }
        byte[] expected = sign(imageId, userId, expSeconds).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(props.getFileTokenSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("图片地址签名不可用", e);
        }
    }
}
