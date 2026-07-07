package com.o2o.carpooling.ai;

import com.o2o.carpooling.common.domain.OcrResult;
import com.o2o.carpooling.common.domain.OcrTask;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
class OcrController {

    private final OcrService ocrService;

    OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    /** Compatibility: synchronous mock recognition (submit + drive to completion). */
    @PostMapping("/ocr/mock")
    OcrResult inspect(@RequestBody OcrRequest request) {
        return ocrService.inspectMock(request.fileObjectId());
    }

    /** Submit a document for asynchronous OCR; returns the task in its initial state. */
    @PostMapping("/ocr/tasks")
    OcrTask submit(@RequestBody OcrRequest request) {
        return ocrService.submit(request.fileObjectId());
    }

    /** Poll an OCR task; completes it (with the masked result) once the provider is done. */
    @GetMapping("/ocr/tasks/{taskId}")
    OcrTask get(@PathVariable String taskId) {
        return ocrService.get(taskId);
    }

    /** Recent tasks, newest first. Operator-gated at the Gateway (tasks are not user-owned). */
    @GetMapping("/ocr/tasks")
    List<OcrTask> list(@RequestParam(required = false, defaultValue = "20") int limit) {
        return ocrService.listRecent(limit);
    }

    record OcrRequest(String fileObjectId) {
    }
}
