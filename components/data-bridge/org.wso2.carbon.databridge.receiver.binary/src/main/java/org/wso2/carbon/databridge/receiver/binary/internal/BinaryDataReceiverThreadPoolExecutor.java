/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.databridge.receiver.binary.internal;

import org.wso2.carbon.databridge.commons.utils.DataBridgeThreadFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BinaryDataReceiverThreadPoolExecutor extends ThreadPoolExecutor {
    private final Semaphore semaphore;

    public BinaryDataReceiverThreadPoolExecutor(int poolSize, String threadName) {
        super(poolSize, poolSize, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                new DataBridgeThreadFactory(threadName));
        semaphore = new Semaphore(poolSize);
    }

    @Override
    public void execute(final Runnable task) {
        boolean acquired = false;
        do {
            try {
                semaphore.acquire();
                acquired = true;
            } catch (final InterruptedException e) {
                // Do nothing
            }
        } while (!acquired);
        super.execute(task);
    }

    @Override
    protected void afterExecute(final Runnable r, final Throwable t) {
        super.afterExecute(r, t);
        semaphore.release();
    }
}