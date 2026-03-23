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
import com.ultramancode.aiguardrail.guardrail.application.port.out.PiiAnalyzerPort;
import com.ultramancode.aiguardrail.guardrail.domain.PiiSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 정규식 기반의 Phileas를 사용하여 구조화된 PII 패턴을 탐지합니다.
 */
@Slf4j
@Service
public class PhileasScanner implements PiiAnalyzerPort {

    /**
     * 정규식 기반 패턴 매칭에 대한 신뢰 점수입니다.
     * 정규식 패턴은 정확한 패턴과 일치하므로 높은 신뢰도(0.95)를 가집니다.
     * 이 값을 조정하여 AI 기반 탐지와의 중복 제거 우선순위를 튜닝할 수 있습니다.
     */
    public static final double REGEX_CONFIDENCE_SCORE = 0.95;
    private static final String SOURCE_PHILEAS = "PHILEAS";
    private static final String SOURCE_PHILEAS_MANUAL = "PHILEAS-MANUAL";
    private static final String DEFAULT_CONTEXT = "aiguardrail-context";
    private static final Pattern KOR_PHONE_PATTERN = Pattern.compile("010-\\d{3,4}-\\d{4}");
    private final PlainTextFilterService filterService;
    private final Policy defaultPolicy;
    private final boolean traceRawContent;

    public PhileasScanner(
            @Value("${guardrail.pii.trace-raw-content:false}") boolean traceRawContent
    ) {
        this.traceRawContent = traceRawContent;
        Properties properties = new Properties();
        PhileasConfiguration phileasConfiguration = new PhileasConfiguration(properties);

        // redaction 컨텍스트 관리를 위한 ContextService 사용
        this.filterService = new PlainTextFilterService(
                phileasConfiguration,
                new DefaultContextService(),
                null // VectorService 미사용
        );

        this.defaultPolicy = createDefaultPolicy();
    }

    private Policy createDefaultPolicy() {
        Identifiers identifiers = new Identifiers();

        // 주민등록번호(SSN)
        Ssn ssn = new Ssn();
        ssn.setSsnFilterStrategies(List.of(new SsnFilterStrategy()));
        identifiers.setSsn(ssn);

        // 이메일
        EmailAddress email = new EmailAddress();
        email.setEmailAddressFilterStrategies(List.of(new EmailAddressFilterStrategy()));
        identifiers.setEmailAddress(email);

        // 전화번호
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

    public List<PiiSpan> scan(String text) {
        List<PiiSpan> spans = new ArrayList<>();

        // 1. 기본 Phileas 스캔
        performPhileasScan(text, spans);

        // 2. 한국 전화번호 수동 정규식 스캔 (010-XXXX-XXXX)
        performManualRegexScan(text, spans);

        // 로그 기록
        for (PiiSpan span : spans) {
            if (traceRawContent) {
                log.info(
                        "[PII-SCAN] Phileas Detected: [{}] - \"{}\" ({})",
                        span.type(),
                        span.text(),
                        span.source()
                );
            } else {
                log.info(
                        "[PII-SCAN] Phileas Detected: [{}] [{}~{}] ({})",
                        span.type(),
                        span.start(),
                        span.end(),
                        span.source()
                );
            }
        }

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
            // Phileas filter()가 checked exception을 선언하므로 여기서만 포착 후 수동 정규식으로 축소합니다.
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
