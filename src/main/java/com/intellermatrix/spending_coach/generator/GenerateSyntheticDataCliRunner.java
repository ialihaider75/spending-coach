package com.intellermatrix.spending_coach.generator;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GenerateSyntheticDataCliRunner implements CommandLineRunner {

    private static final String GENERATE_FLAG = "--generate-data";
    private static final String SEED_PREFIX = "--seed=";
    private static final String OUT_PREFIX = "--out=";

    private final SyntheticDataGenerator generator;

    @Override
    public void run(String @NonNull ... args) {
        if (Arrays.stream(args).noneMatch(GENERATE_FLAG::equals)) {
            return;
        }

        long seed = argValue(args, SEED_PREFIX)
            .map(Long::parseLong)
            .orElseThrow(() -> new IllegalArgumentException(SEED_PREFIX + " is required with " + GENERATE_FLAG));

        String outPath = argValue(args, OUT_PREFIX)
            .orElseThrow(() -> new IllegalArgumentException(OUT_PREFIX + " is required with " + GENERATE_FLAG));

        String csv = generator.generateCsv(seed, LocalDate.now(ZoneOffset.UTC));
        try {
            Files.writeString(Path.of(outPath), csv);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<String> argValue(String[] args, String prefix) {
        return Arrays.stream(args)
            .filter(arg -> arg.startsWith(prefix))
            .map(arg -> arg.substring(prefix.length()))
            .findFirst();
    }
}
