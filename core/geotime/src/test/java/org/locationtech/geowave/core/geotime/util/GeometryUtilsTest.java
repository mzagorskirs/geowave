/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.core.geotime.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geowave.core.geotime.index.dimension.LatitudeDefinition;
import org.locationtech.geowave.core.geotime.index.dimension.LongitudeDefinition;
import org.locationtech.geowave.core.index.IndexMetaData;
import org.locationtech.geowave.core.index.InsertionIds;
import org.locationtech.geowave.core.index.MultiDimensionalCoordinateRanges;
import org.locationtech.geowave.core.index.MultiDimensionalCoordinates;
import org.locationtech.geowave.core.index.NumericIndexStrategy;
import org.locationtech.geowave.core.index.QueryRanges;
import org.locationtech.geowave.core.index.dimension.NumericDimensionDefinition;
import org.locationtech.geowave.core.index.numeric.MultiDimensionalNumericData;
import org.locationtech.geowave.core.store.index.IndexImpl;
import org.locationtech.geowave.core.store.query.constraints.Constraints;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

public class GeometryUtilsTest {
  private final float DELTA = 0;
  private Point point3D;
  private Point point2D;

  @Before
  public void createGeometry() {

    final GeometryFactory gf = new GeometryFactory();

    point2D = gf.createPoint(new Coordinate(1, 2));

    point3D = gf.createPoint(new Coordinate(1, 2, 3));
  }

  @Test
  public void test2DGeometryBinaryConversion() {

    // convert 2D point to binary representation
    final byte[] bytes =
        GeometryUtils.geometryToBinary(point2D, GeometryUtils.MAX_GEOMETRY_PRECISION);

    // load the converted 2D geometry
    final Geometry convGeo =
        GeometryUtils.geometryFromBinary(bytes, GeometryUtils.MAX_GEOMETRY_PRECISION);

    // get the coordinates for each version
    final Coordinate origCoords = point2D.getCoordinates()[0];
    final Coordinate convCoords = convGeo.getCoordinates()[0];

    Assert.assertEquals(origCoords.x, convCoords.x, DELTA);

    Assert.assertEquals(origCoords.y, convCoords.y, DELTA);

    Assert.assertTrue(Double.isNaN(convCoords.getZ()));
  }

  @Test
  public void test3DGeometryBinaryConversion() {

    // convert 3D point to binary representation
    final byte[] bytes =
        GeometryUtils.geometryToBinary(point3D, GeometryUtils.MAX_GEOMETRY_PRECISION);

    // load the converted 3D geometry
    final Geometry convGeo =
        GeometryUtils.geometryFromBinary(bytes, GeometryUtils.MAX_GEOMETRY_PRECISION);

    // get the coordinates for each version
    final Coordinate origCoords = point3D.getCoordinates()[0];
    final Coordinate convCoords = convGeo.getCoordinates()[0];

    Assert.assertEquals(origCoords.x, convCoords.x, DELTA);

    Assert.assertEquals(origCoords.y, convCoords.y, DELTA);

    Assert.assertEquals(origCoords.z, convCoords.z, DELTA);
  }

  @Test
  public void testConstraintGeneration() {

    final GeometryFactory gf = new GeometryFactory();
    final Geometry multiPolygon =
        gf.createMultiPolygon(
            new Polygon[] {
                gf.createPolygon(
                    new Coordinate[] {
                        new Coordinate(20.0, 30),
                        new Coordinate(20, 40),
                        new Coordinate(10, 40),
                        new Coordinate(10, 30),
                        new Coordinate(20, 30)}),
                gf.createPolygon(
                    new Coordinate[] {
                        new Coordinate(-9, -2),
                        new Coordinate(-9, -1),
                        new Coordinate(-8, -1),
                        new Coordinate(-8, -2),
                        new Coordinate(-9, -2)})});
    final Constraints constraints = GeometryUtils.basicConstraintsFromGeometry(multiPolygon);
    final List<MultiDimensionalNumericData> results =
        constraints.getIndexConstraints(new IndexImpl(new ExampleNumericIndexStrategy(), null));
    assertEquals(2, results.size());
    assertTrue(Arrays.equals(new Double[] {10d, 30d}, results.get(0).getMinValuesPerDimension()));
    assertTrue(Arrays.equals(new Double[] {20d, 40d}, results.get(0).getMaxValuesPerDimension()));
    assertTrue(Arrays.equals(new Double[] {-9d, -2d}, results.get(1).getMinValuesPerDimension()));
    assertTrue(Arrays.equals(new Double[] {-8d, -1d}, results.get(1).getMaxValuesPerDimension()));
  }

  GeometryFactory factory = new GeometryFactory();

  @Test
  public void testSplit() {
    final Geometry multiPolygon =
        factory.createMultiPolygon(
            new Polygon[] {
                factory.createPolygon(
                    new Coordinate[] {
                        new Coordinate(179.0, -89),
                        new Coordinate(179.0, -92),
                        new Coordinate(182.0, -92),
                        new Coordinate(192.0, -89),
                        new Coordinate(179.0, -89)})});
    final Geometry result = GeometryUtils.adjustGeo(GeometryUtils.getDefaultCRS(), multiPolygon);

    assertTrue(result.intersects(multiPolygon));
    assertTrue(result.getNumGeometries() == 2);
  }

  @Test
  public void testSimple() {

    final Geometry singlePoly =
        factory.createMultiPolygon(
            new Polygon[] {
                factory.createPolygon(
                    new Coordinate[] {
                        new Coordinate(169.0, 20),
                        new Coordinate(169.0, 21),
                        new Coordinate(172.0, 21),
                        new Coordinate(172.0, 20),
                        new Coordinate(169.0, 20)})});
    final Geometry result = GeometryUtils.adjustGeo(GeometryUtils.getDefaultCRS(), singlePoly);

    assertTrue(result.intersects(singlePoly));
    assertTrue(singlePoly.isValid());
    assertTrue(singlePoly.getNumGeometries() == 1);
  }

  public static class ExampleNumericIndexStrategy implements NumericIndexStrategy {

    @Override
    public byte[] toBinary() {
      return null;
    }

    @Override
    public void fromBinary(final byte[] bytes) {}

    @Override
    public NumericDimensionDefinition[] getOrderedDimensionDefinitions() {
      return new NumericDimensionDefinition[] {new LongitudeDefinition(), new LatitudeDefinition()};
    }

    @Override
    public String getId() {
      return "test-gt";
    }

    @Override
    public double[] getHighestPrecisionIdRangePerDimension() {
      return null;
    }

    @Override
    public List<IndexMetaData> createMetaData() {
      return Collections.emptyList();
    }

    @Override
    public MultiDimensionalCoordinateRanges[] getCoordinateRangesPerDimension(
        final MultiDimensionalNumericData dataRange,
        final IndexMetaData... hints) {
      return null;
    }

    @Override
    public QueryRanges getQueryRanges(
        final MultiDimensionalNumericData indexedRange,
        final IndexMetaData... hints) {
      return null;
    }

    @Override
    public QueryRanges getQueryRanges(
        final MultiDimensionalNumericData indexedRange,
        final int maxEstimatedRangeDecomposition,
        final IndexMetaData... hints) {
      return null;
    }

    @Override
    public InsertionIds getInsertionIds(final MultiDimensionalNumericData indexedData) {
      return null;
    }

    @Override
    public InsertionIds getInsertionIds(
        final MultiDimensionalNumericData indexedData,
        final int maxEstimatedDuplicateIds) {
      return null;
    }

    @Override
    public MultiDimensionalNumericData getRangeForId(
        final byte[] partitionKey,
        final byte[] sortKey) {
      return null;
    }

    @Override
    public byte[][] getQueryPartitionKeys(
        final MultiDimensionalNumericData queryData,
        final IndexMetaData... hints) {
      return null;
    }

    @Override
    public MultiDimensionalCoordinates getCoordinatesPerDimension(
        final byte[] partitionKey,
        final byte[] sortKey) {
      return null;
    }

    @Override
    public byte[][] getInsertionPartitionKeys(final MultiDimensionalNumericData insertionData) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public int getPartitionKeyLength() {
      return 0;
    }
  }
}
