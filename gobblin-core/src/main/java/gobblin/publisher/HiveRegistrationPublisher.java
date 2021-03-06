/*
 * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.publisher;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.hadoop.fs.Path;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;

import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.State;
import gobblin.configuration.WorkUnitState;
import gobblin.hive.HiveRegProps;
import gobblin.hive.HiveRegister;
import gobblin.hive.policy.HiveRegistrationPolicy;
import gobblin.hive.policy.HiveRegistrationPolicyBase;
import gobblin.hive.spec.HiveSpec;
import gobblin.util.ExecutorsUtils;
import lombok.extern.slf4j.Slf4j;


/**
 * A {@link DataPublisher} that registers the already published data with Hive.
 *
 * <p>
 *   This publisher is not responsible for publishing data, and it relies on another publisher
 *   to document the published paths in property {@link ConfigurationKeys#PUBLISHER_DIRS}. Thus this publisher
 *   should generally be used as a job level data publisher, where the task level publisher should be a publisher
 *   that documents the published paths, such as {@link BaseDataPublisher}.
 * </p>
 *
 * @author Ziyang Liu
 */
@Slf4j
public class HiveRegistrationPublisher extends DataPublisher {

  private final Closer closer = Closer.create();
  private final HiveRegister hiveRegister;
  private final HiveRegistrationPolicy policy;
  private final ExecutorService hivePolicyExecutor;

  public HiveRegistrationPublisher(State state) {
    super(state);
    this.hiveRegister = this.closer.register(HiveRegister.get(state));
    this.policy = HiveRegistrationPolicyBase.getPolicy(state);
    this.hivePolicyExecutor = Executors.newFixedThreadPool(new HiveRegProps(state).getNumThreads(),
        ExecutorsUtils.newThreadFactory(Optional.of(log), Optional.of("HivePolicyExecutor-%d")));
  }

  @Override
  public void close() throws IOException {
    try {
      ExecutorsUtils.shutdownExecutorService(this.hivePolicyExecutor, Optional.of(log));
    } finally {
      this.closer.close();
    }
  }

  @Deprecated
  @Override
  public void initialize() throws IOException {}

  @Override
  public void publishData(Collection<? extends WorkUnitState> states) throws IOException {
    CompletionService<Collection<HiveSpec>> completionService =
        new ExecutorCompletionService<>(this.hivePolicyExecutor);

    Set<String> pathsToRegister = getUniquePathsToRegister(states);
    log.info("Number of paths to be registered in Hive: " + pathsToRegister.size());
    for (final String path : pathsToRegister) {
      completionService.submit(new Callable<Collection<HiveSpec>>() {

        @Override
        public Collection<HiveSpec> call() throws Exception {
          return HiveRegistrationPublisher.this.policy.getHiveSpecs(new Path(path));
        }
      });

    }

    for (int i = 0; i < pathsToRegister.size(); i++) {
      try {
        for (HiveSpec spec : completionService.take().get()) {
          this.hiveRegister.register(spec);
        }
      } catch (InterruptedException | ExecutionException e) {
        log.info("Failed to generate HiveSpec", e);
        throw new IOException(e);
      }
    }
    log.info("Finished generating all HiveSpecs");
  }

  private static Set<String> getUniquePathsToRegister(Collection<? extends WorkUnitState> states) {
    Set<String> paths = Sets.newHashSet();
    for (State state : states) {
      if (state.contains(ConfigurationKeys.PUBLISHER_DIRS)) {
        paths.addAll(state.getPropAsList(ConfigurationKeys.PUBLISHER_DIRS));
      }
    }
    return paths;
  }

  @Override
  public void publishMetadata(Collection<? extends WorkUnitState> states) throws IOException {
    // Nothing to do
  }

}
