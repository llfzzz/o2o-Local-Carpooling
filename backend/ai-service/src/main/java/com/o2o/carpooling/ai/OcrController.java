package com.o2o.carpooling.ai;

import com.o2o.carpooling.common.domain.OcrResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
class OcrController {

    private final OcrService ocrService;

    OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping("/ocr/mock")
    OcrResult inspect(@RequestBody OcrRequest request) {
        return ocrService.inspectMock(request.fileObjectId());
    }

    record OcrRequest(String fileObjectId) {
    }
}
