package com.intellermatrix.spending_coach.generator.models;

import java.util.Random;

public record SeedParameters(long salary, long rent, long schoolFee, long groceryBase, double groceryGrowth,
                              long diningBaseline, int spikeOrderCount, long spikeExcess, String oneOffCategory,
                              long oneOffAmount, long subscriptionStart, long subscriptionIncrease,
                              long duplicateAmount, int crossingDayTarget) {

    private static final String[] ONE_OFF_CATEGORIES = {"repairs", "medical", "travel"};

    public static SeedParameters from(Random random) {
        return new SeedParameters(
            randomLong(random, 100000, 140000),
            randomLong(random, 35000, 55000),
            randomLong(random, 20000, 35000),
            randomLong(random, 20000, 30000),
            groceryGrowth(random),
            randomLong(random, 3000, 6000),
            random.nextInt(7) + 10,
            randomLong(random, 8000, 16000),
            ONE_OFF_CATEGORIES[random.nextInt(ONE_OFF_CATEGORIES.length)],
            randomLong(random, 15000, 22000),
            randomLong(random, 900, 1300),
            randomLong(random, 200, 400),
            randomLong(random, 2500, 4500),
            random.nextInt(5) + 22);
    }

    private static long randomLong(Random random, long min, long max) {
        return min + random.nextLong(max - min + 1);
    }

    private static double groceryGrowth(Random random) {
        double minGrowth = 0.03;
        double maxGrowth = 0.05;
        return minGrowth + random.nextDouble() * (maxGrowth - minGrowth);
    }
}
