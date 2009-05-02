package persistentmatrix.core;

import clojure.lang.Sequential;
import clojure.lang.IPersistentCollection;
import clojure.lang.Reversible;
import clojure.lang.Counted;
import clojure.lang.IPersistentMap;


//Not implementing Associative or IPersistentStack yet, must add IPersistentCollection manually because it comes with IPersistentStack (remove later)
public interface IPersistentMatrix extends Sequential, IPersistentCollection, Reversible, Counted{
    int length();

    Object nth(int i);

    IPersistentMatrix cons(Object o);

    int getSpace();

    int getLastSDimInDeep();

    int getSubspace();

    int[] getSubspaceShape();

    int[] getShape();

    int[] getSubspaceMap();

    IPersistentMap getALabels();

    IPersistentMap[] getELabels();
    
    int[][] getIndexMap();

    int[] getIntegerArray();

    boolean[] getBooleanArray();

    double[] getDoubleArray();

    Object[] getObjectArray();

    String toStringRecurse(String stringRep);

    Object index(int[] dCoordinates);

    Class getBoxedType();

    IPersistentMatrix slice(int dim, int startingElement, int endingElement);


}
