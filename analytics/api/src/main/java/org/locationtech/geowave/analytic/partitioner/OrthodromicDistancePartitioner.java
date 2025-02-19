/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.analytic.partitioner;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.JobContext;
import org.geotools.referencing.CRS;
import org.locationtech.geowave.analytic.GeometryCalculations;
import org.locationtech.geowave.analytic.PropertyManagement;
import org.locationtech.geowave.analytic.ScopedJobConfiguration;
import org.locationtech.geowave.analytic.extract.DimensionExtractor;
import org.locationtech.geowave.analytic.extract.SimpleFeatureGeometryExtractor;
import org.locationtech.geowave.analytic.param.ExtractParameters;
import org.locationtech.geowave.analytic.param.GlobalParameters;
import org.locationtech.geowave.analytic.param.ParameterEnum;
import org.locationtech.geowave.analytic.param.PartitionParameters;
import org.locationtech.geowave.core.geotime.index.dimension.LatitudeDefinition;
import org.locationtech.geowave.core.geotime.index.dimension.LongitudeDefinition;
import org.locationtech.geowave.core.geotime.util.GeometryUtils;
import org.locationtech.geowave.core.index.dimension.NumericDimensionDefinition;
import org.locationtech.geowave.core.index.numeric.BasicNumericDataset;
import org.locationtech.geowave.core.index.numeric.MultiDimensionalNumericData;
import org.locationtech.geowave.core.index.numeric.NumericData;
import org.locationtech.geowave.core.index.numeric.NumericRange;
import org.locationtech.geowave.core.store.dimension.NumericDimensionField;
import org.locationtech.geowave.core.store.index.CommonIndexModel;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.uom.SI;
import tech.units.indriya.unit.Units;

/*
 * Calculates distance use orthodromic distance to calculate the bounding box around each point.
 *
 * The approach is slow and more accurate, resulting in more partitions of smaller size. The class
 * requires {@link CoordinateReferenceSystem} for the distance calculation and {@link
 * DimensionExtractor} to extract geometries and other dimensions.
 *
 * The order of distances provided must match the order or dimensions extracted from the dimension
 * extractor.
 */
public class OrthodromicDistancePartitioner<T> extends AbstractPartitioner<T> implements
    Partitioner<T>,
    java.io.Serializable {

  /** */
  private static final long serialVersionUID = 1L;

  static final Logger LOGGER = LoggerFactory.getLogger(OrthodromicDistancePartitioner.class);

  private Unit<Length> geometricDistanceUnit = SI.METRE;
  private String crsName;
  private transient CoordinateReferenceSystem crs = null;
  private transient GeometryCalculations calculator;
  protected DimensionExtractor<T> dimensionExtractor;
  private int latDimensionPosition;
  private int longDimensionPosition;

  public OrthodromicDistancePartitioner() {}

  public OrthodromicDistancePartitioner(
      final CoordinateReferenceSystem crs,
      final CommonIndexModel indexModel,
      final DimensionExtractor<T> dimensionExtractor,
      final double[] distancePerDimension,
      final Unit<Length> geometricDistanceUnit) {
    super(distancePerDimension);
    this.crs = crs;
    this.crsName = crs.getIdentifiers().iterator().next().toString();
    this.geometricDistanceUnit = geometricDistanceUnit;
    this.dimensionExtractor = dimensionExtractor;
    initIndex(indexModel, distancePerDimension);
  }

  @Override
  protected NumericDataHolder getNumericData(final T entry) {
    final NumericDataHolder numericDataHolder = new NumericDataHolder();

    final Geometry entryGeometry = dimensionExtractor.getGeometry(entry);
    final double otherDimensionData[] = dimensionExtractor.getDimensions(entry);
    numericDataHolder.primary = getNumericData(entryGeometry.getEnvelope(), otherDimensionData);
    final List<Geometry> geometries =
        getGeometries(entryGeometry.getCentroid().getCoordinate(), getDistancePerDimension());
    final MultiDimensionalNumericData[] values = new MultiDimensionalNumericData[geometries.size()];
    int i = 0;
    for (final Geometry geometry : geometries) {
      values[i++] = getNumericData(geometry.getEnvelope(), otherDimensionData);
    }
    numericDataHolder.expansion = values;
    return numericDataHolder;
  }

  private MultiDimensionalNumericData getNumericData(
      final Geometry geometry,
      final double[] otherDimensionData) {
    final NumericDimensionField<?>[] dimensionFields = getIndex().getIndexModel().getDimensions();
    final NumericData[] numericData = new NumericData[dimensionFields.length];
    final double[] distancePerDimension = getDistancePerDimension();
    int otherIndex = 0;

    for (int i = 0; i < dimensionFields.length; i++) {
      final double minValue =
          (i == this.longDimensionPosition) ? geometry.getEnvelopeInternal().getMinX()
              : (i == this.latDimensionPosition ? geometry.getEnvelopeInternal().getMinY()
                  : otherDimensionData[otherIndex] - distancePerDimension[i]);
      final double maxValue =
          (i == this.longDimensionPosition) ? geometry.getEnvelopeInternal().getMaxX()
              : (i == this.latDimensionPosition ? geometry.getEnvelopeInternal().getMaxY()
                  : otherDimensionData[otherIndex] + distancePerDimension[i]);
      if ((i != this.longDimensionPosition) && (i != latDimensionPosition)) {
        otherIndex++;
      }
      numericData[i] = new NumericRange(minValue, maxValue);
    }
    return new BasicNumericDataset(numericData);
  }

  private static int findLongitude(final CommonIndexModel indexModel) {
    return indexOf(indexModel.getDimensions(), LongitudeDefinition.class);
  }

  private static int findLatitude(final CommonIndexModel indexModel) {
    return indexOf(indexModel.getDimensions(), LatitudeDefinition.class);
  }

  private static int indexOf(
      final NumericDimensionField<?> fields[],
      final Class<? extends NumericDimensionDefinition> clazz) {

    for (int i = 0; i < fields.length; i++) {
      if (clazz.isInstance(fields[i].getBaseDefinition())) {
        return i;
      }
    }
    return -1;
  }

  private List<Geometry> getGeometries(
      final Coordinate coordinate,
      final double[] distancePerDimension) {
    return getCalculator().buildSurroundingGeometries(
        new double[] {
            distancePerDimension[longDimensionPosition],
            distancePerDimension[latDimensionPosition]},
        geometricDistanceUnit == null ? Units.METRE : geometricDistanceUnit,
        coordinate);
  }

  private GeometryCalculations getCalculator() {
    if (calculator == null) {
      // this block would only occur in test or in failed initialization
      if (crs == null) {
        try {
          crs = CRS.decode(crsName, true);
        } catch (final FactoryException e) {
          LOGGER.error("CRS not providd and default EPSG:4326 cannot be instantiated", e);
          throw new RuntimeException(e);
        }
      }

      calculator = new GeometryCalculations(crs);
    }
    return calculator;
  }

  @Override
  protected void initIndex(final CommonIndexModel indexModel, final double[] distancePerDimension) {

    longDimensionPosition = findLongitude(indexModel);
    latDimensionPosition = findLatitude(indexModel);

    final List<Geometry> geos = getGeometries(new Coordinate(0, 0), distancePerDimension);

    final Envelope envelope = geos.get(0).getEnvelopeInternal();

    // set up the distances based on geometry (orthodromic distance)
    final double[] distancePerDimensionForIndex = new double[distancePerDimension.length];
    for (int i = 0; i < distancePerDimension.length; i++) {
      distancePerDimensionForIndex[i] =
          (i == longDimensionPosition) ? envelope.getWidth() / 2.0
              : (i == latDimensionPosition ? envelope.getHeight() / 2.0 : distancePerDimension[i]);
      LOGGER.info("Dimension size {} is {} ", i, distancePerDimensionForIndex[i]);
    }

    super.initIndex(indexModel, distancePerDimensionForIndex);
  }

  @Override
  public void initialize(final JobContext context, final Class<?> scope) throws IOException {
    this.initialize(context.getConfiguration(), scope);
  }

  public void initialize(final Configuration configuration, final Class<?> scope)
      throws IOException {
    initialize(new ScopedJobConfiguration(configuration, scope));
  }

  @Override
  public void initialize(final ScopedJobConfiguration config) throws IOException {

    crsName = config.getString(GlobalParameters.Global.CRS_ID, "EPSG:4326");
    try {
      crs = CRS.decode(crsName, true);
    } catch (final FactoryException e) {
      throw new IOException("Cannot find CRS " + crsName, e);
    }

    try {
      dimensionExtractor =
          config.getInstance(
              ExtractParameters.Extract.DIMENSION_EXTRACT_CLASS,
              DimensionExtractor.class,
              SimpleFeatureGeometryExtractor.class);
    } catch (final Exception ex) {
      throw new IOException(
          "Cannot find class for  " + ExtractParameters.Extract.DIMENSION_EXTRACT_CLASS.toString(),
          ex);
    }

    final String distanceUnit =
        config.getString(PartitionParameters.Partition.GEOMETRIC_DISTANCE_UNIT, "m");

    this.geometricDistanceUnit = GeometryUtils.lookup(distanceUnit);

    super.initialize(config);
  }

  @Override
  public Collection<ParameterEnum<?>> getParameters() {
    final Set<ParameterEnum<?>> params = new HashSet<>();
    params.addAll(super.getParameters());
    params.addAll(
        Arrays.asList(
            new ParameterEnum<?>[] {
                PartitionParameters.Partition.GEOMETRIC_DISTANCE_UNIT,
                ExtractParameters.Extract.DIMENSION_EXTRACT_CLASS}));
    return params;
  }

  @Override
  public void setup(
      final PropertyManagement runTimeProperties,
      final Class<?> scope,
      final Configuration configuration) {
    super.setup(runTimeProperties, scope, configuration);
    final ParameterEnum[] params =
        new ParameterEnum[] {
            GlobalParameters.Global.CRS_ID,
            ExtractParameters.Extract.DIMENSION_EXTRACT_CLASS,
            PartitionParameters.Partition.GEOMETRIC_DISTANCE_UNIT};
    runTimeProperties.setConfig(params, configuration, scope);
  }
}
