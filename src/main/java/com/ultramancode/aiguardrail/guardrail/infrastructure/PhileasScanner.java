package com.ultramancode.aiguardrail.guardrail.infrastructure;

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
import com.ultramancode.aiguardrail.guardrail.domain.PiiSpan;
import com.ultramancode.aiguardrail.guardrail.application.port.out.PiiAnalyzerPort;
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
public class PhileasScanner implements PiiAnalyzerPort {

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

    @Override
    public List<PiiSpan> analyze(String text) {
        return scan(text);
    }

    private static final String SOURCE_PHILEAS = "PHILEAS";
    private static final String SOURCE_PHILEAS_MANUAL = "PHILEAS-MANUAL";
    private static final String DEFAULT_CONTEXT = "aiguardrail-context";
    private static final Pattern KOR_PHONE_PATTERN = Pattern.compile("010-\\d{3,4}-\\d{4}");

    public List<PiiSpan> scan(String text) {
        List<PiiSpan> spans = new ArrayList<>();

        // 1. Standard Phileas Scan
        performPhileasScan(text, spans);

        // 2. Manual Korean Phone Regex (010-XXXX-XXXX)
        performManualRegexScan(text, spans);

        // Logging
        spans.forEach(span ->
                log.info("[PII-SCAN] Phileas Detected: [{}] - \"{}\" ({})",
                        span.type(), span.text(), span.source())
        );

        return spans;
    }

    private void performPhileasScan(String text, List<PiiSpan> spans) {
        try {
            TextFilterResult result = filterService.filter(defaultPolicy, DEFAULT_CONTEXT, text);
            result.getExplanation().identifiedSpans().stream()
                    .map(span -> new PiiSpan(
                            span.getFilterType().getType(),
                            span.getCharacterStart(),
                            span.getCharacterEnd(),
                            span.getText(),
                            SOURCE_PHILEAS,
                            REGEX_CONFIDENCE_SCORE))
                    .forEach(spans::add);
        } catch (Exception e) {
            log.debug("[PII-SCAN] Phileas engine failed, proceeding with manual regex: {}", e.getMessage());
        }
    }

    private void performManualRegexScan(String text, List<PiiSpan> spans) {
        Matcher matcher = KOR_PHONE_PATTERN.matcher(text);

        while (matcher.find()) {
            final int start = matcher.start();
            boolean alreadyFound = spans.stream().anyMatch(s -> s.start() == start);
            if (!alreadyFound) {
                spans.add(new PiiSpan(
                        "PHONE_NUMBER",
                        start,
                        matcher.end(),
                        matcher.group(),
                        SOURCE_PHILEAS_MANUAL,
                        REGEX_CONFIDENCE_SCORE));
            }
        }
    }
}
