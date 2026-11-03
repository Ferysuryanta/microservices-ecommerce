package com.fery.microservices.auth.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Bean
    public RSAPrivateKey jwtPrivateKey(
            @Value("${security.jwt.private-key-location}")Resource resource) throws Exception {

        byte[] keyBytes = decodePem(resource, "PRIVATE KEY");

        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    @Bean
    public RSAPublicKey jwtPublicKey(
            @Value("${security.jwt.public-key-location}") Resource resource) throws Exception {

        byte[] keyBytes = decodePem(resource, "PUBLIC KEY");

        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyBytes));
    }

   @Bean
   public RSAKey rsaJwk(
           RSAPublicKey publicKey,
           RSAPrivateKey privateKey,
           @Value("${security.jwt.key-id}") String keyId) {

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(keyId)
                .build();
   }

   @Bean
   public JWKSource<SecurityContext> jwkSource(RSAKey rsaJwk) {

       JWKSet jwkSet = new JWKSet(rsaJwk);
       return new ImmutableJWKSet<>(jwkSet);
   }

   @Bean
   public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
   }

   @Bean
   public JwtDecoder jwtDecoder(
           RSAPublicKey publicKey,
           @Value("${security.jwt.issuer}") String issuer) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(issuer)
        );

        return decoder;
   }


    private static byte[] decodePem(Resource resource, String keyType) throws IOException {

        String pem;

        try (InputStream input = resource.getInputStream()) {
            pem = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String header = "-----BEGIN " + keyType + "-----";
        String footer = "-----END " + keyType + "-----";

        if (!pem.contains(header) || !pem.contains(footer)) {
            throw new IllegalStateException(
                    "Invalid PEM format. Expected " + keyType
            );
        }

        String base64 = pem
                .replace(header, "")
                .replace(footer, "")
                .replaceAll("\\s", "");

        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid Base64 content in " + keyType,
                    ex
            );
        }
    }
}
