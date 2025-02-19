/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.adapter.vector.export;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumWriter;
import org.geotools.filter.text.cql2.CQLException;
import org.locationtech.geowave.adapter.vector.GeoWaveAvroFeatureUtils;
import org.locationtech.geowave.adapter.vector.avro.AvroAttributeValues;
import org.locationtech.geowave.adapter.vector.avro.AvroSimpleFeatureCollection;
import org.locationtech.geowave.adapter.vector.cli.VectorSection;
import org.locationtech.geowave.core.cli.annotations.GeowaveOperation;
import org.locationtech.geowave.core.cli.api.Command;
import org.locationtech.geowave.core.cli.api.DefaultOperation;
import org.locationtech.geowave.core.cli.api.OperationParams;
import org.locationtech.geowave.core.geotime.store.GeotoolsFeatureDataAdapter;
import org.locationtech.geowave.core.geotime.store.query.api.VectorQueryBuilder;
import org.locationtech.geowave.core.store.CloseableIterator;
import org.locationtech.geowave.core.store.adapter.InternalAdapterStore;
import org.locationtech.geowave.core.store.adapter.InternalDataAdapter;
import org.locationtech.geowave.core.store.adapter.PersistentAdapterStore;
import org.locationtech.geowave.core.store.api.DataStore;
import org.locationtech.geowave.core.store.api.Index;
import org.locationtech.geowave.core.store.cli.CLIUtils;
import org.locationtech.geowave.core.store.cli.store.DataStorePluginOptions;
import org.locationtech.geowave.core.store.index.IndexStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

@GeowaveOperation(name = "localexport", parentOperation = VectorSection.class)
@Parameters(commandDescription = "Export data directly to Avro file")
public class VectorLocalExportCommand extends DefaultOperation implements Command {
  @Parameter(description = "<store name>")
  private List<String> parameters = new ArrayList<>();

  @ParametersDelegate
  private VectorLocalExportOptions options = new VectorLocalExportOptions();

  private DataStorePluginOptions inputStoreOptions = null;

  @Override
  public void execute(final OperationParams params) throws IOException, CQLException {

    // Ensure we have all the required arguments
    if (parameters.size() != 1) {
      throw new ParameterException("Requires arguments: <store name>");
    }

    final String storeName = parameters.get(0);

    // Attempt to load store.
    inputStoreOptions =
        CLIUtils.loadStore(storeName, getGeoWaveConfigFile(params), params.getConsole());

    final PersistentAdapterStore adapterStore = inputStoreOptions.createAdapterStore();
    final IndexStore indexStore = inputStoreOptions.createIndexStore();
    final DataStore dataStore = inputStoreOptions.createDataStore();
    final InternalAdapterStore internalAdapterStore =
        inputStoreOptions.createInternalAdapterStore();

    try (final DataFileWriter<AvroSimpleFeatureCollection> dfw =
        new DataFileWriter<>(
            new GenericDatumWriter<AvroSimpleFeatureCollection>(
                AvroSimpleFeatureCollection.SCHEMA$))) {
      dfw.setCodec(CodecFactory.snappyCodec());
      dfw.create(AvroSimpleFeatureCollection.SCHEMA$, options.getOutputFile());
      // get appropriate feature adapters
      final List<GeotoolsFeatureDataAdapter> featureAdapters = new ArrayList<>();
      if ((options.getTypeNames() != null) && (options.getTypeNames().size() > 0)) {
        for (final String typeName : options.getTypeNames()) {
          final short adapterId = internalAdapterStore.getAdapterId(typeName);
          final InternalDataAdapter<?> internalDataAdapter = adapterStore.getAdapter(adapterId);
          if (internalDataAdapter == null) {
            params.getConsole().println("Type '" + typeName + "' not found");
            continue;
          } else if (!(internalDataAdapter.getAdapter() instanceof GeotoolsFeatureDataAdapter)) {
            params.getConsole().println(
                "Type '"
                    + typeName
                    + "' does not support vector export. Instance of "
                    + internalDataAdapter.getAdapter().getClass());
            continue;
          }
          featureAdapters.add((GeotoolsFeatureDataAdapter) internalDataAdapter.getAdapter());
        }
      } else {
        final InternalDataAdapter<?>[] adapters = adapterStore.getAdapters();
        for (final InternalDataAdapter<?> adapter : adapters) {
          if (adapter.getAdapter() instanceof GeotoolsFeatureDataAdapter) {
            featureAdapters.add((GeotoolsFeatureDataAdapter) adapter.getAdapter());
          }
        }
      }
      if (featureAdapters.isEmpty()) {
        params.getConsole().println("Unable to find any vector data types in store");
      }
      Index queryIndex = null;
      if (options.getIndexName() != null) {
        queryIndex = indexStore.getIndex(options.getIndexName());
        if (queryIndex == null) {
          params.getConsole().println(
              "Unable to find index '" + options.getIndexName() + "' in store");
          return;
        }
      }
      for (final GeotoolsFeatureDataAdapter adapter : featureAdapters) {
        params.getConsole().println("Exporting type '" + adapter.getTypeName() + "'");
        final VectorQueryBuilder bldr = VectorQueryBuilder.newBuilder();

        if (options.getIndexName() != null) {
          bldr.indexName(options.getIndexName());
        }
        if (options.getCqlFilter() != null) {
          bldr.constraints(bldr.constraintsFactory().cqlConstraints(options.getCqlFilter()));
        }
        bldr.addTypeName(adapter.getTypeName());

        try (final CloseableIterator<SimpleFeature> it = dataStore.query(bldr.build())) {
          int iteration = 0;
          while (it.hasNext()) {
            final AvroSimpleFeatureCollection simpleFeatureCollection =
                new AvroSimpleFeatureCollection();

            final SimpleFeature next = it.next();
            final SimpleFeatureType featureType = next.getFeatureType();
            simpleFeatureCollection.setFeatureType(
                GeoWaveAvroFeatureUtils.buildFeatureDefinition(null, featureType, null, ""));
            final List<AvroAttributeValues> avList = new ArrayList<>(options.getBatchSize());
            avList.add(GeoWaveAvroFeatureUtils.buildAttributeValue(next, featureType));
            while (it.hasNext() && (avList.size() < options.getBatchSize())) {
              avList.add(GeoWaveAvroFeatureUtils.buildAttributeValue(it.next(), featureType));
            }
            params.getConsole().println(
                "Exported "
                    + (avList.size() + (iteration * options.getBatchSize()))
                    + " features from '"
                    + adapter.getTypeName()
                    + "'");
            iteration++;
            simpleFeatureCollection.setSimpleFeatureCollection(avList);
            dfw.append(simpleFeatureCollection);
            dfw.flush();
          }
          params.getConsole().println("Finished exporting '" + adapter.getTypeName() + "'");
        }
      }
    }
  }

  public List<String> getParameters() {
    return parameters;
  }

  public void setParameters(final String storeName) {
    parameters = new ArrayList<>();
    parameters.add(storeName);
  }

  public DataStorePluginOptions getInputStoreOptions() {
    return inputStoreOptions;
  }

  public void setOptions(final VectorLocalExportOptions options) {
    this.options = options;
  }

  public VectorLocalExportOptions getOptions() {
    return options;
  }
}
