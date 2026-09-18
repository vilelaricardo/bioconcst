/**
 * Synthetic Benchmark - Threshold Handshake (Worker)
 *
 * Shared-memory sibling of quorum-handshake: same two-tier structure (an
 * easy per-thread signal, a hard compound target requiring both threads to
 * land in their secret window in the same test case), but using semaphore
 * handoffs on shared state instead of sockets between separate processes.
 */
import java.util.concurrent.Semaphore;

public class Worker extends Thread {
    static final int WINDOW_LOW = 480;
    static final int WINDOW_HIGH = 519;

    private int value;
    private boolean isWorker1;
    private SharedState state;
    private Semaphore doneGate;

    public void setValue(int value) {
        this.value = value;
    }

    public void setIsWorker1(boolean isWorker1) {
        this.isWorker1 = isWorker1;
    }

    public void setSharedState(SharedState state) {
        this.state = state;
    }

    public void setDoneGate(Semaphore doneGate) {
        this.doneGate = doneGate;
    }

    public void run() {
        boolean inWindow = (value >= WINDOW_LOW) && (value <= WINDOW_HIGH);

        // doneGate.release() is duplicated in each branch (instead of being
        // called once after the if/else) so ValiPar's static analysis sees
        // two distinct call sites - same lesson as quorum-handshake's
        // Peer.java: a single shared call site can't distinguish which
        // branch ran, so landing in the window would be invisible to the
        // coverage model.
        if (inWindow) {
            if (isWorker1) {
                state.worker1InWindow = true;
            } else {
                state.worker2InWindow = true;
            }
            doneGate.release();
        } else {
            doneGate.release();
        }
    }
}
