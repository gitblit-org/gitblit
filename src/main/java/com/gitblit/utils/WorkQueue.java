// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.gitblit.utils;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/** Delayed execution of tasks using a background thread pool. */
public class WorkQueue {
  private static final Logger log = LoggerFactory.getLogger(WorkQueue.class);
  private static final UncaughtExceptionHandler LOG_UNCAUGHT_EXCEPTION =
      new UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
          log.error("WorkQueue thread " + t.getName() + " threw exception", e);
        }
      };

  private Executor defaultQueue;
  private final IdGenerator idGenerator;
  private final int defaultQueueSize;
  private final CopyOnWriteArrayList<Executor> queues;

  public WorkQueue(final IdGenerator idGenerator, final int defaultQueueSize) {
    this.idGenerator = idGenerator;
    this.defaultQueueSize = defaultQueueSize;
    this.queues = new CopyOnWriteArrayList<Executor>();
  }

  /** Get the default work queue, for miscellaneous tasks. */
  public synchronized Executor getDefaultQueue() {
    if (defaultQueue == null) {
      defaultQueue = createQueue(defaultQueueSize, "WorkQueue");
    }
    return defaultQueue;
  }

  /** Create a new executor queue with one thread. */
  public Executor createQueue(final int poolsize, final String prefix) {
    final Executor r = new Executor(poolsize, prefix);
    r.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    r.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    queues.add(r);
    return r;
  }

  /** Get all of the tasks currently scheduled in any work queue. */
  public List<Task<?>> getTasks() {
    final List<Task<?>> r = new ArrayList<Task<?>>();
    for (final Executor e : queues) {
      e.addAllTo(r);
    }
    return r;
  }

  public <T> List<T> getTaskInfos(TaskInfoFactory<T> factory) {
    List<T> taskInfos = Lists.newArrayList();
    for (Executor exe : queues) {
      for (Task<?> task : exe.getTasks()) {
        taskInfos.add(factory.getTaskInfo(task));
      }
    }
    return taskInfos;
  }

  /** Locate a task by its unique id, null if no task matches. */
  public Task<?> getTask(final int id) {
    Task<?> result = null;
    for (final Executor e : queues) {
      final Task<?> t = e.getTask(id);
      if (t != null) {
        if (result != null) {
          // Don't return the task if we have a duplicate. Lie instead.
          return null;
        } else {
          result = t;
        }
      }
    }
    return result;
  }

  public void stop() {
    for (final Executor p : queues) {
      p.shutdown();
      boolean isTerminated;
      do {
        try {
          isTerminated = p.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
          isTerminated = false;
        }
      } while (!isTerminated);
    }
    queues.clear();
  }

  /** An isolated queue. */
  public class Executor extends ScheduledThreadPoolExecutor {
    private final ConcurrentHashMap<Integer, Task<?>> all;

    Executor(final int corePoolSize, final String prefix) {
      super(corePoolSize, new ThreadFactory() {
        private final ThreadFactory parent = Executors.defaultThreadFactory();
        private final AtomicInteger tid = new AtomicInteger(1);

        @Override
        public Thread newThread(final Runnable task) {
          final Thread t = parent.newThread(task);
          t.setName(prefix + "-" + tid.getAndIncrement());
          t.setUncaughtExceptionHandler(LOG_UNCAUGHT_EXCEPTION);
          return t;
        }
      });

      all = new ConcurrentHashMap<Integer, Task<?>>( //
          corePoolSize << 1, // table size
          0.75f, // load factor
          corePoolSize + 4 // concurrency level
          );
    }

    public void unregisterWorkQueue() {
      queues.remove(this);
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        final Runnable runnable, RunnableScheduledFuture<V> r) {
      r = super.decorateTask(runnable, r);
      for (;;) {
        final int id = idGenerator.next();

        Task<V> task;
        task = new Task<V>(runnable, r, this, id);

        if (all.putIfAbsent(task.getTaskId(), task) == null) {
          return task;
        }
      }
    }

    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(
        final Callable<V> callable, final RunnableScheduledFuture<V> task) {
      throw new UnsupportedOperationException("Callable not implemented");
    }

    void remove(final Task<?> task) {
      all.remove(task.getTaskId(), task);
    }

    Task<?> getTask(final int id) {
      return all.get(id);
    }

    void addAllTo(final List<Task<?>> list) {
      list.addAll(all.values()); // iterator is thread safe
    }

    Collection<Task<?>> getTasks() {
      return all.values();
    }
  }

  /** Runnable needing to know it was canceled. */
  public interface CancelableRunnable extends Runnable {
    /** Notifies the runnable it was canceled. */
    public void cancel();
  }

  /** A wrapper around a scheduled Runnable, as maintained in the queue. */
  public static class Task<V> implements RunnableScheduledFuture<V> {
    /**
     * Summarized status of a single task.
     * <p>
     * Tasks have the following state flow:
     * <ol>
     * <li>{@link #SLEEPING}: if scheduled with a non-zero delay.</li>
     * <li>{@link #READY}: waiting for an available worker thread.</li>
     * <li>{@link #RUNNING}: actively executing on a worker thread.</li>
     * <li>{@link #DONE}: finished executing, if not periodic.</li>
     * </ol>
     */
    public static enum State {
      // Ordered like this so ordinal matches the order we would
      // prefer to see tasks sorted in: done before running,
      // running before ready, ready before sleeping.
      //
      DONE, CANCELLED, RUNNING, READY, SLEEPING, OTHER
    }

    private final Runnable runnable;
    private final RunnableScheduledFuture<V> task;
    private final Executor executor;
    private final int taskId;
    private final AtomicBoolean running;
    private final Date startTime;

    Task(Runnable runnable, RunnableScheduledFuture<V> task, Executor executor,
        int taskId) {
      this.runnable = runnable;
      this.task = task;
      this.executor = executor;
      this.taskId = taskId;
      this.running = new AtomicBoolean();
      this.startTime = new Date();
    }

    public int getTaskId() {
      return taskId;
    }

    public State getState() {
      if (isCancelled()) {
        return State.CANCELLED;
      } else if (isDone() && !isPeriodic()) {
        return State.DONE;
      } else if (running.get()) {
        return State.RUNNING;
      }

      final long delay = getDelay(TimeUnit.MILLISECONDS);
      if (delay <= 0) {
        return State.READY;
      } else if (0 < delay) {
        return State.SLEEPING;
      }

      return State.OTHER;
    }

    public Date getStartTime() {
      return startTime;
    }

    @Override
	public boolean cancel(boolean mayInterruptIfRunning) {
      if (task.cancel(mayInterruptIfRunning)) {
        // Tiny abuse of running: if the task needs to know it was
        // canceled (to clean up resources) and it hasn't started
        // yet the task's run method won't execute. So we tag it
        // as running and allow it to clean up. This ensures we do
        // not invoke cancel twice.
        //
        if (runnable instanceof CancelableRunnable
            && running.compareAndSet(false, true)) {
          ((CancelableRunnable) runnable).cancel();
        }
        executor.remove(this);
        executor.purge();
        return true;

      } else {
        return false;
      }
    }

    @Override
	public int compareTo(Delayed o) {
      return task.compareTo(o);
    }

    @Override
	public V get() throws InterruptedException, ExecutionException {
      return task.get();
    }

    @Override
	public V get(long timeout, TimeUnit unit) throws InterruptedException,
        ExecutionException, TimeoutException {
      return task.get(timeout, unit);
    }

    @Override
	public long getDelay(TimeUnit unit) {
      return task.getDelay(unit);
    }

    @Override
	public boolean isCancelled() {
      return task.isCancelled();
    }

    @Override
	public boolean isDone() {
      return task.isDone();
    }

    @Override
	public boolean isPeriodic() {
      return task.isPeriodic();
    }

    @Override
	public void run() {
      if (running.compareAndSet(false, true)) {
        try {
          task.run();
        } finally {
          if (isPeriodic()) {
            running.set(false);
          } else {
            executor.remove(this);
          }
        }
      }
    }

    @Override
    public String toString() {
      return runnable.toString();
    }
  }
}
