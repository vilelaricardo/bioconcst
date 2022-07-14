import java.util.concurrent.Semaphore;

public class Producer extends Thread {
    private Buffer object;
    private Semaphore tokenToProducer;
    private int operation;

    public void setProducer(Semaphore tokenToProducer, int operation) {
        this.operation = operation;
        this.tokenToProducer = tokenToProducer;
    }
    
    public void setSharedObject(Buffer object) {
        this.object = object;
    }

    public void run() {
        if (operation == 0) {
            tokenToProducer.acquireUninterruptibly();
            object.setSharedIntIncrement();
            tokenToProducer.release();
        }
        if (operation == 1) {
            tokenToProducer.acquireUninterruptibly();
            object.setSharedIntMultiplie();
            tokenToProducer.release();
        }
    }
}
