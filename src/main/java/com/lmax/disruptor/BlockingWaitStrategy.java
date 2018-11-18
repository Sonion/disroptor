/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import com.lmax.disruptor.util.ThreadHints;

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 * <p>
 * This strategy can be used when throughput and low-latency are not as important as CPU resource.
 */
public final class BlockingWaitStrategy implements WaitStrategy
{
    private final Object mutex = new Object();

    @Override
    public long waitFor(long sequence, Sequence cursorSequence, Sequence dependentSequence, SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        // 当前游标小于给定序号，也就是无可用事件
        if (cursorSequence.get() < sequence)
        {
            //也就是只有等待策略才会用锁，其他使用CAS，这就是前文提到的高效原因
            synchronized (mutex)
            {
                // 当给定的序号大于生产者游标序号时，进行等待
                while (cursorSequence.get() < sequence)
                // 循环等待，在Sequencer中publish进行唤醒；等待消费时也会在循环中定时唤醒。
                {
                    barrier.checkAlert();
                    mutex.wait();
                }
            }
        }
        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            barrier.checkAlert();
            ThreadHints.onSpinWait();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        synchronized (mutex)
        {
            mutex.notifyAll();
        }
    }

    @Override
    public String toString()
    {
        return "BlockingWaitStrategy{" +
            "mutex=" + mutex +
            '}';
    }
}
