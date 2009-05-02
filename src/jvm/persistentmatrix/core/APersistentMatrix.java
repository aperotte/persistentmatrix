/**
 *   Copyright (c) Adler Perotte. All rights reserved.
 *   The use and distribution terms for this software are covered by the
 *   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *   which can be found in the file epl-v10.html at the root of this distribution.
 *   By using this software in any fashion, you are agreeing to be bound by
 * 	 the terms of this license.
 *   You must not remove this notice, or any other, from this software.
 *
 *   May 2, 2009
 **/

package persistentmatrix.core;

//import java.util.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicInteger; 

import clojure.lang.AFn;
import clojure.lang.ASeq;
import clojure.lang.Counted;
import clojure.lang.IFn;
import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IReduce;
import clojure.lang.ISeq;
import clojure.lang.IndexedSeq;
import clojure.lang.PersistentHashMap;
import clojure.lang.RT;
import clojure.lang.Sequential;
import clojure.lang.Stream;
import clojure.lang.Streamable;
import clojure.lang.Util;
import persistentmatrix.core.IPersistentMatrix;



public abstract class APersistentMatrix extends AFn implements IPersistentMatrix, Iterable,
							       List, RandomAccess,
							       Streamable, Comparable {


    int _hash = -1;

    public APersistentMatrix(IPersistentMap meta){
	super(meta);
    }

    protected APersistentMatrix(){}


    public String toStringRecurse(String stringRep){

	if(getSubspace() != 1){
	    for(int i=0;i<getSubspaceShape()[getSubspace()-1];i++){
		stringRep += "[";
		stringRep = slice(getSubspace()-1, i, i+1).toStringRecurse(stringRep);
		if(i==getSubspaceShape()[getSubspace()-1]-1){
		    stringRep += "]";
		}
		else{
		    stringRep +="]\n";
		}
	    }
	    return stringRep;
	}
	else{
	    for(int i=0; i<getShape()[0]; i++){
		if(i!=getShape()[0]-1){
		    stringRep += nth(i).toString();
		    stringRep += ", ";
		}
		else{
		    stringRep += nth(i).toString();
		}
	    }
	    return stringRep;
	}
    }	


    public String toString(){
	String stringRep = new String("\n[");
	return toStringRecurse(stringRep) + "]";
    }

    public ISeq seq(){
	if(count() > 0)
	    return new Seq(this, 0);
	return null;
    }

    public ISeq rseq(){
	if(count() > 0)
	    return new RSeq(this, count() - 1);
	return null;
    }

    static boolean doEquals(IPersistentMatrix mat, Object obj){
	if(obj instanceof List || obj instanceof IPersistentMatrix)
	    {
		Collection ma = (Collection) obj;
		if(ma.size() != mat.count() || ma.hashCode() != mat.hashCode())
		    return false;
		for(Iterator i1 = ((List) mat).iterator(), i2 = ma.iterator();
		    i1.hasNext();)
		    {
			if(!Util.equals(i1.next(), i2.next()))
			    return false;
		    }
		return true;
	    }
	else
	    {
		if(!(obj instanceof Sequential))

		    return false;
		ISeq ms = ((IPersistentCollection) obj).seq();
		for(int i = 0; i < mat.count(); i++, ms = ms.next())
		    {
			if(ms == null || !Util.equals(mat.nth(i), ms.first()))
			    return false;
		    }
		if(ms != null)
		    return false;
	    }

	return true;

    }

    static boolean doEquiv(IPersistentMatrix mat, Object obj){
	if(obj instanceof List || obj instanceof IPersistentMatrix)
	    {
		Collection ma = (Collection) obj;
		if(ma.size() != mat.count())
		    return false;
		for(Iterator i1 = ((List) mat).iterator(), i2 = ma.iterator();
		    i1.hasNext();)
		    {
			if(!Util.equiv(i1.next(), i2.next()))
			    return false;
		    }
		return true;
	    }
	else
	    {
		if(!(obj instanceof Sequential))
		    return false;
		ISeq ms = ((IPersistentCollection) obj).seq();
		for(int i = 0; i < mat.count(); i++, ms = ms.next())
		    {
			if(ms == null || !Util.equiv(mat.nth(i), ms.first()))
			    return false;
		    }
		if(ms != null)
		    return false;
	    }

	return true;

    }

    public boolean equals(Object obj){
	return doEquals(this, obj);
    }

    public boolean equiv(Object obj){
	return doEquiv(this, obj);
    }

    public int hashCode(){
	if(_hash == -1)
	    {
		int hash = 0;
		hash += getIndexMap().hashCode();
		if(getIntegerArray() != null){
		    hash += getIntegerArray().hashCode();
		}
		if(getBooleanArray() != null){
		    hash += getBooleanArray().hashCode();
		}
		if(getDoubleArray() != null){
		    hash += getDoubleArray().hashCode();
		}
		else{
		    hash += getObjectArray().hashCode();
		}
		this._hash = hash;
	    }
	return _hash;
    }

    public Object get(int index){
	return nth(index);
    }

    public Object remove(int i){
	throw new UnsupportedOperationException();
    }

    public int indexOf(Object o){
	for(int i = 0; i < count(); i++)
	    if(Util.equiv(nth(i), o))
		return i;
	return -1;
    }

    public int lastIndexOf(Object o){
	for(int i = count() - 1; i >= 0; i--)
	    if(Util.equiv(nth(i), o))
		return i;
	return -1;
    }

    public ListIterator listIterator(){
	return listIterator(0);
    }

    public ListIterator listIterator(final int index){
	return new ListIterator(){
	    int nexti = index;

	    public boolean hasNext(){
		return nexti < count();
	    }

	    public Object next(){
		return nth(nexti++);
	    }

	    public boolean hasPrevious(){
		return nexti > 0;
	    }

	    public Object previous(){
		return nth(--nexti);
	    }

	    public int nextIndex(){
		return nexti;
	    }

	    public int previousIndex(){
		return nexti - 1;
	    }

	    public void remove(){
		throw new UnsupportedOperationException();
	    }

	    public void set(Object o){
		throw new UnsupportedOperationException();
	    }

	    public void add(Object o){
		throw new UnsupportedOperationException();
	    }
	};
    }


    public List subList(int fromIndex, int toIndex){
	return (List) slice(getLastSDimInDeep(), fromIndex, toIndex);
    }


    public Object set(int i, Object o){
	throw new UnsupportedOperationException();
    }

    public void add(int i, Object o){
	throw new UnsupportedOperationException();
    }

    public boolean addAll(int i, Collection c){
	throw new UnsupportedOperationException();
    }


    public List[] sliceMapFmArgs(List[] args, boolean deep){
	List[] sliceMap = new List[getSpace()];
	if(deep){
	    for(int i=0; i<args.length; i++){
		int j = 0;
		List dimslice = new ArrayList(); 
		if(args[i] instanceof ISeq){
		    for(ISeq s = ((ISeq)args[i]).seq(); s != null && (Integer)s.first()<=getShape()[i]; j++, s = s.next()){
			dimslice.add(j, (Integer)s.first());
		    }
		    sliceMap[i] = dimslice;
		}
		else{
		    for(int s = 0; s<args[i].size(); s++){
			dimslice.add(s, args[i].get(s));
		    }
		    sliceMap[i] = dimslice;
		}
	    }
	    return sliceMap;
	}
	else{
	    for(int i=0; i<args.length; i++){
		int j = 0;
		int[] subspaceMap = getSubspaceMap();
		for(int k =0; k<sliceMap.length; k++){
		    sliceMap[k] = null;
		} 

		List dimslice = new ArrayList(); 
		if(args[i] instanceof ISeq){
		    for(ISeq s = ((ISeq)args[i]).seq(); s != null && (Integer)s.first()<=getShape()[i]; j++, s = s.next()){
			dimslice.add(j, (Integer)s.first());
		    }
		    sliceMap[subspaceMap[i]] = dimslice;
		}
		else{
		    for(int s = 0; s<args[i].size(); s++){
			dimslice.add(s, args[i].get(s));
		    }
		    sliceMap[subspaceMap[i]] = dimslice;
		}
	    }
	    return sliceMap;
	}
    }

    public List[] sliceMapFmMap(Map map){
	List[] sliceMap = new ArrayList[getSpace()];
	for(int i=0; i<sliceMap.length; i++){
	    sliceMap[i] = null;
	}
	Iterator mapIterator = map.entrySet().iterator();
	if(mapIterator.hasNext()){
	    Map.Entry entries = (Map.Entry)mapIterator.next();
	    for(int i=0; mapIterator.hasNext(); entries = (Map.Entry)mapIterator.next(), i++){
		int j = 0;
		List dimslice = new ArrayList(); 
		if(entries.getValue() instanceof ISeq){
		    for(ISeq s = ((ISeq)entries.getValue()).seq(); s != null && eLabelToIndex(aLabelToIndex(entries.getKey()), s.first())<=getShape()[aLabelToIndex(entries.getKey())]; j++, s = s.next()){
			dimslice.add(j, eLabelToIndex(aLabelToIndex(entries.getKey()), s.first()));
		    }
		    if(sliceMap[aLabelToIndex(entries.getKey())] == null){
			sliceMap[aLabelToIndex(entries.getKey())] = dimslice;
		    }
		    else{
			sliceMap[aLabelToIndex(entries.getKey())] = aListIntersection((ArrayList)sliceMap[aLabelToIndex(entries.getKey())], (ArrayList)dimslice);
		    }
		}
		else{
		    for(int s = 0; s<((List)entries.getValue()).size(); s++){
			dimslice.add(s, eLabelToIndex(aLabelToIndex(entries.getKey()), ((List)entries.getValue()).get(s)));
		    }
		    if(sliceMap[aLabelToIndex(entries.getKey())] == null){
			sliceMap[aLabelToIndex(entries.getKey())] = dimslice;
		    }
		    else{
			sliceMap[aLabelToIndex(entries.getKey())] = aListIntersection((ArrayList)sliceMap[aLabelToIndex(entries.getKey())], (ArrayList)dimslice);
		    }
		}
	    }
	}
	return sliceMap;
    }

    public ArrayList aListIntersection(ArrayList list1, ArrayList list2){
	Iterator iterator = list1.iterator();
	ArrayList newlist = new ArrayList();
	Object o;
	while( iterator.hasNext()){
	    o = iterator.next();
	    if(list2.contains(o)){
		newlist.add(o);
	    }
	}
	if(newlist.size() > 0){
	    return newlist;
	}
	else{
	    throw new UnsupportedOperationException();
	}
    }


    public Object indexToALabel(Integer index){
	Iterator iterator = ((PersistentHashMap)getALabels()).iterator();
	IMapEntry entry = null;
	if(iterator.hasNext()){
	    entry = (IMapEntry)iterator.next();
	}
	for(; iterator.hasNext();entry = (IMapEntry)iterator.next()){
	    if(entry.val() == index){
		return entry.key();
	    }
	}
	throw new UnsupportedOperationException();
    }

    public Integer aLabelToIndex(Object label){
	if(Util.isInteger(label)){
	    return (Integer)label;
	}
	else{
	    return (Integer)((PersistentHashMap)getALabels()).valAt(label);
	}
    }

    public Integer eLabelToIndex(Integer axis, Object label){
	if(Util.isInteger(label)){
	    return (Integer)label;
	}
	else{
	    return (Integer)getELabels()[axis].valAt(label);
	}
    }

    public IPersistentMatrix applySliceMap(List[] sliceMap, int[] squeezeMap){
	return null; // To be continued ...
    }
	

    public Object invoke(Object arg1) throws Exception{ // only valid number of args is = space or >= subspace
	boolean deep = true;
	if(getSubspace() == 1){
	    deep = false;
	}
	int[] squeezeMap = new int[getSubspace()];
	for(int i=0; i<squeezeMap.length; i++){
	    squeezeMap[i] = 1;
	}
	int[] args = new int[1];
	if(Util.isInteger(arg1)){
	    squeezeMap[0] = 0;
	}
	if(arg1 instanceof List){ // in other methods if first is a List ensure that all others are lists too
	    return applySliceMap(sliceMapFmArgs(new List[] {(List)arg1}, deep), squeezeMap);
	}
	else if(arg1 instanceof Map){
	    return applySliceMap(sliceMapFmMap((Map)arg1), squeezeMap);
	}
	else{
	    throw new UnsupportedOperationException();
	}

    }


//     public Object invoke() throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7)
// 	throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11, Object arg12) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13)
// 	throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14)
// 	throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
// 			 Object arg15) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
// 			 Object arg15, Object arg16) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
// 			 Object arg15, Object arg16, Object arg17) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
// 			 Object arg15, Object arg16, Object arg17, Object arg18) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
// 			 Object arg15, Object arg16, Object arg17, Object arg18, Object arg19) throws Exception{
// 	return throwArity();
//     }

//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
// 			 Object arg15, Object arg16, Object arg17, Object arg18, Object arg19, Object arg20)
// 	throws Exception{
// 	return throwArity();
//     }


//     public Object invoke(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7,
// 			 Object arg8, Object arg9, Object arg10, Object arg11, Object arg12, Object arg13, Object arg14,
// 			 Object arg15, Object arg16, Object arg17, Object arg18, Object arg19, Object arg20,
// 			 Object... args)
// 	throws Exception{
// 	return throwArity();

//     }



    public Iterator iterator(){
	//todo - something more efficient
	return new Iterator(){
	    int i = 0;

	    public boolean hasNext(){
		return i < count();
	    }

	    public Object next(){
		return nth(i++);
	    }

	    public void remove(){
		throw new UnsupportedOperationException();
	    }
	};
    }

//     public Object peek(){
// 	if(count() > 0)
// 	    return nth(count() - 1);
// 	return null;
//     }

//     public boolean containsKey(Object key){
// 	if(!(Util.isInteger(key)))
// 	    return false;
// 	int i = ((Number) key).intValue();
// 	return i >= 0 && i < count();
//     }

//     public IMapEntry entryAt(Object key){
// 	if(Util.isInteger(key))
// 	    {
// 		int i = ((Number) key).intValue();
// 		if(i >= 0 && i < count())
// 		    return new MapEntry(key, nth(i));
// 	    }
// 	return null;
//     }

//     public IPersistentMatrix assoc(Object key, Object val){
// 	if(Util.isInteger(key))
// 	    {
// 		int i = ((Number) key).intValue();
// 		return assocN(i, val);
// 	    }
// 	throw new IllegalArgumentException("Key must be integer");
//     }

//     public Object valAt(Object key, Object notFound){
// 	if(Util.isInteger(key))
// 	    {
// 		int i = ((Number) key).intValue();
// 		if(i >= 0 && i < count())
// 		    return nth(i);
// 	    }
// 	return notFound;
//     }

//     public Object valAt(Object key){
// 	return valAt(key, null);
//     }

    // java.util.Collection implementation

    public Object[] toArray(){
	return RT.seqToArray(seq());
    }

    public boolean add(Object o){
	throw new UnsupportedOperationException();
    }

    public boolean remove(Object o){
	throw new UnsupportedOperationException();
    }

    public boolean addAll(Collection c){
	throw new UnsupportedOperationException();
    }

    public void clear(){
	throw new UnsupportedOperationException();
    }

    public boolean retainAll(Collection c){
	throw new UnsupportedOperationException();
    }

    public boolean removeAll(Collection c){
	throw new UnsupportedOperationException();
    }

    public boolean containsAll(Collection c){
	for(Object o : c)
	    {
		if(!contains(o))
		    return false;
	    }
	return true;
    }
    // TODO toArray should probably return a nested array - figure out how to do this
    public Object[] toArray(Object[] a){
	if(a.length >= count())
	    {
		ISeq s = seq();
		for(int i = 0; s != null; ++i, s = s.next())
		    {
			a[i] = s.first();
		    }
		if(a.length > count())
		    a[count()] = null;
		return a;
	    }
	else
	    return toArray();
    }

    public int size(){
	return count();
    }

    public boolean isEmpty(){
	return count() == 0;
    }

    public boolean contains(Object o){
	for(ISeq s = seq(); s != null; s = s.next())
	    {
		if(Util.equiv(s.first(), o))
		    return true;
	    }
	return false;
    }

    public int length(){
	return count();
    }

    public int compareTo(Object o){
	IPersistentMatrix mat = (IPersistentMatrix) o;
	if(count() < mat.count())
	    return -1;
	else if(count() > mat.count())
	    return 1;
	for(int i = 0; i < count(); i++)
	    {
		int c = Util.compare(nth(i),mat.nth(i));
		if(c != 0)
		    return c;
	    }
	return 0;
    }

    public Stream stream() throws Exception {
	return new Stream(new Src(this));
    }

    static class Src extends AFn{
        final IPersistentMatrix m;
        int i = 0;

        Src(IPersistentMatrix m) {
            this.m = m;
        }

        public Object invoke() throws Exception {
            if (i < m.count())
                return m.nth(i++);
            return RT.EOS;
        }
    }


    static class Seq extends ASeq implements IndexedSeq, IReduce{
	//todo - something more efficient
	final IPersistentMatrix mat;
	final int i;


	public Seq(IPersistentMatrix mat, int i){
	    this.mat = mat;
	    this.i = i;
	}

	Seq(IPersistentMap meta, IPersistentMatrix mat, int i){
	    super(meta);
	    this.mat = mat;
	    this.i = i;
	}

	public Object first(){
	    return mat.nth(i);
	}

	public ISeq next(){
	    if(i + 1 < mat.count())
		return new APersistentMatrix.Seq(mat, i + 1);
	    return null;
	}

	public int index(){
	    return i;
	}

	public int count(){
	    return mat.count() - i;
	}

	public APersistentMatrix.Seq withMeta(IPersistentMap meta){
	    return new APersistentMatrix.Seq(meta, mat, i);
	}

	public Object reduce(IFn f) throws Exception{
	    Object ret = mat.nth(i);
	    for(int x = i + 1; x < mat.count(); x++)
		ret = f.invoke(ret, mat.nth(x));
	    return ret;
	}

	public Object reduce(IFn f, Object start) throws Exception{
	    Object ret = f.invoke(start, mat.nth(i));
	    for(int x = i + 1; x < mat.count(); x++)
		ret = f.invoke(ret, mat.nth(x));
	    return ret;
	}
    }

    static class RSeq extends ASeq implements IndexedSeq, Counted{
	final IPersistentMatrix mat;
	final int i;

	RSeq(IPersistentMatrix mat, int i){
	    this.mat = mat;
	    this.i = i;
	}

	RSeq(IPersistentMap meta, IPersistentMatrix mat, int i){
	    super(meta);
	    this.mat = mat;
	    this.i = i;
	}

	public Object first(){
	    return mat.nth(i);
	}

	public ISeq next(){
	    if(i > 0)
		return new APersistentMatrix.RSeq(mat, i - 1);
	    return null;
	}

	public int index(){
	    return i;
	}

	public int count(){
	    return i + 1;
	}

	public APersistentMatrix.RSeq withMeta(IPersistentMap meta){
	    return new APersistentMatrix.RSeq(meta, mat, i);
	}
    }


}
