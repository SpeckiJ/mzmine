/*
 * Copyright (c) 2004-2025 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.dbscan_clustering;

import io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.dbscan_clustering_optimized.FastDBScan;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FastDBScanTest {


    @Test
    void testBorderpointAtStart() {


    }

    @Test
    void testManual() {
        // 3.75, 12, 13, 15.9, 17, 20.5 are border points as they have <3 neighbors
        // 5 is assigned to second cluster because it is denser with 6.8 because 3.75 only has 2 neighbors
        // 17 is not included in cluster because it only connects to 15.9 which only has 2 neighbors
        // requires 3 neighbors at least
        double[] rawData = {1.2, 1.5, 1.5, 1.5, 1.7, 3.65, 5, 6.8, 6.8, 6.9, 9.1, 9.2, 9.2, 9.3, 10.1,
                12, 13, 15.9, 17, 20.5};

        final List<TestValue> list = Arrays.stream(rawData).mapToObj(TestValue::new).collect(Collectors.toCollection(ArrayList::new));

        FastDBScan<TestValue> dbscan = new FastDBScan<>(2.0, 3, TestValue::value);
        final List<List<TestValue>> clusters = dbscan.clusterSorted(list);

        for (List<TestValue> cluster : clusters) {
            System.out.println(
                    "Cluster: " + cluster.stream().map(Objects::toString).collect(Collectors.joining(", ")));
        }

        assertEquals(3, clusters.size());
        assertEquals(6, clusters.get(0).size());
        assertEquals(4, clusters.get(1).size());
        assertEquals(6, clusters.get(2).size());

        assertEquals(list.subList(0, 6), clusters.get(0));
        assertEquals(list.subList(6, 10), clusters.get(1));
        assertEquals(list.subList(10, 16), clusters.get(2));

    }


    /**
     * We check correctness of the optimized version against the original "correct" one
     */
    @RepeatedTest(100)
    @Disabled
    void testRandom() {

        int test_size = 500_000;
        Random rand = new Random();
        double[] rawData = new double[test_size];
        for (int i = 0; i < test_size; i++) {
            rawData[i] = new BigDecimal(rand.nextFloat() * test_size).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        }

        compareScans(rawData, 2.0f, 3);
        compareScans(rawData, 2.0f, 10);
        compareScans(rawData, 5.0f, 10);
    }

    @Test()
    void testEdgecases() {

        double[] raw = {
                0.98, 2.27, 3.33, 4.65, 6.22, 7.43, 7.65, 8.43, 8.86, 8.90, 10.71, 13.34, 16.21, 18.37, 18.83, 20.18, 21.20, 22.22, 24.52, 24.79, 25.17, 25.70, 26.34, 27.06, 28.66, 32.15, 35.12, 35.14, 35.67, 36.95, 37.23, 37.88, 39.67, 41.52, 42.46, 42.61, 42.91, 43.36, 43.52, 45.02, 45.22, 46.71, 49.86, 50.41, 52.58, 53.23, 54.58, 55.50, 57.63, 57.98, 58.10, 58.11, 58.71, 58.76, 58.99, 60.92, 61.46, 62.17, 62.26, 62.37, 62.62, 63.67, 64.20, 65.22, 65.87, 66.43, 68.03, 69.55, 73.14, 73.41, 73.71, 74.35, 76.47, 76.48, 77.79, 77.80, 79.09, 79.13, 79.30, 79.78, 80.34, 81.84, 82.61, 83.73, 84.79, 86.07, 86.50, 89.03, 89.29, 89.58, 91.89, 93.66, 93.83, 95.11, 95.67, 96.22, 97.42, 97.63, 99.02, 99.97
        };

        double[] raw2 = {
                0.12, 0.42, 2.07, 4.07, 4.28, 5.21, 5.62, 6.72, 8.92, 9.62, 9.73, 10.08, 11.37, 12.42, 13.03, 13.64, 14.23, 16.91, 18.35, 18.60, 19.84, 20.54, 23.16, 24.67, 25.06, 25.20, 25.38, 27.09, 27.47, 30.97, 34.85, 38.03, 38.59, 40.49, 41.00, 41.43, 41.95, 42.34, 44.50, 47.24, 47.57, 48.32, 49.06, 50.46, 50.47, 50.48, 52.13, 52.31, 53.49, 54.30, 55.49, 56.04, 56.52, 58.87, 59.40, 63.49, 65.07, 66.21, 66.40, 67.22, 67.53, 67.94, 68.55, 68.86, 70.45, 73.87, 74.38, 74.71, 76.12, 76.93, 77.48, 77.77, 77.81, 78.16, 78.80, 81.46, 82.41, 84.58, 85.23, 87.28, 87.42, 87.89, 88.62, 89.26, 89.34, 89.41, 89.42, 89.44, 89.51, 89.56, 91.11, 91.34, 91.76, 93.09, 93.60, 93.82, 94.02, 95.07, 96.94, 98.80
        };


        double[] raw3 = {
                1.08, 1.75, 2.28, 3.18, 3.68, 5.29, 7.07, 7.29, 8.09, 9.36, 9.50, 9.54, 11.05, 11.76, 12.01, 12.03, 13.84, 14.66, 15.52, 16.53, 20.92, 22.38, 22.39, 25.24, 28.54, 30.09, 30.92, 31.18, 32.57, 32.96, 33.10, 33.74, 33.92, 34.83, 36.84, 36.97, 39.47, 39.95, 42.17, 42.51, 43.16, 43.90, 44.23, 45.28, 47.63, 47.73, 47.86, 49.02, 49.98, 50.50, 51.84, 52.30, 52.61, 52.76, 53.90, 54.69, 54.83, 57.57, 58.56, 58.72, 60.29, 60.32, 61.76, 61.90, 64.83, 65.76, 66.76, 68.56, 68.69, 69.33, 69.65, 71.65, 72.85, 73.52, 74.00, 78.21, 78.74, 80.96, 81.28, 82.85, 83.58, 83.61, 84.98, 85.82, 87.77, 88.15, 88.63, 88.70, 89.37, 91.10, 92.13, 92.51, 92.56, 93.10, 93.21, 94.61, 95.34, 96.39, 97.14, 97.59
        };

        double[] raw4 = {
                0.46, 1.88, 2.12, 3.23, 4.34, 4.95, 5.19, 5.37, 5.85, 6.04, 7.59, 8.15, 8.53, 10.05, 10.84, 11.48, 12.48, 15.09, 15.78, 16.88, 17.95, 18.95, 20.13, 22.29, 23.53, 24.94, 26.59, 26.77, 26.96, 28.91, 29.79, 30.03, 30.58, 31.37, 33.26, 33.36, 36.86, 38.25, 40.25, 42.10, 43.27, 45.41, 45.84, 46.56, 47.04, 48.08, 48.16, 48.48, 48.64, 52.54, 53.54, 54.52, 54.95, 55.85, 56.90, 58.45, 59.05, 60.97, 63.73, 64.47, 66.77, 66.78, 68.07, 68.59, 69.77, 71.11, 74.67, 75.64, 78.33, 78.36, 78.93, 79.97, 80.48, 81.84, 82.92, 83.53, 83.61, 85.04, 86.41, 88.61, 89.17, 90.77, 90.91, 91.19, 91.77, 92.00, 92.38, 94.26, 95.22, 95.65, 95.81, 96.13, 96.19, 97.17, 97.21, 97.40, 99.42, 99.43, 99.88, 99.96
        };

        double[] raw5 = {
                0.61, 2.13, 2.74, 4.39, 5.61, 7.33, 7.62, 7.83, 8.76, 9.18, 11.02, 11.26, 13.21, 14.99, 15.10, 15.35, 17.27, 17.37, 17.55, 17.85, 20.40, 23.96, 25.59, 25.80, 28.26, 31.62, 32.41, 33.11, 33.39, 33.57, 37.13, 38.00, 39.10, 39.11, 39.86, 39.94, 41.01, 42.05, 44.55, 44.72, 46.15, 48.01, 48.25, 48.95, 49.04, 49.54, 49.90, 51.83, 51.99, 53.76, 53.89, 54.30, 54.73, 54.87, 55.02, 56.21, 57.04, 58.17, 58.58, 60.77, 61.42, 63.15, 63.73, 65.15, 65.41, 65.74, 66.94, 67.29, 68.65, 69.24, 71.69, 72.77, 73.02, 73.55, 73.83, 74.27, 75.38, 78.37, 78.79, 81.35, 81.78, 82.35, 83.21, 83.86, 84.19, 85.38, 85.58, 85.87, 87.55, 87.62, 92.31, 93.09, 93.72, 94.08, 94.61, 95.07, 95.58, 95.80, 96.32, 98.07
        };

        compareScans(raw, 2.0f, 3);
        compareScans(raw2, 2.0f, 3);
        compareScans(raw3, 2.0f, 3);
        compareScans(raw4, 2.0f, 3);
        compareScans(raw5,2.0f, 3);
    }

    private void compareScans(double[] values, double epsilon, int minPoints) {
        long start, end;
        var list = Arrays.stream(values).mapToObj(TestValue::new).collect(Collectors.toCollection(ArrayList::new));

        start = System.currentTimeMillis();
        DBScan<TestValue> dbscan = new DBScan<>(epsilon, minPoints, TestValue::value);
        final List<List<TestValue>> clusters = dbscan.clusterSorted(list);
        end = System.currentTimeMillis();
        System.out.println("DEBUG: dbscan A took " + (end - start) + " MilliSeconds");

        for (List<TestValue> cluster : clusters) {
            System.out.println(
                    "SlowCluster: " + cluster.stream().map(Objects::toString).collect(Collectors.joining(", ")));
        }

        start = System.currentTimeMillis();
        FastDBScan<TestValue> fastdbscan = new FastDBScan<>(epsilon, minPoints, TestValue::value);
        final List<List<TestValue>> fastclusters = fastdbscan.clusterSorted(list);
        end = System.currentTimeMillis();
        System.out.println("DEBUG: fastdbscan took " + (end - start) + " MilliSeconds");

        for (List<TestValue> cluster : fastclusters) {
            System.out.println(
                    "FastCluster: " + cluster.stream().map(Objects::toString).collect(Collectors.joining(", ")));
        }

        // same amount of clusters
        assertEquals(clusters.size(), fastclusters.size());
        for (int i = 0; i < clusters.size(); i++) {
            List<TestValue> refCluster = clusters.get(i);
            List<TestValue> fastCluster = fastclusters.get(i);

            assertEquals(refCluster.size(), fastCluster.size());

            for (int j = 0; j < refCluster.size(); j++) {
                assertEquals(refCluster.get(j), fastCluster.get(j));
            }
        }
    }

    record TestValue(double value) {

        private static final DecimalFormat dfZero = new DecimalFormat("0.00");

        @Override
        public String toString() {
            return dfZero.format(value);
        }
    }
}