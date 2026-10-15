package com.ecomtest.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Date;

@Service
public class JwtService {

    private final byte[] secret;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email) {
        try {
            long now = System.currentTimeMillis();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(email)
                    .issueTime(new Date(now))
                    .expirationTime(new Date(now + expirationMs))
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJWT.sign(new MACSigner(secret));
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    public String extractEmail(String token) {
        return parseAndVerify(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            parseAndVerify(token);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private JWTClaimsSet parseAndVerify(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secret);
            if (!signedJWT.verify(verifier)) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime == null || expirationTime.before(new Date())) {
                throw new IllegalArgumentException("JWT has expired");
            }
            return claims;
        } catch (ParseException | JOSEException e) {
            throw new IllegalArgumentException("Invalid JWT", e);
        }
    }
}
