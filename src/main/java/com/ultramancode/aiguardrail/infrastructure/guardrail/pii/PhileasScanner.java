package com.ultramancode.aiguardrail.infrastructure.guardrail.pii;

import ai.philterd.phileas.PhileasConfiguration;
import ai.philterd.phileas.model.filtering.TextFilterResult;
import ai.philterd.phileas.policy.Identifiers;
import ai.philterd.phileas.policy.Policy;
import ai.philterd.phileas.policy.filters.EmailAddress;
import ai.philterd.phileas.policy.filters.PhoneNumber;
import ai.philterd.phileas.policy.filters.Ssn;
import ai.philterd.phileas.services.context.DefaultContextService;
import ai.philterd.phileas.services.filters.filtering.PlainTextFilterService;
import ai.philterd.phileas.services.strategies.rules.EmailAddressFilterStrategy;
import ai.philterd.phileas.services.strategies.rules.PhoneNumberFilterStrategy;
import ai.philterd.phileas.services.strategies.rules.SsnFilterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses Phileas (Regex-based) to detect structured PII patterns.
 */
@Slf4j
@Service
public class PhileasScanner {

    /**
     * Confidence score for regex-based pattern matching.
     * Regex patterns have high confidence (0.95) because they match exact patterns.
     * Adjust this value to tune deduplication priority against AI-based detection.
     */
    public static final double REGEX_CONFIDENCE_SCORE = 0.95;

    private final PlainTextFilterService filterService;
    private final Policy defaultPolicy;

    public PhileasScanner() throws Exception {
        Properties properties = new Properties();
        PhileasConfiguration phileasConfiguration = new PhileasConfiguration(properties);

        // ContextService is used for managing redaction context
        this.filterService = new PlainTextFilterService(
                phileasConfiguration,
                new DefaultContextService(),
                null // VectorService
        );

        this.defaultPolicy = createDefaultPolicy();
    }

    private Policy createDefaultPolicy() {
        Identifiers identifiers = new Identifiers();

        // SSN
        Ssn ssn = new Ssn();
        ssn.setSsnFilterStrategies(List.of(new SsnFilterStrategy()));
        identifiers.setSsn(ssn);

        // Email
        EmailAddress email = new EmailAddress();
        email.setEmailAddressFilterStrategies(List.of(new EmailAddressFilterStrategy()));
        identifiers.setEmailAddress(email);

        // Phone Number
        PhoneNumber phone = new PhoneNumber();
        phone.setPhoneNumberFilterStrategies(List.of(new PhoneNumberFilterStrategy()));
        identifiers.setPhoneNumber(phone);

        Policy policy = new Policy();
        policy.setName("default-pii-policy");
        policy.setIdentifiers(identifiers);
        return policy;
    }

    public List<PiiSpan> scan(String text) {
        List<PiiSpan> spans = new ArrayList<>();
        
        // 1. Standard Phileas Scan
        try {
            TextFilterResult result = filterService.filter(defaultPolicy, "aiguardrail-context", text);
            result.getExplanation().identifiedSpans().stream()
                    .map(span -> new PiiSpan(
                            span.getFilterType().getType(),
                            span.getCharacterStart(),
                            span.getCharacterEnd(),
                            span.getText(),
                            "PHILEAS",
                            REGEX_CONFIDENCE_SCORE))
                    .forEach(spans::add);
        } catch (Exception e) {
            // Phileas failure is non-critical; manual regex fallback will still run
            log.debug("[PII-SCAN] Phileas engine failed, proceeding with manual regex: {}", e.getMessage());
        }

        // 2. Manual Korean Phone Regex (010-XXXX-XXXX)
        // Since default Phileas might be US-centric
        Pattern korPhone = Pattern.compile("010-\\d{3,4}-\\d{4}");
        Matcher matcher = korPhone.matcher(text);
        
        while (matcher.find()) {
            boolean alreadyFound = spans.stream().anyMatch(s -> s.start() == matcher.start());
            if (!alreadyFound) {
                spans.add(new PiiSpan(
                        "PHONE_NUMBER", // Match Phileas type
                        matcher.start(),
                        matcher.end(),
                        matcher.group(),
                        "PHILEAS-MANUAL",
                        REGEX_CONFIDENCE_SCORE));
            }
        }

        // Logging
        spans.forEach(span -> 
             log.info("[PII-SCAN] Phileas Detected: [{}] - \"{}\" ({})", 
                         span.type(), span.text(), span.source())
        );

        return spans;
    }

    /**
     * Represents a detected PII span with metadata for deduplication.
     *
     * @param type   Entity type (e.g., PHONE_NUMBER, PERSON)
     * @param start  Start character index (inclusive)
     * @param end    End character index (exclusive)
     * @param text   The actual detected text
     * @param source Engine that detected this span (PHILEAS, PHILEAS-MANUAL, PRESIDIO)
     * @param score  Confidence score (0.0 ~ 1.0). Used for priority-based deduplication.
     *               - Phileas (Regex): 0.95 (high confidence for pattern matching)
     *               - Presidio (AI): Actual model confidence score
     */
    public record PiiSpan(String type, int start, int end, String text, String source, double score) {}
}
