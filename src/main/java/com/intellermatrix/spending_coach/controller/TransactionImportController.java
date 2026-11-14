package com.intellermatrix.spending_coach.controller;

import com.intellermatrix.spending_coach.ingestion.dto.ImportSummary;
import com.intellermatrix.spending_coach.ingestion.service.TransactionImportService;
import com.intellermatrix.spending_coach.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TransactionImportController {

    public static final String IMPORT_ENDPOINT = "/api/transactions/import";
    public static final String FILE_PARAM = "file";

    private final TransactionImportService importService;
    private final CurrentUserService currentUserService;

    @PostMapping(IMPORT_ENDPOINT)
    public ImportSummary importTransactions(@RequestParam(FILE_PARAM) MultipartFile file) {
        UUID userId = currentUserService.resolveUserId();
        try {
            return importService.importCsv(userId, file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
