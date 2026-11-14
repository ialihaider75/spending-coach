package com.intellermatrix.spending_coach.controller;

import com.intellermatrix.spending_coach.IntegrationTestSupport;
import com.intellermatrix.spending_coach.ingestion.dto.ImportSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class TransactionImportControllerIT extends IntegrationTestSupport {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void importEndpointReturnsSummary() {
        var file = new ByteArrayResource("date,amount,category,note,kind\n2026-10-01,120000,salary,Employer,income\n".getBytes()) {
            @Override
            public String getFilename() {
                return "transactions.csv";
            }
        };
        var body = new LinkedMultiValueMap<String, Object>();
        body.add(TransactionImportController.FILE_PARAM, file);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        var response = rest.postForEntity(TransactionImportController.IMPORT_ENDPOINT,
            new HttpEntity<>(body, headers), ImportSummary.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().importedCount()).isEqualTo(1);
    }
}
