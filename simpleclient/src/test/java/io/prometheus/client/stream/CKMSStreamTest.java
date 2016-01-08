package io.prometheus.client.stream;


import org.junit.Test;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;


public class CKMSStreamTest {

    @Test
    public void testQuantileApproximation() {
        final int maxSize = 1000;
        final Quantile[] q = new Quantile[]{new Quantile(0.2, 0.05), new Quantile(0.5, 0.1), new Quantile(0.9, 0.01),
                new Quantile(0.99, 0.001)};

        Stream<Double> stream = new CKMSStream<Double>(500, q);
        ArrayList<Double> dataPoints = new ArrayList<Double>(maxSize);

        Random random = new Random();
        for (int j = 0; j < 2; j++) {
            for (int i = 1; i < maxSize; i++) {
                int m = random.nextInt(100);
                if (i % 2 == 0) {
                    m = m * m + 1;
                }
                m = i;
                dataPoints.add((double) m);
                stream.insert((double) m);
            }

            Collections.sort(dataPoints);
            for (Quantile targetedQuantile : q) {
                int n = dataPoints.size();
                int k = (int) (targetedQuantile.getQuantile() * n);
                int lower = (int) Math.floor((targetedQuantile.getQuantile() - targetedQuantile.getError()) * n);

                if (lower < 1) {
                    lower = 1;
                }

                int upper = (int) Math.ceil((targetedQuantile.getQuantile() + targetedQuantile.getError()) * n);

                double min = dataPoints.get(lower - 1);
                double max = dataPoints.get(upper - 1);
                Double g = stream.query(targetedQuantile.getQuantile());

                assertTrue(g>min);
                assertTrue(g<max);

            }
            stream.reset();
            dataPoints.clear();
        }

    }
}
