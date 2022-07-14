/**
 * Concurrent Benchmarks
 * 
 * Title:  Producer-Consumer with Semaphore and Locks/Condition     
 * 
 * Description:  This benchmark implements the producer-consumer program
 *               using locks, conditions variables and semaphore. 
 * 
 * Paradigm:     Shared Memory               
 *               
 * Year:         2014
 * Company:      ICMC/USP - São Carlos
 *               University of São Paulo (USP)
 *               Institute of Mathematics and Computer Science (ICMC)
 *               
 * @author       George Gabriel Mendes Dourado
 * @version      1.0
 */

/* TEST 1
 * java Main 4 1 2 0
 * 
 * OUTPUT:
 * Thread-0 writes lock = 4.0
 * Thread-2 reads lock = 4.0
 * Thread-0 writes lock = 8.0
 * Thread-2 reads lock = 8.0
 * Thread-1 writes lock = 16.0
 * Thread-3 reads lock = 16.0
 * Thread-1 writes lock = 32.0
 * Thread-3 reads lock = 32.0
 */

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        int numberOfThreads = 4; // total number of threads
        int operation = 0; // if 0 increments, if 1 multiply
        int numberOfIterations = 1; // amount of iterations that each thread make in the shared variable
        int path = 0; // if 0 -> use buffer with semaphore, if 1 -> use buffer with locks and conditions
        boolean flag = true;


	    numberOfThreads = Integer.parseInt(args[0]);  // fix 4
            operation = Integer.parseInt(args[1]); // 0 ou 1
            numberOfIterations = Integer.parseInt(args[2]); // 1+
            path = Integer.parseInt(args[3]); //0 ou 1 

                if((operation>-1) && (operation<2) && (numberOfIterations>=1) && (numberOfIterations<=10)){

	if(path==0 || path==1){

        
        Buffer_Semaphore sharedObject1 = new Buffer_Semaphore();
        Buffer_With_Lock_Condition sharedObject2 = new Buffer_With_Lock_Condition();
        sharedObject2.acessLock = new ReentrantLock();
        sharedObject2.canWrite = sharedObject2.acessLock.newCondition();
        sharedObject2.canRead = sharedObject2.acessLock.newCondition();
        
        Semaphore tokenToProducer = new Semaphore(0);
        tokenToProducer.release();
        Semaphore tokenToConsumer = new Semaphore(0);
        
        Producer producer0 = new Producer();
        producer0.setSharedObject(operation, numberOfIterations, path);
        producer0.setBuffer_Semaphore(sharedObject1);
        producer0.setBuffer_With_Lock_Condition(sharedObject2);
        producer0.setSemaphoreTokenToConsumer(tokenToConsumer);
        producer0.setSemaphoreTokenToProducer(tokenToProducer);
        producer0.start();
                    
        Producer producer1 = new Producer();
        producer1.setSharedObject(operation, numberOfIterations, path);
        producer1.setBuffer_Semaphore(sharedObject1);
        producer1.setBuffer_With_Lock_Condition(sharedObject2);
        producer1.setSemaphoreTokenToConsumer(tokenToConsumer);
        producer1.setSemaphoreTokenToProducer(tokenToProducer);
        producer1.start();
        
        
        Consumer consumer0 = new Consumer();
        consumer0.setSharedObject(numberOfIterations, path);
        consumer0.setBuffer_Semaphore(sharedObject1);
        consumer0.setBuffer_With_Lock_Condition(sharedObject2);
        consumer0.setSemaphoreTokenToConsumer(tokenToConsumer);
        consumer0.setSemaphoreTokenToProducer(tokenToProducer);
        consumer0.start();
        
        Consumer consumer1 = new Consumer();
        consumer1.setSharedObject(numberOfIterations, path);
        consumer1.setBuffer_Semaphore(sharedObject1);
        consumer1.setBuffer_With_Lock_Condition(sharedObject2);
        consumer1.setSemaphoreTokenToConsumer(tokenToConsumer);
        consumer1.setSemaphoreTokenToProducer(tokenToProducer);
        consumer1.start();
        
        
        producer0.join();
        consumer0.join();
        
        producer1.join();
        consumer1.join();
    }}
}
}
