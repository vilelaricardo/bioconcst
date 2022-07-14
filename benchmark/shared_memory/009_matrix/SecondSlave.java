import java.util.concurrent.Semaphore;

public class SecondSlave extends Thread {
    Semaphore mutexCij;
    Semaphore semaphoreSecondSlave;
    int matrixOrder;
    int a[][];
    int b[][];
    int c[][];
    int row;
    int column;
    int a_row, b_row;
    int a_column, b_column;
    
    public void setSemaphoreInSecondSlave(Semaphore semaphoreSecondSlave) {
        this.semaphoreSecondSlave = semaphoreSecondSlave;	
    }

    public void setSecondSlave(Semaphore mutexCij, int N, int a[][], int b[][], int c[][], int a_row, int a_column, int b_row,
            int b_column, int row, int column) {
        this.mutexCij = mutexCij;
        this.matrixOrder = N;
        this.a = a;
        this.b = b;
        this.c = c;
        this.a_row = a_row;
        this.a_column = a_column;
        this.b_row = b_row;
        this.b_column = b_column;
        this.row = row;
        this.column = column;
    }

    public void run() {
        semaphoreSecondSlave.acquireUninterruptibly();
        int mult;
        mult = a[a_row][a_column] * b[b_row][b_column];
        mutexCij.acquireUninterruptibly();
        c[row][column] += mult;
        mutexCij.release();
    }
}