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

package io.github.mzmine.modules.dataprocessing.featdet_spectraldeconvolutiongc.dbscan_clustering_optimized;

import org.apache.commons.collections.list.UnmodifiableList;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.ToDoubleFunction;



/**
 * For internal use only!
 * @param point original point to be mapped
 * @param leftmostNeighborIndex index of the leftmost neighbor
 * @param rightmostNeighborIndex index of the rightmost neighbor
 * @param leftClusterId id of the closest cluster left of this value
 * @param <TVALUE>
 */
record BorderPoint<TVALUE>(TVALUE point, int leftmostNeighborIndex, int rightmostNeighborIndex, int leftClusterId) {}

/**
 * Optimized Density-based clustering algorithm.
 * <p>
 * Use as:
 * {@snippet :
 * List<Feature> features = new ArrayList<>(); // needs content
 * DBScan<ModularFeature> dbScan = new FastDBScan<>( minNumberOfSignals, ModularFeature::getRT);
 * dbScan.cluster(features);
 *}
 *
 * @param <TVALUE>
 */
public class FastDBScan<TVALUE> {

    private final double epsilon;
    private final int minPts;
    private final @NotNull ToDoubleFunction<TVALUE> valueFunction;


    public FastDBScan(final double epsilon, final int minPts,
                      final @NotNull ToDoubleFunction<TVALUE> valueFunction) {
        this.epsilon = epsilon;
        this.minPts = minPts;
        this.valueFunction = valueFunction;
    }

    /**
     * Cluster and sort result. Clusters will be sorted by value and also internally
     */
    public List<List<TVALUE>> clusterSorted(List<TVALUE> list) {
        return cluster(list);
    }
    /*
    public List<List<TVALUE>> cluster(UnmodifiableList<TVALUE> list) {
        Collections.unmodifiableList(list);

        return cluster(list);
    }
    */


    public List<List<TVALUE>> cluster(List<TVALUE> unsortedPoints) {
        /*
        var sorted = unsortedPoints.stream()
                .sorted()
                .toList();
         */
        unsortedPoints.sort(Comparator.comparingDouble(valueFunction));
        var sorted = unsortedPoints;

        List<List<TVALUE>> clusters = new ArrayList<>();
        List<BorderPoint<TVALUE>> borderPoints = new ArrayList<>();


        int leftmostNeighborIndex = 0;
        int rightmostNeighborIndex = 1;
        ArrayList<TVALUE> cluster = new ArrayList<>();

        // Cluster based on epsilon distance in a single pass
        // Schedule borderpoints (points with < minPts neighbors) for later handling
        for (int i = 0; i < sorted.size(); i++) {
            TVALUE current = sorted.get(i);
            double val = valueFunction.applyAsDouble(current);
            boolean movedRight = false;

            // recalculate neighborhood borders
            while (val - valueFunction.applyAsDouble(sorted.get(leftmostNeighborIndex)) > epsilon) {
                leftmostNeighborIndex++;
            }
            while (rightmostNeighborIndex < sorted.size() &&
                    valueFunction.applyAsDouble(sorted.get(rightmostNeighborIndex)) - val <= epsilon) {
                rightmostNeighborIndex++;
                movedRight = true;
            }

            // check if we are border point - defer handling until all clusters are set
            if (rightmostNeighborIndex - leftmostNeighborIndex - 1 < minPts) {
                // our neighborhood is too small, we are border point
                if (!cluster.isEmpty()) {

                    if (movedRight) {
                        // border points automatically end the current cluster
                        clusters.add(cluster);
                        cluster = new ArrayList<>();
                        borderPoints.add(new BorderPoint<>(
                                current,
                                leftmostNeighborIndex,
                                rightmostNeighborIndex,
                                clusters.size() - 1));
                    } else {
                        // We are a border point but deeply embedded in a cluster
                        cluster.add(current);
                    }
                } else {
                    // queue for handling later
                    borderPoints.add(new BorderPoint<>(
                            current,
                            leftmostNeighborIndex,
                            rightmostNeighborIndex,
                            clusters.size() - 1));
                }

            } else {
                // we have no left neighbors, end the current cluster and start a new one
                if (leftmostNeighborIndex == i) {
                    if (!cluster.isEmpty()) {
                        clusters.add(cluster);
                        cluster = new ArrayList<>();
                    }
                }
                cluster.add(current);
            }
        }

        // add last cluster
        if (!cluster.isEmpty()) {
            clusters.add(cluster);
        }


        // Calculate the borderpoint -> cluster assignments
        // We cannot modify the cluster directly as this would impact subsequent calculations
        HashMap<Integer, List<TVALUE>[]> bpAssigments = new HashMap<>();
        for (int i = 0; i < clusters.size(); i++) {
            bpAssigments.put(i, new List[]{new ArrayList<TVALUE>(), new ArrayList<TVALUE>()});
        }

        for (BorderPoint<TVALUE> borderPoint : borderPoints) {
            int maxN = 0;
            TVALUE densestNeighbor = null;

            // Iterate all neighbors and search for the one with most neighbors
            for (int i = borderPoint.leftmostNeighborIndex(); i < borderPoint.rightmostNeighborIndex(); i++) {
                var neighbor = sorted.get(i);
                var neighborVal = valueFunction.applyAsDouble(neighbor);
                int left = i;
                int right = i;
                while (neighborVal - valueFunction.applyAsDouble(sorted.get(left)) <= epsilon) {
                    left--;
                    if (left < 0) {
                        break;
                    }
                }
                while (valueFunction.applyAsDouble(sorted.get(right)) - neighborVal <= epsilon) {
                    right++;
                    if (right >= sorted.size()) {
                        break;
                    }
                }

                if (right - left - 2 > maxN) {
                    maxN = right - left - 2;
                    densestNeighbor = neighbor;
                }
            }

            // no neighbors are part of a cluster - the all have too small neighborhoods
            if (maxN < minPts) {
                continue;
            }

            // if neighbor is smaller than us he must be in the left cluster
            if (valueFunction.applyAsDouble(densestNeighbor) < valueFunction.applyAsDouble(borderPoint.point())) {
                bpAssigments.computeIfPresent(borderPoint.leftClusterId(), (_, val) -> {
                    val[0].add(borderPoint.point());
                    return val;
                });
            } else {
                bpAssigments.computeIfPresent(borderPoint.leftClusterId() + 1, (_, val) -> {
                    val[1].add(borderPoint.point());
                    return val;
                });
            }
        }

        // Apply the borderpoint mapping by addind the
        bpAssigments.forEach((clusterId, vals) -> {
            vals[0].forEach(prependedbp ->
                    clusters.get(clusterId).addLast(prependedbp)
            );
            vals[1].reversed().forEach(appendedbp ->
                    clusters.get(clusterId).addFirst(appendedbp)
            );
        });

        return clusters;
    }
}


