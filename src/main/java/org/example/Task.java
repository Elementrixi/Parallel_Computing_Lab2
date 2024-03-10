package org.example;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

class Task implements Runnable {
    private final int id;
    private final int executionTime;

    public Task(int id) {
        this.id = id;
        Random rand = new Random();
        this.executionTime = rand.nextInt(6) + 5;
    }

    @Override
    public void run() {
        System.out.println("Task " + id + " started. Executing for " + executionTime + " seconds.");
        try {
            Thread.sleep(executionTime * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Task " + id + " interrupted.");
            return;
        }
        System.out.println("Task " + id + " completed.");
    }
}

class WorkerThread extends Thread {
    private final Queue<Runnable> taskQueue;
    private boolean running = true;

    public WorkerThread(Queue<Runnable> taskQueue) {
        this.taskQueue = taskQueue;
    }

    @Override
    public void run() {
        while (running) {
            Runnable task = null;
            synchronized (taskQueue) {
                while (taskQueue.isEmpty() && running) {
                    try {
                        taskQueue.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (running) {
                    task = taskQueue.poll();
                }
            }
            if (task != null) {
                task.run();
            }
        }
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    public void setRunning(boolean status) {
        running = status;
    }
}

class ThreadPool {
    private final WorkerThread[] threads;
    private final Queue<Runnable> taskQueue;

    public ThreadPool(int nThreads) {
        this.threads = new WorkerThread[nThreads];
        this.taskQueue = new LinkedList<>();

        for (int i = 0; i < nThreads; i++) {
            threads[i] = new WorkerThread(taskQueue);
            threads[i].start();
        }
    }

    public void submitTask(Runnable task) {
        synchronized (taskQueue) {
            if (taskQueue.size() >= 20) {
                //  System.err.println("Task queue is full. Task dropped.");
                return;
            }
            taskQueue.offer(task);
            taskQueue.notify();
        }
    }

    public synchronized void pause(long duration) throws InterruptedException {
        Thread.sleep(duration);
    }

    public int getCurrentQueueSize() {
        return taskQueue.size();
    }

    public void shutdown() {
        for (WorkerThread thread : threads) {
            thread.shutdown();
        }
    }

    public void shutdownAndExecute() {
        for (WorkerThread thread : threads) {
            thread.setRunning(false);
        }
        taskQueue.clear();

        for (WorkerThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }
    }
}

class Main1 {
    public static void main(String[] args) throws InterruptedException {
        ThreadPool threadPool = new ThreadPool(6);

        long startTime = System.currentTimeMillis();

        final long[] totalWaitingTime = {0};
        final int[] totalTasksStarted = {0};
        final int[] totalTasksDropped = {0};
        final long[] maxQueueWaitTime = {0};
        final long[] minQueueWaitTime = {Long.MAX_VALUE};

        long testDuration = 30 * 1000;
        long endTime = startTime + testDuration;

        Thread taskAdderThread = new Thread(() -> {
            while (System.currentTimeMillis() < endTime) {
                Thread thread1 = new Thread(() -> addTasks(threadPool, totalWaitingTime, totalTasksStarted, totalTasksDropped, maxQueueWaitTime, minQueueWaitTime, endTime));
                Thread thread2 = new Thread(() -> addTasks(threadPool, totalWaitingTime, totalTasksStarted, totalTasksDropped, maxQueueWaitTime, minQueueWaitTime, endTime));
                thread1.start();
                thread2.start();

                try {
                    thread1.join();
                    thread2.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        taskAdderThread.start();

        taskAdderThread.join();
        final String ANSI_GREEN = "\u001B[32m";
        final String ANSI_RESET = "\u001B[0m";

        System.out.println(ANSI_GREEN + "\nPool in shutting down\n" + ANSI_RESET);
        threadPool.shutdownAndExecute();
//        threadPool.shutdown();

        double averageWaitingTime = (double) totalWaitingTime[0] / totalTasksStarted[0];
        threadPool.pause(5000);
        long endTimeAll = System.currentTimeMillis() - startTime;

        System.out.println("Total Tasks Started: " + totalTasksStarted[0]);
        System.out.println("Total Tasks Dropped: " + totalTasksDropped[0]);
        System.out.println("Total Tasks Executed: " + (totalTasksStarted[0] - totalTasksDropped[0]));
        System.out.printf("Average Waiting Time per Thread: %.10f milliseconds\n", averageWaitingTime);
        System.out.println("Maximum Time Task Waited in Queue: " + maxQueueWaitTime[0] + " milliseconds");
        System.out.println("Minimum Time Task Waited in Queue: " + minQueueWaitTime[0] + " milliseconds");
        System.out.println("Total time: " + endTimeAll);
    }

    private static synchronized void addTasks(ThreadPool threadPool, long[] totalWaitingTime, int[] totalTasksExecuted, int[] totalTasksDropped, long[] maxQueueWaitTime, long[] minQueueWaitTime, long endTime) {
        while (System.currentTimeMillis() < endTime) {
            int taskId = totalTasksExecuted[0] + 1;
            Task task = new Task(taskId);
            long taskStartTime = System.currentTimeMillis();
            threadPool.submitTask(task);

            long threadWaitTime = System.currentTimeMillis() - taskStartTime;
            totalWaitingTime[0] += threadWaitTime;

            int currentQueueSize = threadPool.getCurrentQueueSize();

            if (currentQueueSize >= 20) {
                if (threadWaitTime > maxQueueWaitTime[0]) {
                    maxQueueWaitTime[0] = threadWaitTime;
                }
                if (threadWaitTime < minQueueWaitTime[0]) {
                    minQueueWaitTime[0] = threadWaitTime;
                }
                totalTasksDropped[0]++;
            }

            totalTasksExecuted[0]++;
        }
    }
}