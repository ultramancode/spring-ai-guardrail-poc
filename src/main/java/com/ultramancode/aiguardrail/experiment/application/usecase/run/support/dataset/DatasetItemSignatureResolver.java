package com.ultramancode.aiguardrail.experiment.application.usecase.run.support.dataset;

import com.ultramancode.aiguardrail.common.util.DatasetItemSignatureUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DatasetItemSignatureResolver {

    public String resolve(Map<String, Object> item) {
        return DatasetItemSignatureUtils.resolveSignature(item);
    }
}
