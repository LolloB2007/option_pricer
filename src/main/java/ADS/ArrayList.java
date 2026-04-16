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
        this();
        add(init);
    }
    
    private t[] getData()
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
        
        if(index >= this.size() || index<0)
            return null;
        
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
            if(data[i].equals(o) && data[i]!=null) {
                return i;
            }
        }
        
        return -1;
    }
    
    /**
     * toString. Returns with semi-colon separator
     * @return String
     */
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
    
    /**
     * Converts ArrayList into Array
     * @return t[]
     */
    public t[] toArray()
    {
        Object[] temp = new Object[data.length-1];
        
        for(int i = 0; i<temp.length; i++) {
            temp[i] = this.get(i);
        }
        
        return (t[]) temp;
    }
    
    /**
     * Returns size of ArrayList
     * @return int
     */
    public int size() {
        return data.length-1;
    }
    
    /**
     * Method that appends ArrayList of same type at the back
     * of the originial
     * @param ext 
     */
    public void append(ArrayList<t> ext) {
        ArrayList<t> temp = new ArrayList<t>();
        
        for(int i = 0; i<size(); i++) {
            temp.add(get(i));
        }
        
        for(int i = 0; i<ext.size(); i++) {
            temp.add(ext.get(i));
        }
        
        this.data = new Object[temp.size() + 1];
        
        for(int i = 0; i<temp.size(); i++) {
            data[i] = temp.get(i);
        }
    }
    
    /**
     * Method that merges two ArrayLists. ext1 is kept at the front
     * @param ext1
     * @param ext2
     * @return 
     */
    public static ArrayList merge(ArrayList ext1, ArrayList ext2) {
        ext1.append(ext2);
        
        return ext1;
    }
    
    /**
     * Returns whether the ArrayLists are with same values, not same references
     * @param ext
     * @return boolean
     */
    public boolean equals(ArrayList<t> ext) {
        if(this.size() != ext.size())
            return false;
        
        for(int i = 0; i<this.size(); i++) {
            if(!(this.get(i).equals(ext.get(i)))) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Resets ArrayList
     */
    public void empty() {
        this.data = new Object[1];
    }
    
    /**
     * Returns an ArrayList equal to the original
     * @return ArrayList<t>
     */
    public ArrayList<t> duplicate() {
        ArrayList<t> temp = new ArrayList<t>();
        
        for(int i = 0; i<size(); i++) {
            temp.add(get(i));
        }
        
        return temp;
    }
    
    /**
     * Reverses the order of elements within the ArrayList
     */
    public void reverse() {
        ArrayList<t> temp = duplicate();
        
        for(int i = 0; i<temp.size(); i++) {
            this.data[temp.size()-1-i] = temp.get(i);
        }
    }
   
    /**
     * Replaces the object at given index with new object
     * @param index
     * @param o 
     */
    public void replace(int index, Object o) {
        if(index >= this.size() || index<0)
            return;
        
        this.data[index] = o;
    }
}
