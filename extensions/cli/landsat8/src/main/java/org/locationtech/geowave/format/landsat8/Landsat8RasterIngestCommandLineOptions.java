/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.format.landsat8;

import org.locationtech.geowave.adapter.raster.adapter.RasterDataAdapter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.IntegerConverter;

public class Landsat8RasterIngestCommandLineOptions {
  @Parameter(
      names = "--histogram",
      arity = 1,
      description = "An option to store the histogram of the values of the coverage so that histogram equalization will be performed")
  protected boolean histogram = false;

  @Parameter(
      names = "--pyramid",
      arity = 1,
      description = "An option to store an image pyramid for the coverage")
  protected boolean pyramid = false;

  @Parameter(
      names = "--retainimages",
      arity = 1,
      description = "An option to keep the images that are ingested in the local workspace directory.  By default it will delete the local file after it is ingested successfully.")
  protected boolean retainimages = false;

  @Parameter(
      names = "--tilesize",
      description = "The option to set the pixel size for each tile stored in GeoWave. The default is "
          + RasterDataAdapter.DEFAULT_TILE_SIZE)
  protected int tilesize = RasterDataAdapter.DEFAULT_TILE_SIZE;

  @Parameter(
      names = "--coverage",
      description = "The name to give to each unique coverage. Freemarker templating can be used for variable substition based on the same attributes used for filtering.  The default coverage name is '${"
          + SceneFeatureIterator.ENTITY_ID_ATTRIBUTE_NAME
          + "}_${"
          + BandFeatureIterator.BAND_ATTRIBUTE_NAME
          + "}'.  If ${band} is unused in the coverage name, all bands will be merged together into the same coverage.")
  protected String coverage =
      "${"
          + SceneFeatureIterator.ENTITY_ID_ATTRIBUTE_NAME
          + "}_${"
          + BandFeatureIterator.BAND_ATTRIBUTE_NAME
          + "}";

  @Parameter(
      names = "--converter",
      description = "Prior to ingesting an image, this converter will be used to massage the data. The default is not to convert the data.")
  protected String coverageConverter;

  @Parameter(
      names = "--subsample",
      description = "Subsample the image prior to ingest by the scale factor provided.  The scale factor should be an integer value greater than 1.",
      converter = IntegerConverter.class)
  protected int scale = 1;

  @Parameter(
      names = "--crop",
      arity = 1,
      description = "Use the spatial constraint provided in CQL to crop the image.  If no spatial constraint is provided, this will not have an effect.")
  protected boolean cropToSpatialConstraint;

  @Parameter(
      names = "--skipMerge",
      arity = 1,
      description = "By default the ingest will automerge overlapping tiles as a post-processing optimization step for efficient retrieval, but this will skip the merge process")
  protected boolean skipMerge;

  public Landsat8RasterIngestCommandLineOptions() {}

  public boolean isCreateHistogram() {
    return histogram;
  }

  public boolean isCreatePyramid() {
    return pyramid;
  }

  public boolean isRetainImages() {
    return retainimages;
  }

  public String getCoverageName() {
    return coverage;
  }

  public String getCoverageConverter() {
    return coverageConverter;
  }

  public boolean isCoveragePerBand() {
    // technically the coverage will be per band if it contains any of the
    // band attribute names, but realistically the band name should be the
    // only one used
    return coverage.contains("${" + BandFeatureIterator.BAND_ATTRIBUTE_NAME + "}")
        || coverage.contains("${" + BandFeatureIterator.BAND_DOWNLOAD_ATTRIBUTE_NAME + "}")
        || coverage.contains("${" + BandFeatureIterator.SIZE_ATTRIBUTE_NAME + "}");
  }

  public int getTileSize() {
    return tilesize;
  }

  public boolean isSubsample() {
    return (scale > 1);
  }

  public int getScale() {
    return scale;
  }

  public boolean isCropToSpatialConstraint() {
    return cropToSpatialConstraint;
  }

  public void setCreateHistogram(final boolean createHistogram) {
    this.histogram = createHistogram;
  }

  public void setCreatePyramid(final boolean createPyramid) {
    this.pyramid = createPyramid;
  }

  public void setRetainImages(final boolean retainImages) {
    this.retainimages = retainImages;
  }

  public void setTileSize(final int tileSize) {
    this.tilesize = tileSize;
  }

  public void setCoverageName(final String coverageName) {
    this.coverage = coverageName;
  }

  public void setCoverageConverter(final String coverageConverter) {
    this.coverageConverter = coverageConverter;
  }

  public void setScale(final int scale) {
    this.scale = scale;
  }

  public void setCropToSpatialConstraint(final boolean cropToSpatialConstraint) {
    this.cropToSpatialConstraint = cropToSpatialConstraint;
  }

  public boolean isSkipMerge() {
    return skipMerge;
  }

  public void setSkipMerge(final boolean skipMerge) {
    this.skipMerge = skipMerge;
  }
}
