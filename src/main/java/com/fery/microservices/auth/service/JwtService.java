package com.fery.microservices.auth.service;

import com.fery.microservices.auth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final String keyId;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.access-token-ttl}") Duration accessTokenTtl,
            @Value("${security.jwt.key-id}") String keyId) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
        this.keyId = keyId;
    }



    public String generateAccessToken(User user) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", user.getRole())
                .build();

        JwsHeader header = JwsHeader
                .with(SignatureAlgorithm.RS256)
                .keyId(keyId)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    public long getAccessTokenExpirySeconds() {
        return accessTokenTtl.getSeconds();
    }
}
