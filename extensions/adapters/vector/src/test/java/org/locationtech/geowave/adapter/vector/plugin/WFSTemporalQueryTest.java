/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.adapter.vector.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import java.io.IOException;
import java.text.ParseException;
import java.util.UUID;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.feature.SchemaException;
import org.geotools.filter.text.cql2.CQL;
import org.geotools.filter.text.cql2.CQLException;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geowave.adapter.vector.BaseDataStoreTest;
import org.locationtech.geowave.adapter.vector.util.DateUtilities;
import org.locationtech.geowave.core.geotime.index.SpatialDimensionalityTypeProvider;
import org.locationtech.geowave.core.geotime.index.SpatialOptions;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class WFSTemporalQueryTest extends BaseDataStoreTest {
  DataStore dataStore;
  SimpleFeatureType schema;
  SimpleFeatureType type;
  final GeometryFactory factory = new GeometryFactory(new PrecisionModel(PrecisionModel.FIXED));

  @Before
  public void setup() throws SchemaException, CQLException, IOException, GeoWavePluginException {
    dataStore = createDataStore();
    ((GeoWaveGTDataStore) dataStore).getDataStore().addIndex(
        SpatialDimensionalityTypeProvider.createIndexFromOptions(new SpatialOptions()));
    type =
        DataUtilities.createType(
            "geostuff",
            "geometry:Geometry:srid=4326,pop:java.lang.Long,pid:String,start:Date,end:Date");

    dataStore.createSchema(type);
  }

  public void populate() throws IOException, CQLException, ParseException {
    final Transaction transaction1 = new DefaultTransaction();

    final FeatureWriter<SimpleFeatureType, SimpleFeature> writer =
        dataStore.getFeatureWriter(type.getTypeName(), transaction1);
    assertFalse(writer.hasNext());
    SimpleFeature newFeature = writer.next();
    newFeature.setAttribute("pop", Long.valueOf(100));
    newFeature.setAttribute("pid", UUID.randomUUID().toString());
    newFeature.setAttribute("start", DateUtilities.parseISO("2005-05-17T20:32:56Z"));
    newFeature.setAttribute("end", DateUtilities.parseISO("2005-05-19T20:32:56Z"));
    newFeature.setAttribute("geometry", factory.createPoint(new Coordinate(43.454, 28.232)));
    writer.write();

    newFeature = writer.next();
    newFeature.setAttribute("pop", Long.valueOf(100));
    newFeature.setAttribute("pid", UUID.randomUUID().toString());
    newFeature.setAttribute("start", DateUtilities.parseISO("2005-05-18T20:32:56Z"));
    newFeature.setAttribute("end", DateUtilities.parseISO("2005-05-20T20:32:56Z"));
    newFeature.setAttribute("geometry", factory.createPoint(new Coordinate(43.454, 27.232)));
    writer.write();

    newFeature = writer.next();
    newFeature.setAttribute("pop", Long.valueOf(100));
    newFeature.setAttribute("pid", UUID.randomUUID().toString());
    newFeature.setAttribute("start", DateUtilities.parseISO("2005-05-21T20:32:56Z"));
    newFeature.setAttribute("end", DateUtilities.parseISO("2005-05-22T20:32:56Z"));
    newFeature.setAttribute("geometry", factory.createPoint(new Coordinate(43.454, 28.232)));
    writer.write();
    writer.close();
    transaction1.commit();
    transaction1.close();
  }

  @Test
  public void testTemporal() throws CQLException, IOException, ParseException {

    populate();
    final Transaction transaction2 = new DefaultTransaction();
    final Query query =
        new Query(
            "geostuff",
            CQL.toFilter(
                "BBOX(geometry,44,27,42,30) and start during 2005-05-16T20:32:56Z/2005-05-20T21:32:56Z and end during 2005-05-18T20:32:56Z/2005-05-22T21:32:56Z"),
            new String[] {"geometry", "start", "end", "pid"});
    final FeatureReader<SimpleFeatureType, SimpleFeature> reader =
        dataStore.getFeatureReader(query, transaction2);
    int c = 0;
    while (reader.hasNext()) {
      reader.next();
      c++;
    }
    reader.close();
    transaction2.commit();
    transaction2.close();
    assertEquals(2, c);
  }
}
