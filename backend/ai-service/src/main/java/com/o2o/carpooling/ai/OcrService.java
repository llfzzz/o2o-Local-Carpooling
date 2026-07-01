package com.o2o.carpooling.ai;

import com.o2o.carpooling.common.domain.OcrResult;
import com.o2o.carpooling.common.domain.OcrTask;
import com.o2o.carpooling.common.domain.OcrTaskStatus;
import com.o2o.carpooling.common.foundation.BusinessException;
import com.o2o.carpooling.common.foundation.ProviderProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns the asynchronous OCR task lifecycle. Recognition is delegated to the selected {@link
 * OcrProvider} (demo wraps the mock policy); this service persists the task as it moves
 * SUBMITTED/PROCESSING → COMPLETED and is the authoritative record of the (masked) result. The
 * provider type is chosen via {@code providers.ocr.type} and fails closed when unconfigured.
 */
@Service
class OcrService {

    private final OcrTaskRepository ocrTaskRepository;
    private final List<OcrProvider> providers;
    private final ProviderProperties providerProperties;
    private final Clock clock;

    OcrService(
        OcrTaskRepository ocrTaskRepository,
        List<OcrProvider> providers,
        ProviderProperties providerProperties,
        Clock clock
    ) {
        this.ocrTaskRepository = ocrTaskRepository;
        this.providers = providers;
        this.providerProperties = providerProperties;
        this.clock = clock;
    }

    /** Submit a document for OCR; returns the task in its initial (non-terminal) state. */
    @Transactional
    OcrTask submit(String fileObjectId) {
        if (!StringUtils.hasText(fileObjectId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "FILE_OBJECT_ID_REQUIRED", "fileObjectId is required");
        }
        OcrProvider provider = provider();
        String taskId = "ocr-" + UUID.randomUUID();
        OcrProvider.OcrSubmission submission = provider.submit(new OcrProvider.OcrSubmitCommand(taskId, fileObjectId));
        OcrTask task = new OcrTask(taskId, fileObjectId, submission.status(), submission.providerRef(), null, clock.instant(), null);
        ocrTaskRepository.save(task);
        return task;
    }

    /** Poll a task; drives it to COMPLETED (persisting the masked result) once the provider is done. */
    @Transactional
    OcrTask get(String taskId) {
        OcrTask task = ocrTaskRepository.findByTaskId(taskId)
            .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "OCR_TASK_NOT_FOUND", "ocr task not found: " + taskId));
        if (task.status().isTerminal()) {
            return task;
        }
        OcrProvider.OcrPollResult poll = provider().poll(task.providerRef());
        if (poll.status() == OcrTaskStatus.COMPLETED && poll.result() != null) {
            ocrTaskRepository.complete(taskId, poll.result(), clock.instant());
            return ocrTaskRepository.findByTaskId(taskId).orElseThrow();
        }
        return task;
    }

    /** Backward-compatible synchronous entry: submit then drive to completion, returning the result. */
    OcrResult inspectMock(String fileObjectId) {
        OcrTask submitted = submit(fileObjectId);
        return get(submitted.taskId()).result();
    }

    private OcrProvider provider() {
        String type = providerProperties.getOcr().getType();
        return providers.stream()
            .filter(candidate -> candidate.name().equalsIgnoreCase(type))
            .findFirst()
            .orElseThrow(() -> new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "OCR_PROVIDER_UNCONFIGURED",
                "no ocr provider configured for type '" + type + "'"));
    }
}
