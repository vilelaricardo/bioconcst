public class Buffer {
    private int variable;

    public void setSharedObject(int shared) {
        variable = shared;
    }

    public int getSharedObject() {
        return variable;
    }

    public void setSharedIntIncrement() {
        variable++;
    }

    public void setSharedIntMultiplie() {
        variable = variable * 2;
    }
}
