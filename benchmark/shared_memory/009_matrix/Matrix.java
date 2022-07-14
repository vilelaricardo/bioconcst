/**
 * Concurrent Benchmarks
 * 
 * Title:  Matrix     
 * 
 * Description:  This benchmark implements a concurrent version
 * 		         of the matrix multiplication using N³ threads. 
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
 * java Matrix 2 0
 * 
 * OUTPUT:
 * a[0][0]=1 a[0][1]=2	    b[0][0]=1 b[0][1]=2 
 * a[1][0]=1 a[1][1]=2      b[1][0]=1 b[1][1]=2 
 * c[0][0]=3   c[0][1]=6   
 * c[1][0]=3   c[1][1]=6 
 */

import java.util.concurrent.Semaphore;

public class Matrix {
    public static void main(String[] args) throws InterruptedException {
        int i;
        int row;
        int column;
        int test = 1; // pode assumir 1 ou 0
        int matrixOrder = 2; //fixo

        
            matrixOrder = Integer.parseInt(args[0]); // fixo 2
            test = Integer.parseInt(args[1]); // 0 ou diferente de 0
        
        
        int a[][] = new int[matrixOrder][matrixOrder];
        int b[][] = new int[matrixOrder][matrixOrder];
        int c[][] = new int[matrixOrder][matrixOrder];
        
        Semaphore mutexCij_1_0 = new Semaphore(0);
        Semaphore mutexCij_1_1 = new Semaphore(0);
        Semaphore mutexCij_1_2 = new Semaphore(0);
        Semaphore mutexCij_1_3 = new Semaphore(0);
        
        Semaphore mutexCij_2_0 = new Semaphore(0);
        Semaphore mutexCij_2_1 = new Semaphore(0);
        Semaphore mutexCij_2_2 = new Semaphore(0);
        Semaphore mutexCij_2_3 = new Semaphore(0);
        
        Semaphore semaphoreSecondSlave0 = new Semaphore(0);
        Semaphore semaphoreSecondSlave1 = new Semaphore(0);
        Semaphore semaphoreSecondSlave2 = new Semaphore(0);
        Semaphore semaphoreSecondSlave3 = new Semaphore(0);

        for (row = 0; row < matrixOrder; row++) {
            for (column = 0; column < matrixOrder; column++) {
                a[row][column] = column + 1;
                b[row][column] = column + 1;
                System.out.printf("a[%d][%d]=%d ", row, column, a[row][column]);
            }
            System.out.print("      ");
            for (column = 0; column < matrixOrder; column++) {
                System.out.printf("b[%d][%d]=%d ", row, column, b[row][column]);
            }
            System.out.println();
        }

        if (test == 0) {
            mutexCij_1_0.release();
            mutexCij_1_1.release();
            mutexCij_1_2.release();
            mutexCij_1_3.release();
        } else {
            mutexCij_2_0.release();
            mutexCij_2_1.release();
            mutexCij_2_2.release();
            mutexCij_2_3.release();
        }

        FirstSlave thread_handle0 = new FirstSlave();
        FirstSlave thread_handle1 = new FirstSlave();
        FirstSlave thread_handle2 = new FirstSlave();
        FirstSlave thread_handle3 = new FirstSlave();
                
        SecondSlave secondSlave_handle0 = new SecondSlave();
        SecondSlave secondSlave_handle1 = new SecondSlave();
        SecondSlave secondSlave_handle2 = new SecondSlave();
        SecondSlave secondSlave_handle3 = new SecondSlave();
        SecondSlave secondSlave_handle4 = new SecondSlave();
        SecondSlave secondSlave_handle5 = new SecondSlave();
        SecondSlave secondSlave_handle6 = new SecondSlave();
        SecondSlave secondSlave_handle7 = new SecondSlave();

        if (test == 0) {
            thread_handle0.setFirstSlave(mutexCij_1_0, matrixOrder, a, b, c, 0, 0, 0, 0, 0, 0);
            thread_handle0.setSemaphoreInFirstSlave(semaphoreSecondSlave0);
                    
            c[0][0] = 0;                    
            secondSlave_handle0.setSemaphoreInSecondSlave(semaphoreSecondSlave0);
            secondSlave_handle0.setSecondSlave(mutexCij_1_0, matrixOrder, a, b, c, 0, 0, 0, 0, 0, 0);
            
            secondSlave_handle1.setSemaphoreInSecondSlave(semaphoreSecondSlave0);
            secondSlave_handle1.setSecondSlave(mutexCij_1_0, matrixOrder, a, b, c, 0, 1, 1, 0, 0, 0);
            		
            		
                            
            thread_handle1.setFirstSlave(mutexCij_1_1, matrixOrder, a, b, c, 0, 0, 0, 1, 0, 1);
            thread_handle1.setSemaphoreInFirstSlave(semaphoreSecondSlave1);
                    
            c[0][1] = 0;                    
            secondSlave_handle2.setSemaphoreInSecondSlave(semaphoreSecondSlave1);
            secondSlave_handle2.setSecondSlave(mutexCij_1_1, matrixOrder, a, b, c, 0, 0, 0, 1, 0, 1);
            
            secondSlave_handle3.setSemaphoreInSecondSlave(semaphoreSecondSlave1);
            secondSlave_handle3.setSecondSlave(mutexCij_1_1, matrixOrder, a, b, c, 0, 1, 1, 1, 0, 1);
            		
            		
                            
            thread_handle2.setFirstSlave(mutexCij_1_2, matrixOrder, a, b, c, 1, 0, 0, 0, 1, 0);
            thread_handle2.setSemaphoreInFirstSlave(semaphoreSecondSlave2);
            c[1][0] = 0;
                     
            secondSlave_handle4.setSemaphoreInSecondSlave(semaphoreSecondSlave2);
            secondSlave_handle4.setSecondSlave(mutexCij_1_2, matrixOrder, a, b, c, 1, 0, 0, 0, 1, 0);
            
            secondSlave_handle5.setSemaphoreInSecondSlave(semaphoreSecondSlave2);
            secondSlave_handle5.setSecondSlave(mutexCij_1_2, matrixOrder, a, b, c, 1, 1, 1, 0, 1, 0);
                     
                     
                            
            thread_handle3.setFirstSlave(mutexCij_1_3, matrixOrder, a, b, c, 1, 0, 0, 1, 1, 1);
            thread_handle3.setSemaphoreInFirstSlave(semaphoreSecondSlave3);
            c[1][1] = 0;
                     
            secondSlave_handle6.setSemaphoreInSecondSlave(semaphoreSecondSlave3);
            secondSlave_handle6.setSecondSlave(mutexCij_1_3, matrixOrder, a, b, c, 1, 0, 0, 1, 1, 1);
            
            secondSlave_handle7.setSemaphoreInSecondSlave(semaphoreSecondSlave3);
            secondSlave_handle7.setSecondSlave(mutexCij_1_3, matrixOrder, a, b, c, 1, 1, 1, 1, 1, 1);
            		
        } else {
            thread_handle0.setFirstSlave(mutexCij_2_0, matrixOrder, a, b, c, 0, 0, 0, 0, 0, 0);
            thread_handle0.setSemaphoreInFirstSlave(semaphoreSecondSlave0);
                    
            c[0][0] = 0;                    
            secondSlave_handle0.setSemaphoreInSecondSlave(semaphoreSecondSlave0);
            secondSlave_handle0.setSecondSlave(mutexCij_2_0, matrixOrder, a, b, c, 0, 0, 0, 0, 0, 0);
            
            secondSlave_handle1.setSemaphoreInSecondSlave(semaphoreSecondSlave0);
            secondSlave_handle1.setSecondSlave(mutexCij_2_0, matrixOrder, a, b, c, 0, 1, 1, 0, 0, 0);
            		
            		
                            
            thread_handle1.setFirstSlave(mutexCij_2_1, matrixOrder, a, b, c, 0, 0, 0, 1, 0, 1);
            thread_handle1.setSemaphoreInFirstSlave(semaphoreSecondSlave1);
                    
            c[0][1] = 0;                    
            secondSlave_handle2.setSemaphoreInSecondSlave(semaphoreSecondSlave1);
            secondSlave_handle2.setSecondSlave(mutexCij_2_1, matrixOrder, a, b, c, 0, 0, 0, 1, 0, 1);
            
            secondSlave_handle3.setSemaphoreInSecondSlave(semaphoreSecondSlave1);
            secondSlave_handle3.setSecondSlave(mutexCij_2_1, matrixOrder, a, b, c, 0, 1, 1, 1, 0, 1);
            		
            		
                            
            thread_handle2.setFirstSlave(mutexCij_2_2, matrixOrder, a, b, c, 1, 0, 0, 0, 1, 0);
            thread_handle2.setSemaphoreInFirstSlave(semaphoreSecondSlave2);
            c[1][0] = 0;
                     
            secondSlave_handle4.setSemaphoreInSecondSlave(semaphoreSecondSlave2);
            secondSlave_handle4.setSecondSlave(mutexCij_2_2, matrixOrder, a, b, c, 1, 0, 0, 0, 1, 0);
            
            secondSlave_handle5.setSemaphoreInSecondSlave(semaphoreSecondSlave2);
            secondSlave_handle5.setSecondSlave(mutexCij_2_2, matrixOrder, a, b, c, 1, 1, 1, 0, 1, 0);
                     
                     
                            
            thread_handle3.setFirstSlave(mutexCij_2_3, matrixOrder, a, b, c, 1, 0, 0, 1, 1, 1);
            thread_handle3.setSemaphoreInFirstSlave(semaphoreSecondSlave3);
            c[1][1] = 0;
                     
            secondSlave_handle6.setSemaphoreInSecondSlave(semaphoreSecondSlave3);
            secondSlave_handle6.setSecondSlave(mutexCij_2_3, matrixOrder, a, b, c, 1, 0, 0, 1, 1, 1);
            
            secondSlave_handle7.setSemaphoreInSecondSlave(semaphoreSecondSlave3);
            secondSlave_handle7.setSecondSlave(mutexCij_2_3, matrixOrder, a, b, c, 1, 1, 1, 1, 1, 1);
        }
        
        thread_handle0.start();
        thread_handle1.start();
        thread_handle2.start();
        thread_handle3.start();
        
        secondSlave_handle0.start();
        secondSlave_handle1.start();
        secondSlave_handle2.start();
        secondSlave_handle3.start();
        secondSlave_handle4.start();
        secondSlave_handle5.start();
        secondSlave_handle6.start();
        secondSlave_handle7.start();
        
        secondSlave_handle0.join();
        secondSlave_handle1.join();
        secondSlave_handle2.join();
        secondSlave_handle3.join();
        secondSlave_handle4.join();
        secondSlave_handle5.join();
        secondSlave_handle6.join();
        secondSlave_handle7.join();

        thread_handle0.join();
        thread_handle1.join();
        thread_handle2.join();
        thread_handle3.join();
        
        for (row = 0; row < matrixOrder; row++) {
            for (column = 0; column < matrixOrder; column++) {
                System.out.printf("c[%d][%d]=%d   ", row, column, c[row][column]);
            }
            System.out.println();
        }
    }
}
