package com.o2o.carpooling.ai;

import com.o2o.carpooling.common.domain.MockOcrPolicy;
import com.o2o.carpooling.common.domain.OcrResult;
import com.o2o.carpooling.common.domain.OcrResultMasker;
import com.o2o.carpooling.common.domain.OcrTaskStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interactive mock OCR provider wrapping {@link MockOcrPolicy}. Submit registers a task as
 * PROCESSING; the first poll "finishes" recognition and returns the COMPLETED result with sensitive
 * fields masked ({@link OcrResultMasker}). Active when providers.ocr.type=demo. In-memory task
 * state is single-instance (a real vendor holds this state on its side).
 */
@Component
@ConditionalOnProperty(prefix = "providers.ocr", name = "type", havingValue = "demo")
class DemoOcrProvider implements OcrProvider {

    private final MockOcrPolicy ocrPolicy = new MockOcrPolicy();
    private final Map<String, String> inFlight = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "demo";
    }

    @Override
    public OcrSubmission submit(OcrSubmitCommand command) {
        String providerRef = "demo-ocr-" + command.taskId();
        inFlight.put(providerRef, command.fileObjectId());
        return new OcrSubmission(providerRef, OcrTaskStatus.PROCESSING);
    }

    @Override
    public OcrPollResult poll(String providerRef) {
        String fileObjectId = inFlight.remove(providerRef);
        if (fileObjectId == null) {
            // Unknown/already-consumed ref: nothing to report as newly completed.
            return new OcrPollResult(OcrTaskStatus.PROCESSING, null);
        }
        OcrResult result = OcrResultMasker.maskSensitiveFields(ocrPolicy.inspect(fileObjectId));
        return new OcrPollResult(OcrTaskStatus.COMPLETED, result);
    }
}
