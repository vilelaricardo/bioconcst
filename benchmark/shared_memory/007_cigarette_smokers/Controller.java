import java.util.concurrent.Semaphore;

public class Controller extends Thread {
    private int choice1;
    private int choice2;
    private boolean isTobacco = false;
    private boolean isPaper = false;
    private boolean isMatch = false;
    private Semaphore agentSemaphore;
    private Semaphore tobaccoSemaphore;
    private Semaphore paperSemaphore;
    private Semaphore matchSemaphore;
    
    public void setController(int choice1, int choice2, boolean isTobacco, boolean isPaper, boolean isMatch) {
        this.choice1 = choice1;
        this.choice2 = choice2;
        this.isTobacco = isTobacco;
        this.isPaper = isPaper;
        this.isMatch = isMatch;
    }
    
    public void setAgentSemaphore(Semaphore agentSemaphore){
    	this.agentSemaphore = agentSemaphore;
    }
            
    public void setTobaccoSemaphore(Semaphore tobaccoSemaphore){
        this.tobaccoSemaphore = tobaccoSemaphore;	
    }
    
    public void setPaperSemaphore(Semaphore paperSemaphore){
        this.paperSemaphore = paperSemaphore;	
    }
    
    public void setMatchSemaphore(Semaphore matchSemaphore){
        this.matchSemaphore = matchSemaphore;	
    }
    
    public void run() {
        if (choice1 == 0 || choice2 == 0) {
            // The controller puts tobacco on the table
            tobaccoSemaphore.acquireUninterruptibly();
            agentSemaphore.acquireUninterruptibly();
            if (isPaper) {
                isPaper = false;
                matchSemaphore.release();
            }
            if (isMatch) {
                isMatch = false;
                paperSemaphore.release();
            }
            if (!isPaper && !isMatch) {
                isTobacco = true;
            }
        }
        if (choice1 == 1 || choice2 == 1) {
            // The controller puts paper on the table
            paperSemaphore.acquireUninterruptibly();
            agentSemaphore.acquireUninterruptibly();
            if (isMatch) {
                isMatch = false;
                tobaccoSemaphore.release();
            }
            if (isTobacco) {
                isTobacco = false;
                matchSemaphore.release();
            } 
            if (!isTobacco && !isMatch) {
                isPaper = true;
            }
        }
        if (choice1 == 2 || choice2 == 2) {
            // The controller puts match on the table
            matchSemaphore.acquireUninterruptibly();
            agentSemaphore.acquireUninterruptibly();
            if (isTobacco) {
                isTobacco = false;
                paperSemaphore.release();
            }
            if (isPaper) {
                isPaper = false;
                tobaccoSemaphore.release();
            }
            if (!isTobacco && !isPaper) {
                isMatch = true;
            }
        }
    }
}