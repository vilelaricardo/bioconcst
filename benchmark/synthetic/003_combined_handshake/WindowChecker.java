/**
 * Synthetic Benchmark - Combined Handshake (WindowChecker)
 *
 * Shared-memory tier of the combined (message-passing + shared-memory)
 * sibling of quorum-handshake/threshold-handshake: each Peer delegates its
 * own secret-window check to this internal thread instead of doing it
 * directly, so the compound target routes through both a shared-memory
 * handoff (this thread signaling the Peer's main thread) and a
 * message-passing handoff (the Peer then voting to the Coordinator).
 */
import java.util.concurrent.Semaphore;

public class WindowChecker extends Thread {
    static final int WINDOW_LOW = 480;
    static final int WINDOW_HIGH = 519;

    private int value;
    private PeerState state;
    private Semaphore doneGate;

    public void setValue(int value) {
        this.value = value;
    }

    public void setPeerState(PeerState state) {
        this.state = state;
    }

    public void setDoneGate(Semaphore doneGate) {
        this.doneGate = doneGate;
    }

    public void run() {
        boolean inWindow = (value >= WINDOW_LOW) && (value <= WINDOW_HIGH);

        // doneGate.release() duplicated per branch - same lesson as
        // quorum-handshake's Peer.java and threshold-handshake's Worker.java:
        // a single shared call site can't distinguish which branch ran.
        if (inWindow) {
            state.inWindow = true;
            doneGate.release();
        } else {
            doneGate.release();
        }
    }
}
