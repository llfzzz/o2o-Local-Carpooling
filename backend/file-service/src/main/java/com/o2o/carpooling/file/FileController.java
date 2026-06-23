package com.o2o.carpooling.file;

import com.o2o.carpooling.common.domain.FileObject;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
class FileController {

    private final FileObjectService fileObjectService;

    FileController(FileObjectService fileObjectService) {
        this.fileObjectService = fileObjectService;
    }

    @PostMapping("/mock-upload")
    FileObject mockUpload(@RequestBody MockUploadRequest request) {
        return fileObjectService.createPrivateObject(request.ownerId(), request.objectName(), request.contentType());
    }

    @PostMapping("/presign-upload")
    FileObject presignUpload(@RequestBody MockUploadRequest request) {
        return fileObjectService.createPrivateObject(request.ownerId(), request.objectName(), request.contentType());
    }

    record MockUploadRequest(String ownerId, String objectName, String contentType) {
    }
}
