/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.analytic.mapreduce.clustering.runner;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.ObjectWritable;
import org.apache.hadoop.mapreduce.Job;
import org.locationtech.geowave.analytic.PropertyManagement;
import org.locationtech.geowave.analytic.SimpleFeatureProjection;
import org.locationtech.geowave.analytic.clustering.CentroidManagerGeoWave;
import org.locationtech.geowave.analytic.clustering.NestedGroupCentroidAssignment;
import org.locationtech.geowave.analytic.mapreduce.GeoWaveAnalyticJobRunner;
import org.locationtech.geowave.analytic.mapreduce.GeoWaveOutputFormatConfiguration;
import org.locationtech.geowave.analytic.mapreduce.clustering.ConvexHullMapReduce;
import org.locationtech.geowave.analytic.param.CentroidParameters;
import org.locationtech.geowave.analytic.param.GlobalParameters;
import org.locationtech.geowave.analytic.param.HullParameters;
import org.locationtech.geowave.analytic.param.MapReduceParameters;
import org.locationtech.geowave.analytic.param.ParameterEnum;
import org.locationtech.geowave.analytic.param.StoreParameters;
import org.locationtech.geowave.core.geotime.index.SpatialDimensionalityTypeProvider;
import org.locationtech.geowave.core.geotime.index.SpatialOptions;
import org.locationtech.geowave.mapreduce.input.GeoWaveInputKey;
import org.locationtech.geowave.mapreduce.output.GeoWaveOutputKey;

/** */
public class ConvexHullJobRunner extends GeoWaveAnalyticJobRunner {

  private int zoomLevel = 1;

  public ConvexHullJobRunner() {
    super.setOutputFormatConfiguration(new GeoWaveOutputFormatConfiguration());
  }

  public void setZoomLevel(final int zoomLevel) {
    this.zoomLevel = zoomLevel;
  }

  @Override
  public void configure(final Job job) throws Exception {
    job.setMapperClass(ConvexHullMapReduce.ConvexHullMap.class);
    job.setMapOutputKeyClass(GeoWaveInputKey.class);
    job.setMapOutputValueClass(ObjectWritable.class);
    job.setReducerClass(ConvexHullMapReduce.ConvexHullReducer.class);
    job.setReduceSpeculativeExecution(false);
    job.setOutputKeyClass(GeoWaveOutputKey.class);
    job.setOutputValueClass(Object.class);
  }

  @Override
  public Class<?> getScope() {
    return ConvexHullMapReduce.class;
  }

  @Override
  public int run(final Configuration config, final PropertyManagement runTimeProperties)
      throws Exception {

    runTimeProperties.storeIfEmpty(
        HullParameters.Hull.PROJECTION_CLASS,
        SimpleFeatureProjection.class);
    runTimeProperties.setConfig(
        new ParameterEnum<?>[] {
            HullParameters.Hull.WRAPPER_FACTORY_CLASS,
            HullParameters.Hull.PROJECTION_CLASS,
            HullParameters.Hull.DATA_TYPE_ID,
            HullParameters.Hull.INDEX_NAME},
        config,
        getScope());
    setReducerCount(runTimeProperties.getPropertyAsInt(HullParameters.Hull.REDUCER_COUNT, 4));
    CentroidManagerGeoWave.setParameters(config, getScope(), runTimeProperties);
    NestedGroupCentroidAssignment.setParameters(config, getScope(), runTimeProperties);

    final int localZoomLevel =
        runTimeProperties.getPropertyAsInt(CentroidParameters.Centroid.ZOOM_LEVEL, zoomLevel);
    // getting group from next level, now that the prior level is complete
    NestedGroupCentroidAssignment.setZoomLevel(config, getScope(), localZoomLevel + 1);

    addDataAdapter(
        config,
        getAdapter(
            runTimeProperties,
            HullParameters.Hull.DATA_TYPE_ID,
            HullParameters.Hull.DATA_NAMESPACE_URI));
    checkIndex(
        runTimeProperties,
        HullParameters.Hull.INDEX_NAME,
        SpatialDimensionalityTypeProvider.createIndexFromOptions(new SpatialOptions()).getName());
    // HP Fortify "Command Injection" false positive
    // What Fortify considers "externally-influenced input"
    // comes only from users with OS-level access anyway
    return super.run(config, runTimeProperties);
  }

  @Override
  public Collection<ParameterEnum<?>> getParameters() {
    final Set<ParameterEnum<?>> params = new HashSet<>();
    params.addAll(super.getParameters());

    params.addAll(
        Arrays.asList(
            new ParameterEnum<?>[] {
                StoreParameters.StoreParam.INPUT_STORE,
                StoreParameters.StoreParam.OUTPUT_STORE,
                GlobalParameters.Global.BATCH_ID}));

    params.addAll(MapReduceParameters.getParameters());
    params.addAll(NestedGroupCentroidAssignment.getParameters());

    params.addAll(
        Arrays.asList(
            new ParameterEnum<?>[] {
                HullParameters.Hull.WRAPPER_FACTORY_CLASS,
                HullParameters.Hull.PROJECTION_CLASS,
                HullParameters.Hull.REDUCER_COUNT,
                HullParameters.Hull.DATA_TYPE_ID,
                HullParameters.Hull.DATA_NAMESPACE_URI,
                HullParameters.Hull.INDEX_NAME}));
    return params;
  }

  @Override
  protected String getJobName() {
    return "Convex Hull";
  }
}
