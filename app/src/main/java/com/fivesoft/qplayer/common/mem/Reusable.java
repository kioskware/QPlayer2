package com.fivesoft.qplayer.common.mem;

public interface Reusable {

    /**
     * Size of the object. Used to select proper object, while looking for "second-hand" objects.
     * @return size of the object.
     */

    int size();

    /**
     * Recycles the object. After calling this method, the object should be ready to be used again.
     */

    void recycle();

}
