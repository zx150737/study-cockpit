package com.study.cockpit.security;

import com.study.cockpit.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与验签（安全要求 2）。
 * - HS256 签名防篡改，密钥来自环境变量且 >= 32 字节
 * - 短有效期（默认 30 分钟）
 * - 每个令牌携带唯一 jti，注销后加入黑名单防复用
 * - 携带 ver（用户 token_version），改密后旧令牌立即失效
 * - 防重放依靠 HTTPS + 短 exp + jti 黑名单 + 服务端版本号校验；
 *   不采用"一次一密"，避免前端并发请求互相把对方令牌挤掉。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AppProperties props;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(props.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发访问令牌 */
    public String issue(Long userId, String username, int tokenVersion) {
        long now = System.currentTimeMillis();
        String jti = UUID.randomUUID().toString().replace("-", "");
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("ver", tokenVersion)
                .id(jti)
                .issuedAt(new Date(now))
                .expiration(new Date(now + props.getJwtTtlMinutes() * 60_000L))
                .signWith(key())
                .compact();
    }

    /** 解析并验签；签名错误/过期/格式错误均抛出 JwtException */
    public JwtClaims parse(String token) {
        Claims c = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Number ver = c.get("ver", Number.class);
        return new JwtClaims(
                Long.valueOf(c.getSubject()),
                c.get("username", String.class),
                ver == null ? 0 : ver.intValue(),
                c.getId(),
                c.getExpiration().getTime());
    }

    public record JwtClaims(Long userId, String username, int ver, String jti, long expMillis) {
    }
}
