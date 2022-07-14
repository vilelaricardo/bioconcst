import java.util.concurrent.Semaphore;

public class Producer extends Thread {
    private Buffer_Semaphore sharedObject1;
    private Buffer_With_Lock_Condition sharedObject2;
    private Semaphore tokenToProducer, tokenToConsumer;
    private int numberOfIterations;
    private int operation;
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
    
    public void setSharedObject(int operation, int numberOfIterations, int path) {
        this.operation = operation;
        this.numberOfIterations = numberOfIterations;
        this.path = path;
    }

    public void run() {
        if (path == 0) {
            if (operation == 0) {
                for (int i = 0; i < numberOfIterations; i++) {
                    tokenToProducer.acquireUninterruptibly(); 
                    sharedObject1.setIncrementSharedValue();
                    tokenToConsumer.release();
                }
            }
            if (operation == 1) {
                for (int i = 0; i < numberOfIterations; i++) {
                    tokenToProducer.acquireUninterruptibly(); 
                    sharedObject1.setMultiplySharedValue();
                    tokenToConsumer.release();
                }
            }
        }
       	  else{
            if (operation == 0) {
                for (int i = 0; i < numberOfIterations; i++) {
                    try {
                        sharedObject2.setIncrementSharedValue();
                    } catch (Exception e) {}
                }
            }
            else{
                for (int i = 0; i < numberOfIterations; i++) {
                    try {
                        sharedObject2.setMultiplySharedValue();
                    } catch (Exception e) {}
                }
            }   
        }  
    }
}
