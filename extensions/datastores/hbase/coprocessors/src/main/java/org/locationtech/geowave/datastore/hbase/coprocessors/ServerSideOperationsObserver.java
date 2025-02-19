/**
 * Copyright (c) 2013-2020 Contributors to the Eclipse Foundation
 *
 * <p> See the NOTICE file distributed with this work for additional information regarding copyright
 * ownership. All rights reserved. This program and the accompanying materials are made available
 * under the terms of the Apache License, Version 2.0 which accompanies this distribution and is
 * available at http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package org.locationtech.geowave.datastore.hbase.coprocessors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessor;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.coprocessor.RegionObserver;
import org.apache.hadoop.hbase.regionserver.FlushLifeCycleTracker;
import org.apache.hadoop.hbase.regionserver.InternalScanner;
import org.apache.hadoop.hbase.regionserver.RegionScanner;
import org.apache.hadoop.hbase.regionserver.ScanType;
import org.apache.hadoop.hbase.regionserver.Store;
import org.apache.hadoop.hbase.regionserver.compactions.CompactionLifeCycleTracker;
import org.apache.hadoop.hbase.regionserver.compactions.CompactionRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.geowave.core.index.ByteArrayUtils;
import org.locationtech.geowave.core.store.server.ServerOpConfig.ServerOpScope;
import org.locationtech.geowave.datastore.hbase.server.HBaseServerOp;
import org.locationtech.geowave.datastore.hbase.server.ServerOpInternalScannerWrapper;
import org.locationtech.geowave.datastore.hbase.server.ServerOpRegionScannerWrapper;
import org.locationtech.geowave.datastore.hbase.server.ServerSideOperationStore;
import org.locationtech.geowave.datastore.hbase.server.ServerSideOperationUtils;
import org.locationtech.geowave.datastore.hbase.util.HBaseUtils;
import com.google.common.collect.ImmutableSet;

public class ServerSideOperationsObserver implements RegionObserver, RegionCoprocessor {

  private static final Logger LOGGER = LogManager.getLogger(ServerSideOperationsObserver.class);
  private static final int SERVER_OP_OPTIONS_PREFIX_LENGTH =
      ServerSideOperationUtils.SERVER_OP_OPTIONS_PREFIX.length();

  private ServerSideOperationStore opStore = null;
  private static final RegionScannerWrapperFactory REGION_SCANNER_FACTORY =
      new RegionScannerWrapperFactory();
  private static final InternalScannerWrapperFactory INTERNAL_SCANNER_FACTORY =
      new InternalScannerWrapperFactory();

  private static interface ScannerWrapperFactory<T extends InternalScanner> {
    public T createScannerWrapper(
        Collection<HBaseServerOp> orderedServerOps,
        T delegate,
        Scan scan);
  }

  private static class RegionScannerWrapperFactory implements ScannerWrapperFactory<RegionScanner> {

    @Override
    public RegionScanner createScannerWrapper(
        final Collection<HBaseServerOp> orderedServerOps,
        final RegionScanner delegate,
        final Scan scan) {
      return new ServerOpRegionScannerWrapper(orderedServerOps, delegate, scan);
    }
  }

  private static class InternalScannerWrapperFactory implements
      ScannerWrapperFactory<InternalScanner> {

    @Override
    public InternalScanner createScannerWrapper(
        final Collection<HBaseServerOp> orderedServerOps,
        final InternalScanner delegate,
        final Scan scan) {
      return new ServerOpInternalScannerWrapper(orderedServerOps, delegate, scan);
    }
  }

  @Override
  public Optional<RegionObserver> getRegionObserver() {
    return Optional.of(this);
  }

  @Override
  public InternalScanner preFlush(
      final ObserverContext<RegionCoprocessorEnvironment> c,
      final Store store,
      final InternalScanner scanner,
      final FlushLifeCycleTracker tracker) throws IOException {
    if (opStore == null) {
      return RegionObserver.super.preFlush(c, store, scanner, tracker);
    }
    return RegionObserver.super.preFlush(
        c,
        store,
        wrapScannerWithOps(
            c.getEnvironment().getRegionInfo().getTable(),
            scanner,
            null,
            ServerOpScope.MINOR_COMPACTION,
            INTERNAL_SCANNER_FACTORY),
        tracker);
  }

  @Override
  public InternalScanner preCompact(
      final ObserverContext<RegionCoprocessorEnvironment> c,
      final Store store,
      final InternalScanner scanner,
      final ScanType scanType,
      final CompactionLifeCycleTracker tracker,
      final CompactionRequest request) throws IOException {
    if (opStore == null) {
      return RegionObserver.super.preCompact(c, store, scanner, scanType, tracker, request);
    }

    return RegionObserver.super.preCompact(
        c,
        store,
        wrapScannerWithOps(
            c.getEnvironment().getRegionInfo().getTable(),
            scanner,
            null,
            ServerOpScope.MAJOR_COMPACTION,
            INTERNAL_SCANNER_FACTORY),
        scanType,
        tracker,
        request);
  }

  @Override
  public void preScannerOpen(final ObserverContext<RegionCoprocessorEnvironment> e, final Scan scan)
      throws IOException {
    if (opStore != null) {
      final TableName tableName = e.getEnvironment().getRegionInfo().getTable();
      if (!tableName.isSystemTable()) {
        final String namespace = tableName.getNamespaceAsString();
        final String qualifier = tableName.getQualifierAsString();
        final Collection<HBaseServerOp> serverOps =
            opStore.getOperations(namespace, qualifier, ServerOpScope.SCAN);
        for (final HBaseServerOp op : serverOps) {
          op.preScannerOpen(scan);
        }
      }
    }
    RegionObserver.super.preScannerOpen(e, scan);
  }

  @Override
  public RegionScanner postScannerOpen(
      final ObserverContext<RegionCoprocessorEnvironment> e,
      final Scan scan,
      final RegionScanner s) throws IOException {
    if (opStore == null) {
      return RegionObserver.super.postScannerOpen(e, scan, s);
    }
    return RegionObserver.super.postScannerOpen(
        e,
        scan,
        wrapScannerWithOps(
            e.getEnvironment().getRegionInfo().getTable(),
            s,
            scan,
            ServerOpScope.SCAN,
            REGION_SCANNER_FACTORY));
  }

  public <T extends InternalScanner> T wrapScannerWithOps(
      final TableName tableName,
      final T scanner,
      final Scan scan,
      final ServerOpScope scope,
      final ScannerWrapperFactory<T> factory) {
    if (!tableName.isSystemTable()) {
      final String namespace = tableName.getNamespaceAsString();
      final String qualifier = tableName.getQualifierAsString();
      final Collection<HBaseServerOp> orderedServerOps =
          opStore.getOperations(namespace, qualifier, scope);
      if (!orderedServerOps.isEmpty()) {
        return factory.createScannerWrapper(orderedServerOps, scanner, scan);
      }
    }
    return scanner;
  }

  @Override
  public void start(final CoprocessorEnvironment env) throws IOException {
    opStore = new ServerSideOperationStore();
    final Configuration config = env.getConfiguration();
    final Map<String, List<String>> uniqueOpsWithOptionKeys = new HashMap<>();
    for (final Map.Entry<String, String> entry : config) {
      if (entry.getKey().startsWith(ServerSideOperationUtils.SERVER_OP_PREFIX)) {
        final String key = entry.getKey();
        final int index = StringUtils.ordinalIndexOf(key, ".", 4);
        if (index > 0) {
          final String uniqueOp = key.substring(0, index + 1);
          List<String> optionKeys = uniqueOpsWithOptionKeys.get(uniqueOp);
          if (optionKeys == null) {
            optionKeys = new ArrayList<>();
            uniqueOpsWithOptionKeys.put(uniqueOp, optionKeys);
          }
          if (key.length() > (uniqueOp.length() + 1 + SERVER_OP_OPTIONS_PREFIX_LENGTH)) {
            if (key.substring(
                uniqueOp.length(),
                uniqueOp.length() + SERVER_OP_OPTIONS_PREFIX_LENGTH).equals(
                    ServerSideOperationUtils.SERVER_OP_OPTIONS_PREFIX)) {
              optionKeys.add(
                  key.substring(uniqueOp.length() + 1 + SERVER_OP_OPTIONS_PREFIX_LENGTH));
            }
          }
        }
      }
    }

    for (final Entry<String, List<String>> uniqueOpAndOptions : uniqueOpsWithOptionKeys.entrySet()) {
      final String uniqueOp = uniqueOpAndOptions.getKey();
      final String priorityStr =
          config.get(uniqueOp + ServerSideOperationUtils.SERVER_OP_PRIORITY_KEY);
      if ((priorityStr == null) || priorityStr.isEmpty()) {
        LOGGER.warn("Skipping server op - unable to find priority for '" + uniqueOp + "'");
        continue;
      }
      final int priority = Integer.parseInt(priorityStr);
      final String commaDelimitedScopes =
          config.get(uniqueOp + ServerSideOperationUtils.SERVER_OP_SCOPES_KEY);
      if ((commaDelimitedScopes == null) || commaDelimitedScopes.isEmpty()) {
        LOGGER.warn("Skipping server op - unable to find scopes for '" + uniqueOp + "'");
        continue;
      }
      final ImmutableSet<ServerOpScope> scopes = HBaseUtils.stringToScopes(commaDelimitedScopes);
      final String classIdStr = config.get(uniqueOp + ServerSideOperationUtils.SERVER_OP_CLASS_KEY);
      if ((classIdStr == null) || classIdStr.isEmpty()) {
        LOGGER.warn("Skipping server op - unable to find class ID for '" + uniqueOp + "'");
        continue;
      }
      final List<String> optionKeys = uniqueOpAndOptions.getValue();
      final Map<String, String> optionsMap = new HashMap<>();
      for (final String optionKey : optionKeys) {
        final String optionValue =
            config.get(
                uniqueOp + ServerSideOperationUtils.SERVER_OP_OPTIONS_PREFIX + "." + optionKey);
        optionsMap.put(optionKey, optionValue);
      }
      final String[] uniqueOpSplit = uniqueOp.split("\\.");
      opStore.addOperation(
          HBaseUtils.readConfigSafeTableName(uniqueOpSplit[1]),
          HBaseUtils.readConfigSafeTableName(uniqueOpSplit[2]),
          uniqueOpSplit[3],
          priority,
          scopes,
          ByteArrayUtils.byteArrayFromString(classIdStr),
          optionsMap);
    }
    RegionCoprocessor.super.start(env);
  }
}
