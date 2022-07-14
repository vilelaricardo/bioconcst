import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CyclicBarrier;

public class Producer extends Thread {
    private Buffer object;
    private Semaphore tokenToProducer;
    private int operation;
    private CyclicBarrier barrier1;
    private CyclicBarrier barrier2;

    public void setProducer(int operation, Semaphore tokenToProducer, CyclicBarrier barrier1, CyclicBarrier barrier2) {
        this.operation = operation;
        this.tokenToProducer = tokenToProducer;
        this.barrier1 = barrier1;
        this.barrier2 = barrier2;
    }

    public void setSharedObject(Buffer object) {
        this.object = object;
    }

    public void run() {
        if (operation == 0) {
            try {
                barrier1.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
            tokenToProducer.acquireUninterruptibly();
            object.setSharedIntIncrement();
            tokenToProducer.release();
            try {
                barrier2.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        }
        if (operation == 1) {
            try {
                barrier1.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
            tokenToProducer.acquireUninterruptibly();
            object.setSharedIntMultiplie();
            tokenToProducer.release();
            try {
                barrier2.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        }
    }
}