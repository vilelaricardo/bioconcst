import java.util.concurrent.Semaphore;

public class Celebrant extends Thread {
    private Semaphore celebrationGate;

    public void setCelebrationGate(Semaphore celebrationGate) {
        this.celebrationGate = celebrationGate;
    }

    public void run() {
        celebrationGate.release();
    }
}
