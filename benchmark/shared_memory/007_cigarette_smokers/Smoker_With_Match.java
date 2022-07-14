import java.util.concurrent.Semaphore;

public class Smoker_With_Match extends Thread {
    private Semaphore agentSemaphore;
    private Semaphore tobaccoSemaphore;
    private Semaphore matchSemaphore;
    private Semaphore paperSemaphore;
    private SharedObject status;
    
    public void setSmoker_With_Match(SharedObject status) {
        this.status = status;
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
            matchSemaphore.acquireUninterruptibly();
            if (status.getStatus() == false) {
            status.setStatus();
            makeCigarrete();
            agentSemaphore.release();
            smoke();
            
            paperSemaphore.release();
            tobaccoSemaphore.release();
            }
    }

    private void makeCigarrete() {
        // The smoker with match is making the cigarette
        try {
            Thread.sleep(500);
        } catch (Exception ie) {}
    }

    private void smoke() {
        System.out.println("The smoker with match smoke the cigarette!");
        try {
            Thread.sleep(500);
        } catch (Exception ie) {}
    }
}