/**
 * Synthetic Benchmark - Threshold Handshake
 *
 * Shared-memory sibling of quorum-handshake (see benchmark/synthetic/
 * 001_quorum_handshake). Two Worker threads each only signal "in window"
 * when their own evolved value lands inside a narrow secret window. Main
 * only starts the Celebrant thread - the "celebration" sync edge - when
 * BOTH workers landed in their window in the same test case: a compound
 * condition across independently evolved arguments, same difficulty class
 * as quorum-handshake, expressed with semaphore handoffs on shared state
 * within a single process instead of sockets between separate processes.
 *
 * java ThresholdHandshake <value1> <value2>
 */
import java.util.concurrent.Semaphore;

public class ThresholdHandshake {
    public static void main(String[] args) throws InterruptedException {
        int value1 = Integer.parseInt(args[0]);
        int value2 = Integer.parseInt(args[1]);

        SharedState state = new SharedState();
        Semaphore doneGate1 = new Semaphore(0);
        Semaphore doneGate2 = new Semaphore(0);

        Worker w1 = new Worker();
        w1.setValue(value1);
        w1.setIsWorker1(true);
        w1.setSharedState(state);
        w1.setDoneGate(doneGate1);
        w1.start();

        Worker w2 = new Worker();
        w2.setValue(value2);
        w2.setIsWorker1(false);
        w2.setSharedState(state);
        w2.setDoneGate(doneGate2);
        w2.start();

        doneGate1.acquireUninterruptibly();
        doneGate2.acquireUninterruptibly();

        if (state.worker1InWindow && state.worker2InWindow) {
            Semaphore celebrationGate = new Semaphore(0);
            Celebrant celebrant = new Celebrant();
            celebrant.setCelebrationGate(celebrationGate);
            celebrant.start();
            celebrationGate.acquireUninterruptibly();
            System.out.println("Threshold reached! Celebrating.");
        } else {
            System.out.println("No threshold. Continuing normally.");
        }

        w1.join();
        w2.join();
    }
}
