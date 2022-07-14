public class SharedVariables {
    private int synch_threads = 0;
    private boolean thread_continues = true;
    private float max_x =  0.0f;
    private float max_dif =  0.0f;
    
    public int getSynch_threads() {
        return synch_threads;
    }
 
    public void incrementSynch_threads() {
        synch_threads++;
    }
    
    public void zeroSynch_threads() {
        synch_threads = 0;
    }
 
    public boolean getThread_continues() {
        return thread_continues;
    }
 
    public void setThread_continues() {
        thread_continues = false;
    }
    
    public float getMax_x() {
        return max_x;
    }
 
    public void setMax_x(float value) {
        max_x = value;
    }
    
    public void setZeroMax_x() {
        max_x = 0.0f;
    }
    
    public float getMax_dif() {
        return max_dif;
    }
 
    public void setMax_dif(float value) {
        max_dif = value;
    }
    
    public void setZeroMax_dif() {
        max_dif = 0.0f;
    }
}