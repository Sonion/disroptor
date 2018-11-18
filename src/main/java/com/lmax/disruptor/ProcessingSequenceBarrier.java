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


/**
 * {@link SequenceBarrier} handed out for gating {@link EventProcessor}s on a cursor sequence and optional dependent {@link EventProcessor}(s),
 * using the given WaitStrategy.
 */
final class ProcessingSequenceBarrier implements SequenceBarrier
{
    /**
     * 等待可用消费时，指定的等待策略
     */
    private final WaitStrategy waitStrategy;
    /**
     * 依赖的上组消费者的序号，如果当前为第一组则为cursorSequence（即生产者发布游标序列）
     * 否则使用FixedSequenceGroup封装上组消费者序列
     */
    private final Sequence dependentSequence;
    /**
     * 当触发halt时，将标记alerted为true
     */
    private volatile boolean alerted = false;
    /**
     * AbstractSequencer中的cursor引用，记录当前发布者发布的最新位置
     */
    private final Sequence cursorSequence;
    /**
     * MultiProducerSequencer 或 SingleProducerSequencer
     */
    private final Sequencer sequencer;

    ProcessingSequenceBarrier(
        final Sequencer sequencer,
        final WaitStrategy waitStrategy,
        final Sequence cursorSequence,
        final Sequence[] dependentSequences)
    {
        this.sequencer = sequencer;
        this.waitStrategy = waitStrategy;
        this.cursorSequence = cursorSequence;
        // 依赖的上一组序列长度，第一次是0
        if (0 == dependentSequences.length)
        {
            dependentSequence = cursorSequence;
        }
        // 将上一组序列数组复制成新数组保存，引用不变
        else
        {
            dependentSequence = new FixedSequenceGroup(dependentSequences);
        }
    }

    @Override
    public long waitFor(final long sequence)
        throws AlertException, InterruptedException, TimeoutException
    {
        // 检查是否停止服务
        checkAlert();
        // 获取最大可用序号 sequence为给定序号，一般为当前序号+1，cursorSequence记录生产者最新位置，
        long availableSequence = waitStrategy.waitFor(sequence, cursorSequence, dependentSequence, this);

        if (availableSequence < sequence)
        {
            return availableSequence;
        }
        // 返回已发布最高的序列值，将对每个序号进行校验
        return sequencer.getHighestPublishedSequence(sequence, availableSequence);
    }

    @Override
    public long getCursor()
    {
        return dependentSequence.get();
    }

    @Override
    public boolean isAlerted()
    {
        return alerted;
    }

    @Override
    public void alert()
    {
        alerted = true;
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void clearAlert()
    {
        alerted = false;
    }

    @Override
    public void checkAlert() throws AlertException
    {
        if (alerted)
        {
            throw AlertException.INSTANCE;
        }
    }
}