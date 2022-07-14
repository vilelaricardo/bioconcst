import java.util.concurrent.Semaphore;
import java.math.*;

public class Slave extends Thread {
    private int I, J, p;
    int synch[];
    int thread_semaphore_parameter;
    int master_semaphore_parameter;
    float mr = 1;
    float A[][];
    float AStar[][];
    float B[];
    float x[];
    float x_temp[];
    float dif[];
    float sumRow[];
    int matrixOrder = 2;
    float value1;
    float value2;
    Semaphore row_mutex;
    Semaphore multiple_mutex;
    Semaphore semaphore_thread1_i_i;
    Semaphore semaphore_thread1_i_j;
    Semaphore semaphore_thread2_i_i;
    Semaphore semaphore_thread2_i_j;
    Semaphore master_semaphore1;
    Semaphore master_semaphore2;
    Semaphore continues_mutex;
    Semaphore synch_threads_mutex;
    SharedVariables shared;
    
    public void setSemaphore_thread1_i_i(Semaphore semaphore_thread1_i_i) {
    	this.semaphore_thread1_i_i = semaphore_thread1_i_i;
    }
    
    public void setSemaphore_thread1_i_j(Semaphore semaphore_thread1_i_j) {
    	this.semaphore_thread1_i_j = semaphore_thread1_i_j;
    }
    
    public void setSemaphore_thread2_i_i(Semaphore semaphore_thread2_i_i) {
    	this.semaphore_thread2_i_i = semaphore_thread2_i_i;
    }
    
    public void setSemaphore_thread2_i_j(Semaphore semaphore_thread2_i_j) {
    	this.semaphore_thread2_i_j = semaphore_thread2_i_j;
    }
    
    public void setSemaphore_row_mutex(Semaphore row_mutex) {
        this.row_mutex = row_mutex;
    }
    
    public void setSemaphore_multiple_mutex(Semaphore multiple_mutex) {
        this.multiple_mutex = multiple_mutex;
    }
    
    public void setSemaphore_continues_mutex(Semaphore continues_mutex) {
        this.continues_mutex = continues_mutex;
    }
    
    public void setSemaphore_synch_threads_mutex(Semaphore synch_threads_mutex) {
        this.synch_threads_mutex = synch_threads_mutex;
    }
    
    public void setMaster_semaphore1(Semaphore master_semaphore1) {
        this.master_semaphore1 = master_semaphore1;
    }
    
    public void setMaster_semaphore2(Semaphore master_semaphore2) {
        this.master_semaphore2 = master_semaphore2;
    }
    
    public void setSharedVariables(SharedVariables shared) {
        this.shared = shared;
    }
    
    public void setSlave(int p, int I, int J, float A[][], float B[], float AStar[][],
            float x[], float x_temp[], float sumRow[], int synch[], float dif[], 
                int thread_semaphore_parameter, int master_semaphore_parameter ) {
        this.p = p;
        this.I = I;
        this.J = J;
        this.A = A;
        this.B = B;
        this.AStar = AStar;
        this.x = x;
        this.x_temp = x_temp;
        this.sumRow = sumRow;
        this.synch = synch;
        this.dif = dif;
        this.thread_semaphore_parameter = thread_semaphore_parameter;
        this.master_semaphore_parameter = master_semaphore_parameter;
    }

    public void run() {
        if (I != J) {
            AStar[I][J] = A[I][J] / A[I][I];
            row_mutex.acquireUninterruptibly();
            sumRow[I] += (float) Math.abs((float) AStar[I][J]);
            synch[I]++;
            if (synch[I] == (matrixOrder - 1)) {
                if (thread_semaphore_parameter == 1) {
                    semaphore_thread1_i_i.release();
                } else {
                    semaphore_thread2_i_i.release();
                }
                synch[I] = 0;
            }
            row_mutex.release();

            while (true) {
                if (thread_semaphore_parameter == 1) {
                    semaphore_thread1_i_j.acquireUninterruptibly();
                } else {
                    semaphore_thread2_i_j.acquireUninterruptibly();
                }

                if (!shared.getThread_continues()) {
                    if (thread_semaphore_parameter == 1) {
                        semaphore_thread1_i_i.release();
                    } else {
                        semaphore_thread2_i_i.release();
                    }
                    return;
                }

                float mult = AStar[I][J] * x[J];

                multiple_mutex.acquireUninterruptibly();
                sumRow[I] = sumRow[I] + mult;
                multiple_mutex.release();

                row_mutex.acquireUninterruptibly();
                synch[I]++;
                if (synch[I] == (matrixOrder - 1)) {
                    if (thread_semaphore_parameter == 1) {
                        semaphore_thread1_i_i.release();
                    } else {
                        semaphore_thread2_i_i.release();
                    }
                    synch[I] = 0;
                }
                row_mutex.release();

            } // end while
        } else {
            x_temp[I] = x[I] = AStar[I][I] = B[I] / A[I][I];

            if (thread_semaphore_parameter == 1) {
                semaphore_thread1_i_i.acquireUninterruptibly();
            } else {
                semaphore_thread2_i_i.acquireUninterruptibly();
            }

            if (!shared.getThread_continues()) {
                // finalizing diagonal thread
                return;
            }

            continues_mutex.acquireUninterruptibly();

	      int a = (int) sumRow[I];	
            //if (sumRow[I] >= 1) {
		if(a>=1){
                shared.setThread_continues(); // the system does not converge
            }
            sumRow[I] = 0;
            continues_mutex.release();

            if (master_semaphore_parameter == 1) {
                master_semaphore1.release();
            } else {
                master_semaphore2.release();
            }

            while (true) {
                if (thread_semaphore_parameter == 1) {
                    semaphore_thread1_i_i.acquireUninterruptibly();
                } else {
                    semaphore_thread2_i_i.acquireUninterruptibly();
                }

                if (!shared.getThread_continues()) {
                    // finalizing diagonal thread
                    break;
                }


                x_temp[I] = AStar[I][I] - sumRow[I];
                dif[I] = Math.abs((x_temp[I] - x[I]));


                continues_mutex.acquireUninterruptibly();

	int j = (int) dif[I] * 1000000; //Ajuste pois a ferramenta de teste não consegue armazenar ponto flutuante em comparações
	int g = (int) shared.getMax_dif() * 1000000; //Ajuste pois a ferramenta de teste não consegue armazenar ponto flutuante em comparações
		
                //if (dif[I] > shared.getMax_dif()) {
		if(j>0){
                    value1 = dif[I];
                    shared.setMax_dif(value1);
                }
			
		
	int b = (int) Math.abs(x_temp[I]) * 1000000; //Ajuste pois a ferramenta de teste não consegue armazenar ponto flutuante em comparações
	int c = (int) shared.getMax_x() * 1000000; //Ajuste pois a ferramenta de teste não consegue armazenar ponto flutuante em comparações
              //  if (Math.abs(x_temp[I]) > shared.getMax_x()) {
	   	  if (b>c){
                    value2 = Math.abs(x_temp[I]);
                    shared.setMax_x(value2);
                }

                sumRow[I] = 0;
                x[I] = x_temp[I];
                continues_mutex.release();

                synch_threads_mutex.acquireUninterruptibly();
                shared.incrementSynch_threads();
                if (shared.getSynch_threads() == matrixOrder) {
                    if (master_semaphore_parameter == 1) {
                        master_semaphore1.release();
                    } else {
                        master_semaphore2.release();
                    }
                    shared.zeroSynch_threads(); 
                }
                synch_threads_mutex.release();
            }
            return;
        }
    }
}
