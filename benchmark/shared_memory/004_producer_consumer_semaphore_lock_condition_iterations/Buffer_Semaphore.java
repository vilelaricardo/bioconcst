public class Buffer_Semaphore {
    private float sharedValue = 2;
    
    // increments the shared variable
    public void setIncrementSharedValue() {
        sharedValue++;
        setDetails();
    }
    
    // multiply the shared variable
    public void setMultiplySharedValue() {
        sharedValue = sharedValue * 2;
        setDetails();
    }

    // get the shared variable
    public float getSharedValue() {
        getDetails();
        return sharedValue;
    }

    private void setDetails() {
        //System.out.println(Thread.currentThread().getName() + " writes semaphore = " + sharedValue);
        System.out.println(" writes semaphore = " + sharedValue);
    }

    private void getDetails() {
        //System.out.println(Thread.currentThread().getName() + " reads semaphore = " + sharedValue);
        System.out.println(" reads semaphore = " + sharedValue);
    }
}