package com.intellermatrix.spending_coach.controller;

import com.intellermatrix.spending_coach.ingestion.dto.ImportSummary;
import com.intellermatrix.spending_coach.ingestion.service.TransactionImportService;
import com.intellermatrix.spending_coach.persistence.entity.User;
import com.intellermatrix.spending_coach.persistence.repository.UserRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class TransactionImportController {

    public static final String IMPORT_ENDPOINT = "/api/transactions/import";
    public static final String FILE_PARAM = "file";

    private static final String DEFAULT_USER_DISPLAY_NAME = "Default User";

    private final TransactionImportService importService;
    private final UserRepository userRepository;

    public TransactionImportController(TransactionImportService importService, UserRepository userRepository) {
        this.importService = importService;
        this.userRepository = userRepository;
    }

    @PostMapping(IMPORT_ENDPOINT)
    public ImportSummary importTransactions(@RequestParam(FILE_PARAM) MultipartFile file) {
        UUID userId = resolveUserId();
        try {
            return importService.importCsv(userId, file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UUID resolveUserId() {
        List<User> users = userRepository.findAll();
        if (!users.isEmpty()) {
            return users.getFirst().getId();
        }
        return userRepository.save(new User(UUID.randomUUID(), DEFAULT_USER_DISPLAY_NAME, Instant.now())).getId();
    }
}
