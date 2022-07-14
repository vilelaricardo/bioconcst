/**
 * Concurrent Benchmarks
 * 
 * Title:  010_jacobi     
 * 
 * Description:  This benchmark implements the Jacobi method used to 
 * 		         find the resolution of a random linear system. 
 * 
 * Paradigm:     Shared Memory               
 *               
 * Year:         2014
 * Company:      ICMC/USP - São Carlos
 *               University of São Paulo (USP)
 *               Institute of Mathematics and Computer Science (ICMC)
 *               
 * @author       Paulo Sérgio Lopes de Souza      
 * @java_code    George Gabriel Mendes Dourado
 * @version      1.0
 */

/* TEST 1
 * java Master 2 0 0
 * 
 * OUTPUT:
 * Iterations:9, x[0]=3.698, x[1]=-3.899 
 * Final iteration:9, max_dif=0.0000 max_x=0.0000  mr=0.0005  
 * A[0][0]=4.00   A[0][1]=2.00        B[0]=7.00 
 * A[1][0]=1.00   A[1][1]=3.00        B[1]=-8.00 
 * 
 * Applying x [*] in the row 0 of A[ 0 ][ * ], the result is: 6.996. 
 * B[0] = 7.000
 */

import java.util.Random;
import java.util.concurrent.Semaphore;

public class Master {
    public static final float precision = 0.001f;

    public static void main(String[] args) throws Exception {
        float mr = 1;
        float sum = 0;
        int matrixOrder = 2;
        int thread_semaphore_parameter = 1;
        int master_semaphore_parameter = 1;
        int iterations = 0;

      
            matrixOrder = Integer.parseInt(args[0]);
            thread_semaphore_parameter = Integer.parseInt(args[1]);
            master_semaphore_parameter = Integer.parseInt(args[2]);

        int synch[] = new int[matrixOrder];
        float A[][] = new float[matrixOrder][matrixOrder];
        float AStar[][] = new float[matrixOrder][matrixOrder];
        float B[] = new float[matrixOrder];
        float x[] = new float[matrixOrder];
        float x_temp[] = new float[matrixOrder];
        float dif[] = new float[matrixOrder];
        float sumRow[] = new float[matrixOrder];
        
        SharedVariables shared = new SharedVariables();
        
        //Semaphore row_mutex[] = new Semaphore[matrixOrder];
        Semaphore row_mutex_0 = new Semaphore(0);
        Semaphore row_mutex_1 = new Semaphore(0);
        
        //Semaphore multiple_mutex[] = new Semaphore[matrixOrder];
        Semaphore multiple_mutex_0 = new Semaphore(0);
        Semaphore multiple_mutex_1 = new Semaphore(0);
        
        //Semaphore semaphore_thread1[][] = new Semaphore[matrixOrder][matrixOrder];
        Semaphore semaphore_thread1_0_0 = new Semaphore(0);
        Semaphore semaphore_thread1_0_1 = new Semaphore(0);
        Semaphore semaphore_thread1_1_0 = new Semaphore(0);
        Semaphore semaphore_thread1_1_1 = new Semaphore(0);
        
        //Semaphore semaphore_thread2[][] = new Semaphore[matrixOrder][matrixOrder];
        Semaphore semaphore_thread2_0_0 = new Semaphore(0);
        Semaphore semaphore_thread2_0_1 = new Semaphore(0);
        Semaphore semaphore_thread2_1_0 = new Semaphore(0);
        Semaphore semaphore_thread2_1_1 = new Semaphore(0);
        
        Semaphore continues_mutex = new Semaphore(0);
        continues_mutex.release();
        Semaphore synch_threads_mutex = new Semaphore(0);
        synch_threads_mutex.release();

        Semaphore master_semaphore1 = new Semaphore(0);
        Semaphore master_semaphore2 = new Semaphore(0);

        row_mutex_0 = new Semaphore(0);
        row_mutex_0.release();
        multiple_mutex_0 = new Semaphore(0);
        multiple_mutex_0.release();
        synch[0] = 0;
        sumRow[0] = 0;
            
        row_mutex_1 = new Semaphore(0);
        row_mutex_1.release();
        multiple_mutex_1 = new Semaphore(0);
        multiple_mutex_1.release();
        synch[1] = 0;
        sumRow[1] = 0;

        Random gerador = new Random();

        A[0][0] = 4;
	    A[0][1] = 2;
	    A[1][0] = 1;
	    A[1][1] = 3;

	    B[0] = 7;
	    B[1] = -8;
	
        /*for (int i = 0; i < matrixOrder; i++) {
            sum = 0;
            for (int j = 0; j < matrixOrder; j++) {
                if (i != j) {
                    while ((A[i][j] = gerador.nextInt(10)) == 0);
                }
                sum += A[i][j];
            }
            A[i][i] = ++sum;
            while ((B[i] = gerador.nextInt(10)) == 0);
        }*/
            
        int I = (int) 0 / matrixOrder;
        int J = 0 - matrixOrder * I;       //row_mutx_0
        Slave thread_handle0 = new Slave();
        thread_handle0.setSlave(0, I, J, A, B, AStar, x, x_temp, sumRow, synch, dif,
                                    thread_semaphore_parameter, master_semaphore_parameter);
        thread_handle0.setSemaphore_thread1_i_i(semaphore_thread1_0_0);
        thread_handle0.setSemaphore_thread1_i_j(semaphore_thread1_0_0);   
        thread_handle0.setSemaphore_thread2_i_i(semaphore_thread2_0_0);  
        thread_handle0.setSemaphore_thread2_i_j(semaphore_thread2_0_0); 
        thread_handle0.setSemaphore_continues_mutex(continues_mutex);
        thread_handle0.setSemaphore_synch_threads_mutex(synch_threads_mutex);
        thread_handle0.setSemaphore_multiple_mutex(multiple_mutex_0);
        thread_handle0.setSemaphore_row_mutex(row_mutex_0);
        thread_handle0.setMaster_semaphore1(master_semaphore1);
        thread_handle0.setMaster_semaphore2(master_semaphore2); 
        thread_handle0.setSharedVariables(shared);   
        thread_handle0.start();
            
        I = (int) 1 / matrixOrder;
        J = 1 - matrixOrder * I;
        Slave thread_handle1 = new Slave();   //row_mutx_0
        thread_handle1.setSlave(1, I, J, A, B, AStar, x, x_temp, sumRow, synch, dif,
                     thread_semaphore_parameter, master_semaphore_parameter);
        thread_handle1.setSemaphore_thread1_i_i(semaphore_thread1_0_0);
        thread_handle1.setSemaphore_thread1_i_j(semaphore_thread1_0_1);    
        thread_handle1.setSemaphore_thread2_i_i(semaphore_thread2_0_0); 
        thread_handle1.setSemaphore_thread2_i_j(semaphore_thread2_0_1);  
        thread_handle1.setSemaphore_continues_mutex(continues_mutex);
        thread_handle1.setSemaphore_synch_threads_mutex(synch_threads_mutex);
        thread_handle1.setSemaphore_multiple_mutex(multiple_mutex_0);
        thread_handle1.setSemaphore_row_mutex(row_mutex_0);
        thread_handle1.setMaster_semaphore1(master_semaphore1);
        thread_handle1.setMaster_semaphore2(master_semaphore2); 
        thread_handle1.setSharedVariables(shared); 
        thread_handle1.start();
            
            
        I = (int) 2 / matrixOrder;
        J = 2 - matrixOrder * I;
        Slave thread_handle2 = new Slave();   //row_mutx_1
        thread_handle2.setSlave(2, I, J, A, B, AStar, x, x_temp, sumRow, synch, dif,
                      thread_semaphore_parameter, master_semaphore_parameter);
        thread_handle2.setSemaphore_thread1_i_i(semaphore_thread1_1_1);
        thread_handle2.setSemaphore_thread1_i_j(semaphore_thread1_1_0);    
        thread_handle2.setSemaphore_thread2_i_i(semaphore_thread2_1_1); 
        thread_handle2.setSemaphore_thread2_i_j(semaphore_thread2_1_0);  
        thread_handle2.setSemaphore_continues_mutex(continues_mutex);
        thread_handle2.setSemaphore_synch_threads_mutex(synch_threads_mutex);
        thread_handle2.setSemaphore_multiple_mutex(multiple_mutex_1);
        thread_handle2.setSemaphore_row_mutex(row_mutex_1);
        thread_handle2.setMaster_semaphore1(master_semaphore1);
        thread_handle2.setMaster_semaphore2(master_semaphore2); 
        thread_handle2.setSharedVariables(shared);
        thread_handle2.start();
            
            
        I = (int) 3 / matrixOrder;
        J = 3 - matrixOrder * I;
        Slave thread_handle3 = new Slave();   //row_mutx_1
        thread_handle3.setSlave(3, I, J, A, B, AStar, x, x_temp, sumRow, synch, dif,
                      thread_semaphore_parameter, master_semaphore_parameter);
        thread_handle3.setSemaphore_thread1_i_i(semaphore_thread1_1_1);
        thread_handle3.setSemaphore_thread1_i_j(semaphore_thread1_1_1);    
        thread_handle3.setSemaphore_thread2_i_i(semaphore_thread2_1_1); 
        thread_handle3.setSemaphore_thread2_i_j(semaphore_thread2_1_1);  
        thread_handle3.setSemaphore_continues_mutex(continues_mutex);
        thread_handle3.setSemaphore_synch_threads_mutex(synch_threads_mutex);
        thread_handle3.setSemaphore_multiple_mutex(multiple_mutex_1);
        thread_handle3.setSemaphore_row_mutex(row_mutex_1);
        thread_handle3.setMaster_semaphore1(master_semaphore1);
        thread_handle3.setMaster_semaphore2(master_semaphore2); 
        thread_handle3.setSharedVariables(shared);
        thread_handle3.start();

        for (int i = 0; i < matrixOrder; i++) {
            if (master_semaphore_parameter == 1) {
                master_semaphore1.acquireUninterruptibly();
            } else {
                master_semaphore2.acquireUninterruptibly();
            }
        }

        if (!shared.getThread_continues()) {
            System.out.println("The system does not converge.\n Finish the program.");
            return;
        }

        while (true) {
            shared.setZeroMax_dif();
            shared.setZeroMax_x();
            
            iterations++; 

            if (thread_semaphore_parameter == 1) {
                semaphore_thread1_0_1.release();
                semaphore_thread1_1_0.release();
            } else {
                semaphore_thread2_0_1.release();
                semaphore_thread2_1_0.release();
            }

            // finish
            if (!shared.getThread_continues()) {
                break;
            }

            if (master_semaphore_parameter == 1) {
                master_semaphore1.acquireUninterruptibly();
            } else {
                master_semaphore2.acquireUninterruptibly();
            }

            mr = shared.getMax_dif() / shared.getMax_x();

            if (mr <= precision) {
                shared.setThread_continues();
            }
        }// end while
        
        System.out.printf("Iterations:%d, x[0]=%.3f, x[1]=%.3f \n",  iterations, x[0], x[1]);
        System.out.printf("Final iteration:%d, max_dif=%.4f max_x=%.4f  mr=%.4f  \n", iterations, shared.getMax_dif(), shared.getMax_x(), mr);

        thread_handle0.join();
        thread_handle1.join();
        thread_handle2.join();
        thread_handle3.join();

        for (int i = 0; i < matrixOrder; i++) {
            for (int j = 0; j < matrixOrder; j++) {
                System.out.printf("A[%d][%d]=%.2f   ", i, j, A[i][j]);
            }
            System.out.printf("     B[%d]=%.2f \n", i, B[i]);
        }

        int i = gerador.nextInt(10) % matrixOrder;
        sum = 0;
        for (int j = 0; j < matrixOrder; j++) {
            sum += (A[i][j] * x[j]);
        }

        System.out.printf("\nApplying x [*] in the row %d of A[ %d ][ * ], the result is: %.3f. \nB[%d] = %.3f \n", i,
               i, sum, i, B[i]);
    }// end main
}// end Master
