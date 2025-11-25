package com.golfbeta.aws;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.cookie.CookiesForCustomPolicy;
import software.amazon.awssdk.services.cloudfront.url.SignedUrl;

@Component
public class CloudFrontSignedUrlService {

    private static final Logger log = LoggerFactory.getLogger(CloudFrontSignedUrlService.class);
    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN [^-]+-----([\\s\\S]+?)-----END [^-]+-----",
            Pattern.MULTILINE
    );

    private final CloudFrontUtilities utilities = CloudFrontUtilities.create();
    private final PrivateKey privateKey;
    private final String keyPairId;
    private final String distributionDomain;
    private final String rawPrivateKey;

    public CloudFrontSignedUrlService(
            @Value("${aws.cloudfront.domain}") String distributionDomain,
            @Value("${aws.cloudfront.key-pair-id}") String keyPairId,
            @Value("${aws.cloudfront.private-key-base64}") String privateKeyBase64
    ) {
        if (!StringUtils.hasText(distributionDomain)) {
            throw new IllegalStateException("CloudFront distribution domain is not configured");
        }
        if (!StringUtils.hasText(keyPairId)) {
            throw new IllegalStateException("CloudFront key pair ID is not configured");
        }
        if (!StringUtils.hasText(privateKeyBase64)) {
            throw new IllegalStateException("CloudFront private key is not configured");
        }

        this.rawPrivateKey = privateKeyBase64.trim();
        this.distributionDomain = distributionDomain.trim();
        this.keyPairId = keyPairId.trim();
        this.privateKey = parsePrivateKey(this.rawPrivateKey);
    }

    public String generateSignedUrl(String objectKey, Duration lifetime) {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }

        String normalisedKey = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        String resourceUrl = "https://" + distributionDomain + "/" + normalisedKey;
        Instant expiresAt = Instant.now().plus(lifetime);

        SignedUrl signedUrl = utilities.getSignedUrlWithCannedPolicy(builder -> builder
                .resourceUrl(resourceUrl)
                .keyPairId(keyPairId)
                .privateKey(privateKey)
                .expirationDate(expiresAt)
        );

        return signedUrl.url();
    }

    public Map<String, String> generateSignedCookies(String resourcePrefix, Duration lifetime) {
        if (!StringUtils.hasText(resourcePrefix)) {
            throw new IllegalArgumentException("resourcePrefix must not be blank");
        }
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }

        String trimmed = resourcePrefix.startsWith("/") ? resourcePrefix.substring(1) : resourcePrefix;
        String candidate = "https://" + distributionDomain + "/" + trimmed;
        if (!candidate.endsWith("*")) {
            if (!candidate.endsWith("/")) {
                candidate = candidate + "/";
            }
            candidate = candidate + "*";
        }
        final String resourceUrl = candidate;

        Instant expiresAt = Instant.now().plus(lifetime);
        CookiesForCustomPolicy cookies = utilities.getCookiesForCustomPolicy(builder -> builder
                .resourceUrl(resourceUrl)
                .keyPairId(keyPairId)
                .privateKey(privateKey)
                .expirationDate(expiresAt)
        );

        Map<String, String> values = new LinkedHashMap<>();
        values.put("CloudFront-Policy", cookies.policyHeaderValue());
        values.put("CloudFront-Signature", cookies.signatureHeaderValue());
        values.put("CloudFront-Key-Pair-Id", cookies.keyPairIdHeaderValue());
        return values;
    }

    private static PrivateKey parsePrivateKey(String privateKeyBase64) {
        try {
            byte[] initial = Base64.getDecoder().decode(privateKeyBase64);
            byte[] keyBytes = extractKeyBytes(initial);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            try {
                return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            } catch (InvalidKeySpecException pkcs1) {
                RSAPrivateKey rsaPrivateKey = RSAPrivateKey.getInstance(ASN1Sequence.getInstance(keyBytes));
                PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(
                        new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE),
                        rsaPrivateKey
                );
                byte[] pkcs8 = privateKeyInfo.getEncoded();
                return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
            }
        } catch (IOException | IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("Unable to parse CloudFront private key", e);
        }
    }

    private static byte[] extractKeyBytes(byte[] decoded) {
        String candidate = new String(decoded, StandardCharsets.UTF_8);
        Matcher matcher = PEM_BLOCK.matcher(candidate);
        if (matcher.find()) {
            String body = matcher.group(1).replaceAll("[^A-Za-z0-9+/=]", "");
            return Base64.getDecoder().decode(body);
        }
        return decoded;
    }
}
