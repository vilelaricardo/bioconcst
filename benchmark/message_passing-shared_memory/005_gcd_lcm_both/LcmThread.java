import java.util.concurrent.Semaphore;

public class LcmThread extends Thread {
    private Buffer sharedObject;
    int firstValueSlave;
    int secondValueSlave;
    int lcm;

    public void setLcmThread(Buffer sharedObject, int firstValueSlave, int secondValueSlave) {
        this.sharedObject = sharedObject;
        this.firstValueSlave = firstValueSlave;
        this.secondValueSlave = secondValueSlave;
    }

    public void run() {
        try {
            sleep(10);
        } catch (InterruptedException e) {}

        if (firstValueSlave == secondValueSlave) {
            lcm = firstValueSlave;
            sharedObject.lcmResult = lcm;
        }
        
        if (secondValueSlave == 1) {
            lcm = firstValueSlave;
            sharedObject.lcmResult = lcm;
        }
        
        if (firstValueSlave == 1) {
            lcm = secondValueSlave;
            sharedObject.lcmResult = lcm;
        } else {
            lcm = 0;
        }

        if (lcm == 0) {
            int tempFirstValueSlave = firstValueSlave, tempSecondValueSlave = secondValueSlave;
            while (tempFirstValueSlave != tempSecondValueSlave) {
                while (tempFirstValueSlave < tempSecondValueSlave) {
                    tempFirstValueSlave += firstValueSlave;
                    sharedObject.totalAmountOfComputation++;
                }
                while (tempSecondValueSlave < tempFirstValueSlave) {
                    tempSecondValueSlave += secondValueSlave;
                    sharedObject.totalAmountOfComputation++;
                }
            }
            lcm = tempFirstValueSlave;
            sharedObject.lcmResult = lcm;
        }
    }
}