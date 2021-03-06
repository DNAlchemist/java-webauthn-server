/* Copyright 2015 Yubico */

package com.yubico.u2f.attestation;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.base.Predicates;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.io.CharStreams;
import com.google.common.io.Closeables;
import com.yubico.u2f.attestation.matchers.ExtensionMatcher;
import com.yubico.u2f.attestation.matchers.FingerprintMatcher;
import com.yubico.u2f.attestation.resolvers.SimpleResolver;
import com.yubico.u2f.exceptions.U2fBadConfigurationException;
import com.yubico.u2f.exceptions.U2fBadInputException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataService {
    private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);

    public static final String SELECTORS = "selectors";
    private static final String SELECTOR_TYPE = "type";
    private static final String SELECTOR_PARAMETERS = "parameters";

    private static final String TRANSPORTS = "transports";
    private static final String TRANSPORTS_EXT_OID = "1.3.6.1.4.1.45724.2.1.1";

    public static final Map<String, DeviceMatcher> DEFAULT_DEVICE_MATCHERS = ImmutableMap.of(
            ExtensionMatcher.SELECTOR_TYPE, new ExtensionMatcher(),
            FingerprintMatcher.SELECTOR_TYPE, new FingerprintMatcher()
    );

    private static MetadataResolver createDefaultMetadataResolver() {
        SimpleResolver resolver = new SimpleResolver();
        InputStream is = null;
        try {
            is = MetadataService.class.getResourceAsStream("/metadata.json");
            resolver.addMetadata(CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8)));
        } catch (IOException e) {
            logger.error("createDefaultMetadataResolver failed", e);
        } catch (CertificateException e) {
            logger.error("createDefaultMetadataResolver failed", e);
        } catch (U2fBadConfigurationException e) {
            logger.error("createDefaultMetadataResolver failed", e);
        } finally {
            Closeables.closeQuietly(is);
        }
        return resolver;
    }

    private final Attestation unknownAttestation = new Attestation(null, null, null, null);
    private final MetadataResolver resolver;
    private final Map<String, DeviceMatcher> matchers = new HashMap<String, DeviceMatcher>();
    private final Cache<String, Attestation> cache;

    public MetadataService(MetadataResolver resolver, Cache<String, Attestation> cache, Map<String, ? extends DeviceMatcher> matchers) {
        this.resolver = resolver != null ? resolver : createDefaultMetadataResolver();
        this.cache = cache != null ? cache : CacheBuilder.newBuilder().<String, Attestation>build();
        if (matchers == null) {
            matchers = DEFAULT_DEVICE_MATCHERS;
        }
        this.matchers.putAll(matchers);
    }

    public MetadataService() {
        this(null, null, null);
    }

    public MetadataService(MetadataResolver resolver) {
        this(resolver, null, null);
    }

    private boolean deviceMatches(JsonNode selectors, X509Certificate attestationCertificate) {
        if (selectors != null && !selectors.isNull()) {
            for (JsonNode selector : selectors) {
                DeviceMatcher matcher = matchers.get(selector.get(SELECTOR_TYPE).asText());
                if (matcher != null && matcher.matches(attestationCertificate, selector.get(SELECTOR_PARAMETERS))) {
                    return true;
                }
            }
            return false;
        }
        return true; //Match if selectors is null or missing.
    }

    public Attestation getCachedAttestation(String attestationCertificateFingerprint) {
        return cache.getIfPresent(attestationCertificateFingerprint);
    }

    public Attestation getAttestation(final X509Certificate attestationCertificate) {
        if (attestationCertificate == null) {
            return unknownAttestation;
        }
        try {
            String fingerprint = Hashing.sha1().hashBytes(attestationCertificate.getEncoded()).toString();
            return cache.get(fingerprint, new Callable<Attestation>() {
                @Override
                public Attestation call() {
                    return lookupAttestation(attestationCertificate);
                }
            });
        } catch (Exception e) {
            return unknownAttestation;
        }
    }

    /**
     * Attempt to look up attestation for a chain of certificates
     *
     * <p>
     * This method will return the first non-unknown result, if any, of calling
     * {@link #getAttestation(X509Certificate)} with each of the certificates
     * in <code>attestationCertificateChain</code> in order, while also
     * verifying that the next attempted certificate has signed the previous
     * certificate.
     * </p>
     *
     * @param attestationCertificateChain a certificate chain, where each
     *          certificate in the list should be signed by the following certificate.
     * @return The first non-unknown result, if any, of calling {@link
     *           #getAttestation(X509Certificate)} for each of the certificates
     *           in the <code>attestationCertificateChain</code>. If the chain
     *           of signatures is broken before finding such a result, an
     *           unknown attestation is returned.
     */
    public Attestation getAttestation(List<X509Certificate> attestationCertificateChain) {

        Iterator<X509Certificate> it = attestationCertificateChain.iterator();
        if (it.hasNext() == false) {
            return unknownAttestation;
        }

        X509Certificate cert = it.next();
        Attestation resolvedInitial = getAttestation(cert);

        if (resolvedInitial.isTrusted()) {
            return resolvedInitial;
        } else {
            while (it.hasNext()) {
                Attestation resolved = getAttestation(cert);

                if (resolved != null) {
                    return resolved;
                } else {
                    logger.trace("Could not look up attestation for certificate [{}] - trying next element in certificate chain.", cert);

                    X509Certificate signingCert = it.next();

                    try {
                        cert.verify(signingCert.getPublicKey());
                    } catch (Exception e) {
                        logger.debug("Failed to verify that certificate [{}] was signed by certificate [{}].", cert, signingCert, e);
                        return unknownAttestation;
                    }
                }
            }

            return unknownAttestation;
        }
    }

    private Attestation lookupAttestation(X509Certificate attestationCertificate) {
        MetadataObject metadata = resolver.resolve(attestationCertificate);
        Map<String, String> vendorProperties = null;
        Map<String, String> deviceProperties = null;
        String identifier = null;
        int transports = 0;
        if (metadata != null) {
            identifier = metadata.getIdentifier();
            vendorProperties = Maps.filterValues(metadata.getVendorInfo(), Predicates.notNull());
            for (JsonNode device : metadata.getDevices()) {
                if (deviceMatches(device.get(SELECTORS), attestationCertificate)) {
                    JsonNode transportNode = device.get(TRANSPORTS);
                    if(transportNode != null) {
                        transports = transportNode.asInt(0);
                    }
                    ImmutableMap.Builder<String, String> devicePropertiesBuilder = ImmutableMap.builder();
                    for (Map.Entry<String, JsonNode> deviceEntry : Lists.newArrayList(device.fields())) {
                        JsonNode value = deviceEntry.getValue();
                        if (value.isTextual()) {
                            devicePropertiesBuilder.put(deviceEntry.getKey(), value.asText());
                        }
                    }
                    deviceProperties = devicePropertiesBuilder.build();
                    break;
                }
            }
        }

        transports |= get_transports(attestationCertificate.getExtensionValue(TRANSPORTS_EXT_OID));

        return new Attestation(identifier, vendorProperties, deviceProperties, Transport.fromInt(transports));
    }

    private int get_transports(byte[] extensionValue) {
        if(extensionValue == null) {
            return 0;
        }

        // Mask out unused bits (shouldn't be needed as they should already be 0).
        int unusedBitMask = 0xff;
        for(int i=0; i < extensionValue[3]; i++) {
            unusedBitMask <<= 1;
        }
        extensionValue[extensionValue.length-1] &= unusedBitMask;

        int transports = 0;
        for(int i=extensionValue.length - 1; i >= 5; i--) {
            byte b = extensionValue[i];
            for(int bi=0; bi < 8; bi++) {
                transports = (transports << 1) | (b & 1);
                b >>= 1;
            }
        }

        return transports;
    }
}
