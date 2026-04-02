package ADS;

/**
 * Implementation of Java Util ArrayList class
 * @author lorenzobarbagelata
 */
public class ArrayList<t> {
    
    Object[] data;
    
    /**
     * Initialises ArrayList object. Does not require param.
     * Use for non-type specific 
     */
    public ArrayList() {
        this.data = new Object[1];
    }
    
    /**
     * Initialises ArrayList object.
     * Contains first element.
     * Don't use for general purpose ArrayList
     */
    public ArrayList(t init) {
        new ArrayList();
        add(init);
    }
    
    public t[] getData()
    {
        return (t[]) data;
    }
    
    /**
     * Increases size of this.data
     * and copies old data
     */
    private void increaseSize() {
        Object[] copy = new Object[this.data.length];
        
        for(int i = 0; i<copy.length; i++) {
            copy[i] = this.data[i];
        }
        
        this.data = new Object[this.data.length + 1];
        
        for(int i = 0; i<copy.length; i++) {
            data[i] = copy[i];
        }
    }
    
    /**
     * Adds o to ArrayList
     * @param o 
     */
    public void add(t o)
    {
        this.increaseSize();
        
        this.data[data.length-2] = o;
    }

    /**
     * Removes object at given index and shifts everything
     * down one index
     * @param index 
     */
    public void remove(int index)
    {
        this.data[index] = null;
        
        decreaseSize();
    }
    
    /**
     * Decreases size of array deleting all null indices
     */
    private void decreaseSize()
    {
        ArrayList<t> temp = new ArrayList<t>();
        
        for(int i = 0; i<data.length-1; i++) {
            if(data[i] != null) {
                temp.add((t) data[i]);
            }
        }
        
        t[] tempData = temp.getData();
        
        Object[] onlyTempData = new Object[tempData.length-1];
        
        for(int i = 0; i<tempData.length-1; i++) {
            onlyTempData[i] = tempData[i];
        }
        
        this.data = onlyTempData;
    }
    
    /**
     * Returns element at given index
     * @param index
     * @return t
     */
    public t get(int index) {
        return (t) data[index];
    }
    
    /**
     * Returns index of object o.
     * Returns -1 if not present in ArrayList
     * @param o
     * @return int
     */
    public int find(Object o) {
        for(int i = 0; i<data.length-1; i++) {
            if(data[i].equals(o)) {
                return i;
            }
        }
        
        return -1;
    }
    
    public String toString()
    {
        String str = "";
        
        for(int i = 0; i<data.length-1; i++) {
            str += data[i].toString();
            
            if(i != data.length-2) {
                str += "; ";
            }
        }
        
        return str;
    }
    
}
