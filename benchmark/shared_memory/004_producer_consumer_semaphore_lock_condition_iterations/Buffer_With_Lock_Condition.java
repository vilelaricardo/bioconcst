import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Buffer_With_Lock_Condition {
    private float sharedValue = 2;
    private boolean writeable = false;  // conditional variable
    
    public Lock acessLock;
    public Condition canWrite;
    public Condition canRead;

    // increments the shared variable
    public void setIncrementSharedValue() throws InterruptedException {
        acessLock.lock();
        while (writeable) {
            canWrite.await();
        }
        sharedValue++;
        setDetails();
        writeable = true;
        canRead.signal();
        acessLock.unlock();
    }
    
    // multiply the shared variable
    public void setMultiplySharedValue() throws InterruptedException {
        acessLock.lock();
        while (writeable) {
            canWrite.await();
        }
        sharedValue = sharedValue * 2;
        setDetails();
        writeable = true;
        canRead.signal();
        acessLock.unlock();
    }

    // get the shared variable
    public float getSharedValue() throws InterruptedException {
        acessLock.lock();
        while (!writeable) {
            canRead.await();
        }
        getDetails();   
        writeable = false;
         canWrite.signal();  // Fault - nonexistent signal
        acessLock.unlock();
        return sharedValue;
    }
    
    private void setDetails() {
       // System.out.println ( Thread.currentThread().getName() + " writes lock = " + sharedValue );
        System.out.println(" writes lock = " + sharedValue );
    }
    
    private void getDetails() {
        //System.out.println ( Thread.currentThread().getName() + " reads lock = " + sharedValue );
        System.out.println(" reads lock = " + sharedValue );
    }
}
