import java.util.concurrent.Semaphore;

public class GcdThread extends Thread {
    private Buffer sharedObject;
    int firstValueSlave;
    int secondValueSlave;

    public void setGcdThread(Buffer sharedObject, int firstValueSlave, int secondValueSlave) {
        this.sharedObject = sharedObject;
        this.firstValueSlave = firstValueSlave;
        this.secondValueSlave = secondValueSlave;
    }

    public void run() {
        try {
            sleep(10);
        } catch (InterruptedException e) {}

        while (firstValueSlave != secondValueSlave) {
            if (firstValueSlave < secondValueSlave) {
                secondValueSlave = secondValueSlave - firstValueSlave;
            } else {
                firstValueSlave = firstValueSlave - secondValueSlave;
            }
            sharedObject.totalAmountOfComputation++;
        }
        sharedObject.gcdResult = firstValueSlave;
    }
}