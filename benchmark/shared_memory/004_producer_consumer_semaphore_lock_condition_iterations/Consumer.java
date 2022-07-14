import java.util.concurrent.Semaphore;

public class Consumer extends Thread {
    private Buffer_Semaphore sharedObject1;
    private Buffer_With_Lock_Condition sharedObject2;
    private Semaphore tokenToProducer, tokenToConsumer;
    private int numberOfIterations;
    private int path;
    
    public void setSemaphoreTokenToConsumer(Semaphore tokenToConsumer){
    	this.tokenToConsumer = tokenToConsumer;
    }
    
    public void setSemaphoreTokenToProducer(Semaphore tokenToProducer){
    	this.tokenToProducer = tokenToProducer;
    }
    
    public void setBuffer_Semaphore(Buffer_Semaphore sharedObject1){
    	this.sharedObject1 = sharedObject1;
    }
    
    public void setBuffer_With_Lock_Condition(Buffer_With_Lock_Condition sharedObject2){
    	this.sharedObject2 = sharedObject2;
    }
    
    public void setSharedObject(int numberOfIterations, int path) {
        this.numberOfIterations = numberOfIterations;
        this.path = path;
    }

    public void run(){
        if (path == 0) {
            for (int i = 0; i < numberOfIterations; i++) {
                tokenToConsumer.acquireUninterruptibly(); 
                sharedObject1.getSharedValue();
                tokenToProducer.release(); 
            }  
        } else{
    		   
            for (int i = 0; i < numberOfIterations; i++) {
                //try {
                    try {
						sharedObject2.getSharedValue();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
               
                //    } catch (InterruptedException e) {}
            } 
        }  
    }
}
