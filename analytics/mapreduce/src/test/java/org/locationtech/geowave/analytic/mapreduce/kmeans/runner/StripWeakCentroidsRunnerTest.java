/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.analytic.mapreduce.kmeans.runner;

import static org.junit.Assert.assertEquals;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.apache.hadoop.conf.Configuration;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.geowave.analytic.AnalyticItemWrapper;
import org.locationtech.geowave.analytic.PropertyManagement;
import org.locationtech.geowave.analytic.clustering.CentroidManager;
import org.locationtech.geowave.analytic.clustering.LongCentroid;
import org.locationtech.geowave.analytic.clustering.exception.MatchingCentroidNotFoundException;
import org.locationtech.geowave.analytic.mapreduce.kmeans.runner.StripWeakCentroidsRunner.MaxChangeBreakStrategy;
import org.locationtech.geowave.analytic.mapreduce.kmeans.runner.StripWeakCentroidsRunner.StableChangeBreakStrategy;
import org.locationtech.geowave.analytic.mapreduce.kmeans.runner.StripWeakCentroidsRunner.TailMaxBreakStrategy;
import org.locationtech.geowave.analytic.mapreduce.kmeans.runner.StripWeakCentroidsRunner.TailStableChangeBreakStrategy;
import org.locationtech.geowave.core.geotime.index.SpatialDimensionalityTypeProvider;
import org.locationtech.geowave.core.geotime.index.SpatialOptions;
import org.locationtech.jts.geom.Coordinate;

public class StripWeakCentroidsRunnerTest {
  @Test
  public void testStable() throws Exception {
    final StripWeakCentroidsRunnerForTest testObj = new StripWeakCentroidsRunnerForTest(60, 62);
    testObj.setBreakStrategy(new StableChangeBreakStrategy<Long>());
    testObj.run(new Configuration(), new PropertyManagement());
  }

  @Test
  public void testStable1() throws Exception {

    final List<AnalyticItemWrapper<Long>> list = new ArrayList<>();
    final int cnts[] = new int[] {1000, 851, 750, 650, 525, 200, 100, 90, 70};
    for (int i = 0; i < cnts.length; i++) {
      list.add(new LongCentroid(i, "", cnts[i]));
    }
    final StableChangeBreakStrategy<Long> breakS = new StableChangeBreakStrategy<>();
    assertEquals(5, breakS.getBreakPoint(list));
  }

  @Test
  public void testStableUniform() throws Exception {

    final List<AnalyticItemWrapper<Long>> list = new ArrayList<>();
    final int cnts[] = new int[] {1000, 851, 750, 650, 525, 200, 100, 90, 70};
    for (int i = 0; i < cnts.length; i++) {
      list.add(new LongCentroid(i, "", cnts[i]));
    }
    final TailStableChangeBreakStrategy<Long> breakS = new TailStableChangeBreakStrategy<>();
    assertEquals(5, breakS.getBreakPoint(list));
  }

  @Test
  public void testMaxDense() throws Exception {

    final List<AnalyticItemWrapper<Long>> list = new ArrayList<>();
    final int cnts[] = new int[] {900, 600, 800,};
    for (int i = 0; i < cnts.length; i++) {
      list.add(new LongCentroid(i, "", cnts[i]));
    }
    final TailMaxBreakStrategy<Long> breakS = new TailMaxBreakStrategy<>();
    assertEquals(3, breakS.getBreakPoint(list));
  }

  @Test
  public void testMaxUniform() throws Exception {

    final List<AnalyticItemWrapper<Long>> list = new ArrayList<>();
    final int cnts[] = new int[] {1000, 851, 750, 650, 525, 200, 90, 70};
    for (int i = 0; i < cnts.length; i++) {
      list.add(new LongCentroid(i, "", cnts[i]));
    }
    final TailMaxBreakStrategy<Long> breakS = new TailMaxBreakStrategy<>();
    assertEquals(5, breakS.getBreakPoint(list));
  }

  @Test
  public void testCliffMean() throws Exception {
    final StripWeakCentroidsRunnerForTest testObj = new StripWeakCentroidsRunnerForTest(79, 81);
    testObj.setBreakStrategy(new MaxChangeBreakStrategy<Long>());
    testObj.run(new Configuration(), new PropertyManagement());
  }

  @Test
  public void testCliff() throws Exception {
    final StripWeakCentroidsRunnerForTestOne testObj = new StripWeakCentroidsRunnerForTestOne();
    testObj.run(new Configuration(), new PropertyManagement());
  }

  private static class StripWeakCentroidsRunnerForTest extends StripWeakCentroidsRunner<Long> {
    private final List<AnalyticItemWrapper<Long>> testSet;
    private final int min;
    private final int max;

    StripWeakCentroidsRunnerForTest(final int min, final int max) {
      super();
      this.min = min;
      this.max = max;
      testSet = load();
    }

    @Override
    protected CentroidManager<Long> constructCentroidManager(
        final Configuration config,
        final PropertyManagement runTimeProperties) throws IOException {
      return new CentroidManager<Long>() {

        @Override
        public AnalyticItemWrapper<Long> createNextCentroid(
            final Long feature,
            final String groupID,
            final Coordinate coordinate,
            final String[] extraNames,
            final double[] extraValues) {
          return new LongCentroid(feature, groupID, 1);
        }

        @Override
        public void clear() {}

        @Override
        public void delete(final String[] dataIds) throws IOException {
          Assert.assertTrue(dataIds.length + "<=" + max, dataIds.length <= max);
          Assert.assertTrue(dataIds.length + ">=" + min, dataIds.length >= min);
        }

        @Override
        public List<String> getAllCentroidGroups() throws IOException {
          return Arrays.asList("1");
        }

        @Override
        public List<AnalyticItemWrapper<Long>> getCentroidsForGroup(final String groupID)
            throws IOException {
          Assert.assertEquals("1", groupID);
          return testSet;
        }

        @Override
        public List<AnalyticItemWrapper<Long>> getCentroidsForGroup(
            final String batchID,
            final String groupID) throws IOException {
          Assert.assertEquals("1", groupID);
          return testSet;
        }

        @Override
        public int processForAllGroups(
            final org.locationtech.geowave.analytic.clustering.CentroidManager.CentroidProcessingFn<Long> fn)
            throws IOException {

          return fn.processGroup("1", testSet);
        }

        @Override
        public AnalyticItemWrapper<Long> getCentroid(final String id) {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public String getDataTypeName() {
          return "centroid";
        }

        @Override
        public String getIndexName() {
          return SpatialDimensionalityTypeProvider.createIndexFromOptions(
              new SpatialOptions()).getName();
        }

        @Override
        public AnalyticItemWrapper<Long> getCentroidById(final String id, final String groupID)
            throws IOException, MatchingCentroidNotFoundException {
          Assert.assertEquals("1", groupID);
          throw new MatchingCentroidNotFoundException(id);
        }
      };
    }

    private List<AnalyticItemWrapper<Long>> load() {
      final Random rand = new Random(2331);
      int begin = 100000000;
      final List<AnalyticItemWrapper<Long>> centroids = new ArrayList<>();
      for (int i = 0; i <= 100; i++) {
        if ((i > 0) && ((i % 20) == 0)) {
          begin /= (Math.pow(100, i / 20));
        }
        centroids.add(new LongCentroid(i, "", (int) (Math.abs(rand.nextDouble() * 10000) + begin)));
      }
      return centroids;
    }
  }

  private static class StripWeakCentroidsRunnerForTestOne extends StripWeakCentroidsRunner<Long> {

    private final List<AnalyticItemWrapper<Long>> testSet =
        Arrays.asList((AnalyticItemWrapper<Long>) new LongCentroid(1L, "", 22));

    StripWeakCentroidsRunnerForTestOne() {
      super();
    }

    @Override
    protected CentroidManager<Long> constructCentroidManager(
        final Configuration config,
        final PropertyManagement runTimeProperties) throws IOException {
      return new CentroidManager<Long>() {

        @Override
        public AnalyticItemWrapper<Long> createNextCentroid(
            final Long feature,
            final String groupID,
            final Coordinate coordinate,
            final String[] extraNames,
            final double[] extraValues) {
          return new LongCentroid(feature, groupID, 1);
        }

        @Override
        public void clear() {}

        @Override
        public void delete(final String[] dataIds) throws IOException {
          Assert.assertFalse(true);
        }

        @Override
        public List<String> getAllCentroidGroups() throws IOException {
          return Arrays.asList("1");
        }

        @Override
        public List<AnalyticItemWrapper<Long>> getCentroidsForGroup(final String groupID)
            throws IOException {
          Assert.assertEquals("1", groupID);
          return testSet;
        }

        @Override
        public List<AnalyticItemWrapper<Long>> getCentroidsForGroup(
            final String batchID,
            final String groupID) throws IOException {
          Assert.assertEquals("1", groupID);
          return testSet;
        }

        @Override
        public int processForAllGroups(
            final org.locationtech.geowave.analytic.clustering.CentroidManager.CentroidProcessingFn<Long> fn)
            throws IOException {

          return fn.processGroup("1", testSet);
        }

        @Override
        public AnalyticItemWrapper<Long> getCentroid(final String id) {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public String getDataTypeName() {
          return "centroid";
        }

        @Override
        public String getIndexName() {
          return SpatialDimensionalityTypeProvider.createIndexFromOptions(
              new SpatialOptions()).getName();
        }

        @Override
        public AnalyticItemWrapper<Long> getCentroidById(final String id, final String groupID)
            throws IOException, MatchingCentroidNotFoundException {
          Assert.assertEquals("1", groupID);
          throw new MatchingCentroidNotFoundException(id);
        }
      };
    }
  }
}
