package com.o2o.carpooling.ai;

import com.o2o.carpooling.common.domain.MockOcrPolicy;
import com.o2o.carpooling.common.domain.OcrResult;
import com.o2o.carpooling.common.domain.OcrResultMasker;
import com.o2o.carpooling.common.domain.OcrTask;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
class OcrService {

    private final MockOcrPolicy ocrPolicy = new MockOcrPolicy();
    private final OcrTaskRepository ocrTaskRepository;

    OcrService(OcrTaskRepository ocrTaskRepository) {
        this.ocrTaskRepository = ocrTaskRepository;
    }

    OcrResult inspectMock(String fileObjectId) {
        OcrResult result = OcrResultMasker.maskSensitiveFields(ocrPolicy.inspect(fileObjectId));
        ocrTaskRepository.save(new OcrTask("ocr-" + UUID.randomUUID(), fileObjectId, result, Instant.now()));
        return result;
    }
}
