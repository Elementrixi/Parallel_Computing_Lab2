package org.alpashaev;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

class Task {
    private final int id;
    private final int executionTime;

    public Task(int id) {
        this.id = id;
        Random rand = new Random();
        this.executionTime = rand.nextInt(6) + 5;
    }


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

    public int getId() {
        return id;
    }
}

class WorkerThread extends Thread {
    private final Queue<Task> taskQueue;
    private boolean running = true;

    public WorkerThread(Queue<Task> taskQueue) {
        this.taskQueue = taskQueue;
    }

    @Override
    public void run() {
        while (running) {
            Task task = null;
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

    public synchronized void shutdown() {
        running = false;
        interrupt();
    }

    public synchronized void setRunning(boolean status) {
        running = status;
    }
}

class ThreadPool {
    private final WorkerThread[] threads;
    private final Queue<Task> taskQueue;
    private long maxQueueFullTime = 0;
    private long minQueueFullTime = Long.MAX_VALUE;
    private int sizePrev = 0;
    private static long queueFullStartTime = 0;
    boolean flag = false;



    public ThreadPool(int nThreads) {
        this.threads = new WorkerThread[nThreads];
        this.taskQueue = new LinkedList<>();

        for (int i = 0; i < nThreads; i++) {
            threads[i] = new WorkerThread(taskQueue);
            threads[i].start();
        }
    }


    public synchronized void submitTask(Task task) {
        synchronized (taskQueue) {
            if (taskQueue.size() == 20) {
                if (!flag) {
                    queueFullStartTime = System.currentTimeMillis();
                    flag = true;
                }
                sizePrev = 20;
//                System.err.println("Task queue is full. Task " + ((Task) task).getId()+ " dropped.");
                return;
            }
            synchronized (taskQueue) {
                if (sizePrev == 20 && taskQueue.size() < 20) {
                    sizePrev = taskQueue.size();
                    flag = false;
                    updateQueueFullTime();
                }
            }
            taskQueue.offer(task);
            taskQueue.notify();
        }
    }

    public synchronized void pause(long duration) throws InterruptedException {
        Thread.sleep(duration);
    }

    public synchronized void shutdown() {
        for (WorkerThread thread : threads) {
            thread.shutdown();
        }
    }

    public synchronized void shutdownAndExecute() {
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

    public synchronized void updateQueueFullTime() {
        long endTime = System.currentTimeMillis();
        long duration = (endTime - queueFullStartTime);
        if (duration > maxQueueFullTime) {
            maxQueueFullTime = duration;
        }
        if (duration < minQueueFullTime && duration > 1) {
            minQueueFullTime = duration;
        }
    }

    public long getMaxQueueFullTime() {
        return maxQueueFullTime;
    }

    public long getMinQueueFullTime() {
        return minQueueFullTime;
    }

}

class Main {
    public static void main(String[] args) throws InterruptedException {
        ThreadPool threadPool = new ThreadPool(6);

        long startTime = System.currentTimeMillis();

        final long[] totalWaitingTime = {0};
        final int[] totalTasksStarted = {0};

        long testDuration = 10 * 1000;
        long endTime = startTime + testDuration;

        Thread taskAdderThread = new Thread(() -> {
            while (System.currentTimeMillis() < endTime) {
                Thread thread1 = new Thread(() -> addTasks(threadPool, totalWaitingTime, totalTasksStarted, endTime));
                Thread thread2 = new Thread(() -> addTasks(threadPool, totalWaitingTime, totalTasksStarted, endTime));
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
//        threadPool.shutdownAndExecute();
        threadPool.shutdown();

        double averageWaitingTime = (double) totalWaitingTime[0] / totalTasksStarted[0];
        threadPool.pause(5000);
        long endTimeAll = System.currentTimeMillis() - startTime;

        System.out.println("Total Tasks Started: " + totalTasksStarted[0]);
        System.out.printf("Average Waiting Time per Thread: %.10f milliseconds\n", averageWaitingTime);
        System.out.println("Maximum Time Task Waited in Queue: " + threadPool.getMaxQueueFullTime() + " milliseconds");
        System.out.println("Minimum Time Task Waited in Queue: " + threadPool.getMinQueueFullTime() + " milliseconds");
        System.out.println("Total time: " + endTimeAll / 1000.0);
    }

    private static synchronized void addTasks(ThreadPool threadPool, long[] totalWaitingTime, int[] totalTasksExecuted, long endTime) {
        while (System.currentTimeMillis() < endTime) {
            int taskId = totalTasksExecuted[0] + 1;
            Task task = new Task(taskId);
            long taskStartTime = System.currentTimeMillis();
            threadPool.submitTask(task);

            long threadWaitTime = System.currentTimeMillis() - taskStartTime;
            totalWaitingTime[0] += threadWaitTime;

            totalTasksExecuted[0]++;
        }
    }
}