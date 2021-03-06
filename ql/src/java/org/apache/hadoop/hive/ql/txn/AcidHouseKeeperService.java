/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hive.ql.txn;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.txn.TxnStore;
import org.apache.hadoop.hive.metastore.txn.TxnUtils;
import org.apache.hadoop.hive.ql.txn.compactor.HouseKeeperServiceBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performs background tasks for Transaction management in Hive.
 * Runs inside Hive Metastore Service.
 */
public class AcidHouseKeeperService extends HouseKeeperServiceBase {
  private static final Logger LOG = LoggerFactory.getLogger(AcidHouseKeeperService.class);

  @Override
  protected long getStartDelayMs() {
    return hiveConf.getTimeVar(HiveConf.ConfVars.HIVE_TIMEDOUT_TXN_REAPER_START, TimeUnit.MILLISECONDS);
  }
  @Override
  protected long getIntervalMs() {
    return hiveConf.getTimeVar(HiveConf.ConfVars.HIVE_TIMEDOUT_TXN_REAPER_INTERVAL, TimeUnit.MILLISECONDS);
  }
  @Override
  protected Runnable getScheduedAction(HiveConf hiveConf, AtomicInteger isAliveCounter) {
    return new TimedoutTxnReaper(hiveConf, isAliveCounter);
  }

  @Override
  public String getServiceDescription() {
    return "Abort expired transactions";
  }

  private static final class TimedoutTxnReaper implements Runnable {
    private final TxnStore txnHandler;
    private final AtomicInteger isAliveCounter;
    private TimedoutTxnReaper(HiveConf hiveConf, AtomicInteger isAliveCounter) {
      txnHandler = TxnUtils.getTxnStore(hiveConf);
      this.isAliveCounter = isAliveCounter;
    }
    @Override
    public void run() {
      try {
        long startTime = System.currentTimeMillis();
        txnHandler.performTimeOuts();
        int count = isAliveCounter.incrementAndGet();
        LOG.info("timeout reaper ran for " + (System.currentTimeMillis() - startTime)/1000 + "seconds.  isAliveCounter=" + count);
      }
      catch(Throwable t) {
        LOG.error("Serious error in {}", Thread.currentThread().getName(), ": {}" + t.getMessage(), t);
      }
    }
  }
}
