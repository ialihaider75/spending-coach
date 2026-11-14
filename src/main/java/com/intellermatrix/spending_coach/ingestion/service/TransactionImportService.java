package com.intellermatrix.spending_coach.ingestion.service;

import com.intellermatrix.spending_coach.ingestion.dto.CsvParseResult;
import com.intellermatrix.spending_coach.ingestion.dto.ImportSummary;
import com.intellermatrix.spending_coach.persistence.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionImportService {

    private final TransactionCsvParser parser;
    private final TransactionRepository transactionRepository;

    public ImportSummary importCsv(UUID userId, InputStream csv) {
        CsvParseResult result = parser.parse(userId, csv);
        transactionRepository.saveAll(result.parsed());

        List<String> rejectionReasons = result.rejected().stream()
            .map(CsvParseResult.RejectedRow::reason)
            .toList();

        return new ImportSummary(result.parsed().size(), result.rejected().size(), rejectionReasons);
    }
}
