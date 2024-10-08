import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

public class GroceryQueues {
    private final int numQueues;
    private final int maxQueueLength;
    private final List<BlockingQueue<Customer>> queues;
    private final List<ReentrantLock> locks;
    private final AtomicBoolean running;
    private final ExecutorService executorService;

    public GroceryQueues(int numQueues, int maxQueueLength) {
        this.numQueues = numQueues;
        this.maxQueueLength = maxQueueLength;
        this.queues = new ArrayList<>();
        this.locks = new ArrayList<>();
        for (int i = 0; i < numQueues; i++) {
            queues.add(new LinkedBlockingQueue<>(maxQueueLength));
            locks.add(new ReentrantLock());
        }
        this.running = new AtomicBoolean(true);
        this.executorService = Executors.newFixedThreadPool(numQueues);
    }

    public boolean addCustomer(Customer customer) {
        List<Integer> shortestQueues = new ArrayList<>();
        int minSize = Integer.MAX_VALUE;

        for (int i = 0; i < numQueues; i++) {
            int size = queues.get(i).size();
            if (size < minSize) {
                shortestQueues.clear();
                shortestQueues.add(i);
                minSize = size;
            } else if (size == minSize) {
                shortestQueues.add(i);
            }
        }

        if (minSize >= maxQueueLength) {
            int retryCount = 0;
            while (retryCount < 5) {  // Retry up to 5 times
                try {
                    Thread.sleep(2000);  // Shorter sleep
                    return addCustomer(customer); // Try again
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false; // Exceeding retries, customer leaves.
        }

        int chosenQueue = shortestQueues.get(new Random().nextInt(shortestQueues.size()));
        return queues.get(chosenQueue).offer(customer);
    }

    public void processCustomers(AtomicInteger customersServed, AtomicLong totalServiceTime) {
        for (int i = 0; i < numQueues; i++) {
            final int queueIndex = i;
            executorService.submit(() -> {
                while (running.get()) {
                    try {
                        locks.get(queueIndex).lock();
                        Customer customer = queues.get(queueIndex).poll(100, TimeUnit.MILLISECONDS);
                        if (customer != null) {
                            customer.setStartServiceTime(System.currentTimeMillis());
                            customer.setServed(true);

                            int serviceTime = customer.getServiceTime();
                            Thread.sleep(serviceTime * 1000L);

                            customer.setEndServiceTime(System.currentTimeMillis());

                            customersServed.incrementAndGet();
                            totalServiceTime.addAndGet(serviceTime * 1000L);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        locks.get(queueIndex).unlock();
                    }
                }
            });
        }
    }

    // Shutdown the queues processing
    public void shutdown() {
        running.set(false);
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        System.out.println("GroceryQueues is shutting down.");
    }
}
