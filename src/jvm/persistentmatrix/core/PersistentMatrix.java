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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.PersistentHashMap;
import clojure.lang.Util;
import persistentmatrix.core.APersistentMatrix;



public class PersistentMatrix extends APersistentMatrix{

    boolean[] booleanArray; 
    double[] doubleArray;
    int[] integerArray;
    Object[] objectArray;

    Class boxedType;

    int unitCnt;
    int totalCnt;
    int[] unitShape;
    int unitSpace;
    int space;
    int[] shape;
    int subspace;
    int[] subspaceShape;
    int[][] indexMap;
    IPersistentMap ALabels;
    IPersistentMap[] ELabels;
    int[] subspaceMap;
    boolean primitive;
    boolean view;
    int[] totalShape;


    static public PersistentMatrix create(int[] unitShape, Object nestedStructure, boolean primitive) {

	return new PersistentMatrix(unitShape, nestedStructure, primitive);

    }


    private boolean isBoxedPrimitive(Object o){

	if(o instanceof Boolean ||
	   //	   o instanceof Character ||
	   //	   o instanceof Byte ||
	   //	   o instanceof Short ||
	   o instanceof Integer ||
	   //	   o instanceof Long ||
	   //	   o instanceof Float ||
	   o instanceof Double) {
	    return true;
	}
	else{
	    return false;
	}
    }

    private void reverseArray(int[] b) {
	int right = b.length-1;
	int left  = 0;          
	
	while (left < right) {
	    int temp = b[left]; 
	    b[left]  = b[right]; 
	    b[right] = temp;
	    
	    right--;
	    left++;

	}
    }


    private void discoverListRecurse(List nestedStructure, int[] shape){
	int[] newShape = new int[shape.length+1];
	for(int i=0;i<shape.length;i++){
	    newShape[i] = shape[i];
	}
	newShape[shape.length] = nestedStructure.size();
	if(nestedStructure.get(0) instanceof List){
	    List newNestedStructure = (List)nestedStructure.get(0);
	    if(newNestedStructure.size() != 0){
		discoverListRecurse(newNestedStructure, newShape);
	    }
	    else{
		throw new UnsupportedOperationException();
	    }
	}
	else{
	    if(primitive){
		if(isBoxedPrimitive(nestedStructure.get(0))){
		    this.boxedType = nestedStructure.get(0).getClass();
		}
		else{
		    throw new UnsupportedOperationException();
		}
	    }
	    else{
		boxedType = Object.class;
	    }
	    reverseArray(newShape);
	    this.totalShape = newShape;
	}
    }

    private void discoverISeqRecurse(ISeq nestedStructure, int[] shape){
	int[] newShape = new int[shape.length+1];
	for(int i=0;i<shape.length;i++){
	    newShape[i] = shape[i];
	}
	newShape[newShape.length-1] = nestedStructure.count();
	if(nestedStructure.first() instanceof ISeq){
	    ISeq newNestedStructure = (ISeq)nestedStructure.first();
	    if(newNestedStructure.count() != 0){
		discoverISeqRecurse(newNestedStructure, newShape);
	    }
	    else{
		throw new UnsupportedOperationException();
	    }
	}
	else{
	    if(primitive){
		if(isBoxedPrimitive(nestedStructure.first())){
		    this.boxedType = nestedStructure.first().getClass();
		}
		else{
		    throw new UnsupportedOperationException();
		}
	    }
	    else{
		boxedType = Object.class;
	    }
	    reverseArray(newShape);
	    this.totalShape = newShape;
	}

    }

    private void discoverArrayRecurse(Object nestedStructure, int[] shape){
	int[] newShape = new int[shape.length+1];
	for(int i=0;i<shape.length;i++){
	    newShape[i] = shape[i];
	}
	newShape[shape.length] = Array.getLength(nestedStructure);
	if(Array.get(nestedStructure, 0).getClass().isArray()){
	    if(Array.getLength(Array.get(nestedStructure, 0)) != 0){
		discoverArrayRecurse(Array.get(nestedStructure, 0), newShape);
	    }
	    else{
		throw new UnsupportedOperationException();
	    }
	}
	else{
	    if(primitive){
		if(isBoxedPrimitive(Array.get(nestedStructure, 0))){
		    this.boxedType = Array.get(nestedStructure, 0).getClass();
		}
		else{
		    throw new UnsupportedOperationException();
		}
	    }
	    else{
		boxedType = Object.class;
	    }
	    reverseArray(newShape);
	    this.totalShape = newShape;
	}
    }

    private void discoverStructure(Object nestedStructure){
	if(nestedStructure instanceof ISeq) {
	    discoverISeqRecurse((ISeq)nestedStructure, new int[] {});
	}
	else if(nestedStructure instanceof List) {
	    discoverListRecurse((List)nestedStructure, new int[] {});
	}
	else if(nestedStructure.getClass().isArray()) {
	    discoverArrayRecurse(nestedStructure, new int[] {});
	}
	else{
	    throw new UnsupportedOperationException();
	}

    }

    public void flattenListRecurse(int[] strides, List nestedStructure, int depth, int[] coordinates){
	if (depth == 0){

	    int receiverOffset = 0;
	    //	    int copierOffset = 0;
	    for(int i = 1;i<coordinates.length;i++){
		receiverOffset += strides[i]*coordinates[i];

	    }

	    if(this.boxedType==Boolean.class){ // This will all be more efficient if these if else statements are brought outside of the recursive loop in the form of different recursive functions for each type
		if(this.booleanArray == null){
		    booleanArray = new boolean[this.totalCnt]; // Non-local assumption that totalCnt has been set before you get here in the construction process
		}
		for(int i =0;i<this.totalShape[0];i++){
		    this.booleanArray[receiverOffset+i] = (Boolean)nestedStructure.get(i);
		}
	    }
	    else if(this.boxedType==Double.class){
		if(this.doubleArray == null){
		    doubleArray = new double[this.totalCnt];
		}
		for(int i=0;i<this.totalShape[0];i++){
		    this.doubleArray[receiverOffset+i] = (Double)nestedStructure.get(i);
		}
	    }
	    else if(this.boxedType==Integer.class){
		if(this.integerArray == null){
		    integerArray = new int[this.totalCnt];
		}
		for(int i=0;i<this.totalShape[0];i++){
		    this.integerArray[receiverOffset+i] = (Integer)nestedStructure.get(i);
		}
	    }
	    else{
		if(this.objectArray == null){
		    objectArray = new Object[this.totalCnt];
		}
		for(int i=0;i<this.totalShape[0];i++){
		    this.objectArray[receiverOffset+i] = nestedStructure.get(i);
		}
	    }
	}
	else{
	    for(int i=0;i<this.totalShape[depth];i++){
		int[] newCoordinates = new int[coordinates.length];
		for(int j=0;j<newCoordinates.length; j++){
		    if(j==depth)
			newCoordinates[j] = i;
		    else
			newCoordinates[j] = coordinates[j];
		}
		flattenListRecurse(strides, (List)nestedStructure.get(i), depth-1, newCoordinates);
	    }
	}
    }

    public void flattenISeqRecurse(int[] strides, ISeq nestedStructure, int depth, int[] coordinates){
	if (depth == 0){

	    int receiverOffset = 0;
	    //	    int copierOffset = 0;
	    for(int i = 1;i<coordinates.length;i++){
		receiverOffset += strides[i]*coordinates[i];

	    }

	    if(this.boxedType==Boolean.class){
		if(this.booleanArray == null){
		    booleanArray = new boolean[this.totalCnt];
		}
		for(int i =0;i<this.totalShape[0]; nestedStructure = nestedStructure.next(), i++){
		    this.booleanArray[receiverOffset+i] = (Boolean)nestedStructure.first();
		}
	    }
	    else if(this.boxedType==Double.class){
		if(this.doubleArray == null){
		    doubleArray = new double[this.totalCnt];
		}

		for(int i=0;i<this.totalShape[0]; nestedStructure = nestedStructure.next(), i++){
		    this.doubleArray[receiverOffset+i] = (Double)nestedStructure.first();
		}
	    }
	    else if(this.boxedType==Integer.class){
		if(this.integerArray == null){
		    integerArray = new int[this.totalCnt];
		}
		for(int i=0;i<this.totalShape[0]; nestedStructure = nestedStructure.next(), i++){
		    this.integerArray[receiverOffset+i] = (Integer)nestedStructure.first();
		}
	    }
	    else{
		if(this.objectArray == null){
		    objectArray = new Object[this.totalCnt];
		}
		for(int i=0;i<this.totalShape[0]; nestedStructure = nestedStructure.next(), i++){
		    this.objectArray[receiverOffset+i] = nestedStructure.first();
		}
	    }
	}
	else{
	    for(int i=0;i<this.totalShape[depth]; nestedStructure = nestedStructure.next(), i++){
		int[] newCoordinates = new int[coordinates.length];
		for(int j=0;j<newCoordinates.length; j++){
		    if(j==depth)
			newCoordinates[j] = i;
		    else
			newCoordinates[j] = coordinates[j];
		}
		flattenISeqRecurse(strides, (ISeq)nestedStructure.first(), depth-1, newCoordinates);
	    }
	}
    }

    public void flattenArrayRecurse(int[] strides, Object nestedStructure, int depth, int[] coordinates){
	if (depth == 0){

	    int receiverOffset = 0;
	    for(int i = 1;i<coordinates.length;i++){
		receiverOffset += strides[i]*coordinates[i];

	    }

	    if(this.boxedType==Boolean.class){
		if(this.booleanArray == null){
		    booleanArray = new boolean[this.totalCnt];
		}
		for(int i =0;i<this.totalShape[0];i++){
		    this.booleanArray[receiverOffset+i] = (Boolean)Array.get(nestedStructure, i);
		}
	    }
	    else if(this.boxedType==Double.class){
		if(this.doubleArray == null){
		    doubleArray = new double[this.totalCnt];
		}
		for(int i=0;i<this.totalShape[0];i++){
		    this.doubleArray[receiverOffset+i] = (Double)Array.get(nestedStructure, i);
		}
	    }
	    else if(this.boxedType==Integer.class){
		if(this.integerArray == null){
		    integerArray = new int[this.totalCnt];
		}
		for(int i=0;i<this.totalShape[0];i++){
		    this.integerArray[receiverOffset+i] = (Integer)Array.get(nestedStructure, i);
		}
	    }
	    else{
		if(this.objectArray == null){
		    objectArray = new Object[this.totalCnt];
		}
		for(int i=0;i<this.totalShape[0];i++){
		    this.objectArray[receiverOffset+i] = Array.get(nestedStructure, i);
		}
	    }
	}
	else{
	    for(int i=0;i<this.totalShape[depth];i++){
		int[] newCoordinates = new int[coordinates.length];
		for(int j=0;j<newCoordinates.length; j++){
		    if(j==depth)
			newCoordinates[j] = i;
		    else
			newCoordinates[j] = coordinates[j];
		}
		flattenArrayRecurse(strides, Array.get(nestedStructure, i), depth-1, newCoordinates);
	    }
	}
    }



    private void flattenAndAssign(Object nestedStructure, Class boxedType, boolean primitive){
	
	int product = 1;
	int[] strides = new int[this.totalShape.length];
	int[] coordinates = new int[this.totalShape.length];
	for(int i=0;i<this.totalShape.length;i++){
	    strides[i] = product;
	    product *= this.totalShape[i];
	    coordinates[i] = this.totalShape[i]-1;
	}
	
	this.totalCnt = product;

	if(nestedStructure instanceof ISeq) {
	    flattenISeqRecurse(strides, (ISeq)nestedStructure, this.totalShape.length-1, coordinates);
	}
	else if(nestedStructure instanceof List) {
	    flattenListRecurse(strides, (List)nestedStructure, this.totalShape.length-1, coordinates);
	}
	else if(nestedStructure.getClass().isArray()) {
	    flattenArrayRecurse(strides, nestedStructure, this.totalShape.length-1, coordinates);
	}
	else{
	    throw new UnsupportedOperationException();
	}

	if(boxedType == Integer.class){
	    objectArray = null;
	    doubleArray = null;
	    booleanArray = null;
	}
	else if(boxedType == Boolean.class){
	    objectArray = null;
	    doubleArray = null;
	    integerArray = null;
	}
	else if(boxedType == Double.class){
	    objectArray = null;
	    booleanArray = null;
	    integerArray = null;
	}
	else{
	    doubleArray = null;
	    booleanArray = null;
	    integerArray = null;
	}
    }


    PersistentMatrix(int[] unitShape, Object nestedStructure, boolean primitive) {
	this.unitShape = unitShape;
	this.primitive = primitive;
	this.view = false;
	discoverStructure(nestedStructure); 

	flattenAndAssign(nestedStructure, boxedType, primitive); 
	for(int i=0,j=0;i<unitShape.length;i++){
	    if(unitShape[j] != 1){
		if(unitShape[i] != this.totalShape[i]){
		    throw new UnsupportedOperationException();
		}
		j++;
	    }
	}

	if(unitShape[0] != 1){
	    this.shape = new int[this.totalShape.length-unitShape.length];

	    for(int i=0;i<this.totalShape.length-unitShape.length;i++){
		this.shape[i] = this.totalShape[i+unitShape.length];
	    }
	}
	else{
	    this.shape = this.totalShape;
	}

	this.space = shape.length;
	this.indexMap = new int[shape.length][];

	for(int i=0;i<shape.length;i++){
	    indexMap[i] = new int[shape[i]];
	}

	int product = 1;
	int tempUnitSpace = 0;

	for(int i=0;i<unitShape.length;i++){
	    if(unitShape[i] > 1){
		product *= unitShape[i];
		tempUnitSpace += 1;
	    }
	}

	this.unitSpace = tempUnitSpace;

	int tempCnt = 1;

	for(int i=0;i<indexMap.length;i++){
	    for(int j=0;j<indexMap[i].length;j++){
		this.indexMap[i][j] = j*product;
	    }
	    product *=this.indexMap[i].length;
	    tempCnt *= this.shape[i];
	}

	this.unitCnt = tempCnt;
	//	this.totalCnt = product;

	int tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspace += 1;
	    }
	}

	this.subspace = tempSubspace;

	int[] tempSubspaceShape = new int[subspace];
	int[] tempSubspaceMap = new int[subspace];
	tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspaceShape[tempSubspace] = this.shape[i];
		tempSubspaceMap[tempSubspace] = i;
		tempSubspace += 1;
	    }
	}
	this.subspaceMap = tempSubspaceMap;
	this.subspaceShape = tempSubspaceShape;
	this.ALabels = null;
	this.ELabels = null;
    }

    PersistentMatrix(int[] unitShape, int[] shape, boolean[] booleanArray) {
	this.shape = shape;
	this.space = shape.length;
	this.unitShape = unitShape; // one dimension of length 1
	this.boxedType = Boolean.class;
	this.booleanArray = booleanArray;
	this.doubleArray = null;
	this.integerArray = null;
	this.objectArray = null;
	this.primitive = true;
	this.view = false;

	this.totalShape = new int[shape.length+unitShape.length];
	for(int i=0;i<this.totalShape.length;i++){
	    if(i<unitShape.length){
		this.totalShape[i] = unitShape[i];
	    }
	    else{
		this.totalShape[i] = shape[i-unitShape.length];
	    }
	}

	this.indexMap = new int[shape.length][];

	for(int i=0;i<shape.length;i++){
	    indexMap[i] = new int[shape[i]];
	}

	int product = 1;
	int tempUnitSpace = 0;

	for(int i=0;i<unitShape.length;i++){
	    if(unitShape[i] > 1){
		product *= unitShape[i];
		tempUnitSpace += 1;
	    }
	}

	this.unitSpace = tempUnitSpace;

	int tempCnt = 1;

	for(int i=0;i<indexMap.length;i++){
	    for(int j=0;j<indexMap[i].length;j++){
		this.indexMap[i][j] = j*product;
	    }
	    product *=this.indexMap[i].length;
	    tempCnt *= this.shape[i];
	}

	this.unitCnt = tempCnt;
	this.totalCnt = product;

	int tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspace += 1;
	    }
	}

	this.subspace = tempSubspace;

	int[] tempSubspaceShape = new int[subspace];
	int[] tempSubspaceMap = new int[subspace];
	tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspaceShape[tempSubspace] = this.shape[i];
		tempSubspaceMap[tempSubspace] = i;
		tempSubspace += 1;
	    }
	}
	this.subspaceMap = tempSubspaceMap;
	this.subspaceShape = tempSubspaceShape;
	this.ALabels = null;
	this.ELabels = null;
    }

    PersistentMatrix(int[] unitShape, int[] shape, double[] doubleArray) {
	this.shape = shape;
	this.space = shape.length;
	this.unitShape = unitShape; // one dimension of length 1
	this.boxedType = Double.class;
	this.doubleArray = doubleArray;
	this.booleanArray = null;
	this.integerArray = null;
	this.objectArray = null;
	this.primitive = true;
	this.view = false;

	this.totalShape = new int[shape.length+unitShape.length];
	for(int i=0;i<this.totalShape.length;i++){
	    if(i<unitShape.length){
		this.totalShape[i] = unitShape[i];
	    }
	    else{
		this.totalShape[i] = shape[i-unitShape.length];
	    }
	}


	this.indexMap = new int[shape.length][];

	for(int i=0;i<shape.length;i++){
	    indexMap[i] = new int[shape[i]];
	}

	int product = 1;
	int tempUnitSpace = 0;

	for(int i=0;i<unitShape.length;i++){
	    if(unitShape[i] > 1){
		product *= unitShape[i];
		tempUnitSpace += 1;
	    }
	}

	this.unitSpace = tempUnitSpace;

	int tempCnt = 1;

	for(int i=0;i<indexMap.length;i++){
	    for(int j=0;j<indexMap[i].length;j++){
		this.indexMap[i][j] = j*product;
	    }
	    product *=this.indexMap[i].length;
	    tempCnt *= this.shape[i];
	}

	this.unitCnt = tempCnt;
	this.totalCnt = product;

	int tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspace += 1;
	    }
	}

	this.subspace = tempSubspace;

	int[] tempSubspaceShape = new int[subspace];
	int[] tempSubspaceMap = new int[subspace];
	tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspaceShape[tempSubspace] = this.shape[i];
		tempSubspaceMap[tempSubspace] = i;
		tempSubspace += 1;
	    }
	}
	this.subspaceMap = tempSubspaceMap;
	this.subspaceShape = tempSubspaceShape;
	this.ALabels = null;
	this.ELabels = null;
    }

    PersistentMatrix(int[] unitShape, int[] shape, int[] integerArray) {
	this.shape = shape;
	this.space = shape.length;
	this.unitShape = unitShape; // one dimension of length 1
	this.boxedType = Integer.class;
	this.integerArray = integerArray;
	this.doubleArray = null;
	this.booleanArray = null;
	this.objectArray = null;
	this.primitive = true;
	this.view = false;

	this.totalShape = new int[shape.length+unitShape.length];
	for(int i=0;i<this.totalShape.length;i++){
	    if(i<unitShape.length){
		this.totalShape[i] = unitShape[i];
	    }
	    else{
		this.totalShape[i] = shape[i-unitShape.length];
	    }
	}


	this.indexMap = new int[shape.length][];

	for(int i=0;i<shape.length;i++){
	    indexMap[i] = new int[shape[i]];
	}

	int product = 1;
	int tempUnitSpace = 0;

	for(int i=0;i<unitShape.length;i++){
	    if(unitShape[i] > 1){
		product *= unitShape[i];
		tempUnitSpace += 1;
	    }
	}

	this.unitSpace = tempUnitSpace;

	int tempCnt = 1;

	for(int i=0;i<indexMap.length;i++){
	    for(int j=0;j<indexMap[i].length;j++){
		this.indexMap[i][j] = j*product;
	    }
	    product *=this.indexMap[i].length;
	    tempCnt *= this.shape[i];
	}

	this.unitCnt = tempCnt;
	this.totalCnt = product;

	int tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspace += 1;
	    }
	}

	this.subspace = tempSubspace;

	int[] tempSubspaceShape = new int[subspace];
	int[] tempSubspaceMap = new int[subspace];
	tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspaceShape[tempSubspace] = this.shape[i];
		tempSubspaceMap[tempSubspace] = i;
		tempSubspace += 1;
	    }
	}
	this.subspaceMap = tempSubspaceMap;
	this.subspaceShape = tempSubspaceShape;
	this.ALabels = null;
	this.ELabels = null;

    }

    PersistentMatrix(int[] unitShape, int[] shape, Object[] objectArray) {
	this.shape = shape;
	this.space = shape.length;
	this.unitShape = unitShape; // one dimension of length 1
	this.boxedType = Object.class;
	this.objectArray = objectArray;
	this.doubleArray = null;
	this.integerArray = null;
	this.booleanArray = null;
	this.primitive = false;
	this.view = false;

	this.totalShape = new int[shape.length+unitShape.length];
	for(int i=0;i<this.totalShape.length;i++){
	    if(i<unitShape.length){
		this.totalShape[i] = unitShape[i];
	    }
	    else{
		this.totalShape[i] = shape[i-unitShape.length];
	    }
	}


	this.indexMap = new int[shape.length][];

	for(int i=0;i<shape.length;i++){
	    indexMap[i] = new int[shape[i]];
	}

	int product = 1;
	int tempUnitSpace = 0;

	for(int i=0;i<unitShape.length;i++){
	    if(unitShape[i] > 1){
		product *= unitShape[i];
		tempUnitSpace += 1;
	    }
	}

	this.unitSpace = tempUnitSpace;

	int tempCnt = 1;

	for(int i=0;i<indexMap.length;i++){
	    for(int j=0;j<indexMap[i].length;j++){
		this.indexMap[i][j] = j*product;
	    }
	    product *=this.indexMap[i].length;
	    tempCnt *= this.shape[i];
	}

	this.unitCnt = tempCnt;
	this.totalCnt = product;

	int tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspace += 1;
	    }
	}

	this.subspace = tempSubspace;

	int[] tempSubspaceShape = new int[subspace];
	int[] tempSubspaceMap = new int[subspace];
	tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspaceShape[tempSubspace] = this.shape[i];
		tempSubspaceMap[tempSubspace] = i;
		tempSubspace += 1;
	    }
	}
	this.subspaceMap = tempSubspaceMap;
	this.subspaceShape = tempSubspaceShape;
	this.ALabels = null;
	this.ELabels = null;

    }

    PersistentMatrix(int[] unitShape, int[][] indexMap, PersistentMatrix baseMatrix) {

	super(null);
	this.boxedType = baseMatrix.boxedType;
	this.booleanArray = baseMatrix.booleanArray;
	this.doubleArray = baseMatrix.doubleArray;
	this.integerArray = baseMatrix.integerArray;
	this.objectArray = baseMatrix.objectArray;

	this.primitive = baseMatrix.primitive;
	this.view = true;


	// Set passed in properties
	this.indexMap = indexMap;
	int[] shape = new int[indexMap.length];
	for(int i=0; i<indexMap.length;i++){
	    shape[i] = indexMap[i].length;
	}
	this.shape = shape;
	this.unitShape = unitShape;

	this.totalShape = new int[shape.length+unitShape.length];
	for(int i=0;i<this.totalShape.length;i++){
	    if(i<unitShape.length){
		this.totalShape[i] = unitShape[i];
	    }
	    else{
		this.totalShape[i] = shape[i-unitShape.length];
	    }
	}



	int product = 1;
	int tempUnitSpace = 0;

	for(int i=0;i<unitShape.length;i++){
	    if(unitShape[i] > 1){
		product *= unitShape[i];
		tempUnitSpace += 1;
	    }
	}

	this.unitSpace = tempUnitSpace;


	this.space = shape.length;

	int tempCnt = 1;
	int tempSubspace = 0;
	for(int i=0; i<this.space; i++){
	    if(this.shape[i] != 1){
		tempSubspace += 1;
	    }
	    tempCnt *= this.shape[i];
	}

	this.unitCnt = tempCnt;
	this.totalCnt = tempCnt*product;
	this.subspace = tempSubspace;

	int[] tempSubspaceShape = new int[subspace];
	int[] tempSubspaceMap = new int[subspace];
	tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspaceShape[tempSubspace] = this.shape[i];
		tempSubspaceMap[tempSubspace] = i;
		tempSubspace += 1;
	    }
	}
	this.subspaceMap = tempSubspaceMap;
	this.subspaceShape = tempSubspaceShape;
	this.ALabels = null;
	this.ELabels = null;

    }


    PersistentMatrix(IPersistentMap meta, int[] unitShape, int[][] indexMap, PersistentMatrix baseMatrix) {
	super(meta);
	this.boxedType = baseMatrix.boxedType;
	this.booleanArray = baseMatrix.booleanArray;
	this.doubleArray = baseMatrix.doubleArray;
	this.integerArray = baseMatrix.integerArray;
	this.objectArray = baseMatrix.objectArray;

	this.primitive = baseMatrix.primitive;
	this.view = true;

	// Set passed in properties
	this.indexMap = indexMap;
	int[] shape = new int[indexMap.length];
	for(int i=0; i<indexMap.length;i++){
	    shape[i] = indexMap[i].length;
	}
	this.shape = shape;
	this.unitShape = unitShape;

	this.totalShape = new int[shape.length+unitShape.length];
	for(int i=0;i<this.totalShape.length;i++){
	    if(i<unitShape.length){
		this.totalShape[i] = unitShape[i];
	    }
	    else{
		this.totalShape[i] = shape[i-unitShape.length];
	    }
	}


	int product = 1;
	int tempUnitSpace = 0;

	for(int i=0;i<unitShape.length;i++){
	    if(unitShape[i] > 1){
		product *= unitShape[i];
		tempUnitSpace += 1;
	    }
	}

	this.unitSpace = tempUnitSpace;


	this.space = shape.length;

	int tempCnt = 1;
	int tempSubspace = 0;
	for(int i=0; i<this.space; i++){
	    if(this.shape[i] != 1){
		tempSubspace += 1;
	    }
	    tempCnt *= this.shape[i];
	}

	this.unitCnt = tempCnt;
	this.totalCnt = tempCnt*product;
	this.subspace = tempSubspace;

	int[] tempSubspaceShape = new int[subspace];
	int[] tempSubspaceMap = new int[subspace];
	tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspaceShape[tempSubspace] = this.shape[i];
		tempSubspaceMap[tempSubspace] = i;
		tempSubspace += 1;
	    }
	}
	this.subspaceMap = tempSubspaceMap;
	this.subspaceShape = tempSubspaceShape;
	this.ALabels = null;
	this.ELabels = null;
    }

    PersistentMatrix(int[] unitShape, int[] shape, PersistentMatrix baseMatrix) {
	super(null);
	this.boxedType = baseMatrix.boxedType;
	this.booleanArray = baseMatrix.booleanArray;
	this.doubleArray = baseMatrix.doubleArray;
	this.integerArray = baseMatrix.integerArray;
	this.objectArray = baseMatrix.objectArray;

	this.unitShape = unitShape;
	this.primitive = baseMatrix.primitive;
	this.view = baseMatrix.view;
	this.shape = shape;
	this.space = shape.length;

	this.totalShape = new int[shape.length+unitShape.length];
	for(int i=0;i<this.totalShape.length;i++){
	    if(i<unitShape.length){
		this.totalShape[i] = unitShape[i];
	    }
	    else{
		this.totalShape[i] = shape[i-unitShape.length];
	    }
	}

	this.indexMap = new int[shape.length][];

	for(int i=0;i<shape.length;i++){
	    indexMap[i] = new int[shape[i]];
	}

	int product = 1;
	int tempUnitSpace = 0;

	for(int i=0;i<unitShape.length;i++){
	    if(unitShape[i] > 1){
		product *= unitShape[i];
		tempUnitSpace += 1;
	    }
	}

	this.unitSpace = tempUnitSpace;

	int tempCnt = 1;

	for(int i=0;i<indexMap.length;i++){
	    for(int j=0;j<indexMap[i].length;j++){
		this.indexMap[i][j] = j*product;
	    }
	    product *=this.indexMap[i].length;
	    tempCnt *= this.shape[i];
	}

	this.unitCnt = tempCnt;
	this.totalCnt = product;

	int tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspace += 1;
	    }
	}

	this.subspace = tempSubspace;

	int[] tempSubspaceShape = new int[subspace];
	int[] tempSubspaceMap = new int[subspace];
	tempSubspace = 0;
	for(int i=0; i<space; i++){
	    if(this.shape[i] != 1){
		tempSubspaceShape[tempSubspace] = this.shape[i];
		tempSubspaceMap[tempSubspace] = i;
		tempSubspace += 1;
	    }
	}
	this.subspaceMap = tempSubspaceMap;
	this.subspaceShape = tempSubspaceShape;
	this.ALabels = null;
	this.ELabels = null;

    }
    
    PersistentMatrix(IPersistentMap ALabels, IPersistentMap[] ELabels, PersistentMatrix baseMatrix) { 

	super(null);
	this.boxedType = baseMatrix.boxedType;
	this.booleanArray = baseMatrix.booleanArray;
	this.doubleArray = baseMatrix.doubleArray;
	this.integerArray = baseMatrix.integerArray;
	this.objectArray = baseMatrix.objectArray;
	this.unitShape = baseMatrix.unitShape;
	this.primitive = baseMatrix.primitive;
	this.view = baseMatrix.view;
	this.shape = baseMatrix.shape;
	this.space = baseMatrix.space;
	this.totalShape = baseMatrix.totalShape;
	this.indexMap = baseMatrix.indexMap;
	this.unitSpace = baseMatrix.unitSpace;
	this.unitCnt = baseMatrix.unitCnt;
	this.totalCnt = baseMatrix.totalCnt;
	this.subspace = baseMatrix.subspace;
	this.subspaceMap = baseMatrix.subspaceMap;
	this.subspaceShape = baseMatrix.subspaceShape;
	this.ALabels = ALabels;
	this.ELabels = ELabels;

    }


    public PersistentMatrix addLabels(Object ALabels, Object ELabels){

	IPersistentMap newALabels;
	IPersistentMap[] newELabels;

	if(ALabels == null){
	    newALabels = this.ALabels;
	}
	else{
	    if(ALabels instanceof ISeq) {
		ISeq castedALabels = (ISeq)ALabels;
		IPersistentMap tempMap = PersistentHashMap.EMPTY;
		for(int i = 0; i < castedALabels.count(); castedALabels = castedALabels.next(), i++) {
		    tempMap = tempMap.assoc(castedALabels.first(), i);
		}
		newALabels = tempMap;
	    }
	    else if(ALabels instanceof List) {
		List castedALabels = (List)ALabels;
		IPersistentMap tempMap = PersistentHashMap.EMPTY;
		for(int i = 0; i<castedALabels.size(); i++) {
		    tempMap = tempMap.assoc(castedALabels.get(i), i);
		}
		newALabels = tempMap;
	    }
	    else if(ALabels.getClass().isArray()) {
		Object[] castedALabels = (Object[])ALabels;
		IPersistentMap tempMap = PersistentHashMap.EMPTY;
		for(int i = 0; i<castedALabels.length; i++) {
		    tempMap = tempMap.assoc(castedALabels[i], i);
		}
		newALabels = tempMap;
	    }
	    else{
		throw new UnsupportedOperationException();
	    }	
	}

	if(ELabels == null){
	    newELabels = this.ELabels;
	}
	else{
	    if(ELabels instanceof ISeq) {
		ISeq castedELabels = (ISeq)ELabels;
		ISeq nestedELabels;
		IPersistentMap[] tempMap = new PersistentHashMap[castedELabels.count()];
		for(int i = 0; i < castedELabels.count(); castedELabels = (ISeq)castedELabels.next(), i++) {
		    nestedELabels = (ISeq)castedELabels.first();
		    tempMap[i] = PersistentHashMap.EMPTY;
		    for(int j = 0; j < nestedELabels.count(); nestedELabels = (ISeq)nestedELabels.next(), j++) {
			tempMap[i] = tempMap[i].assoc(nestedELabels.first(), j);
		    }
		}
		newELabels = tempMap;
	    }
	    else if(ELabels instanceof List) {
		List castedELabels = (List)ELabels;
		List nestedELabels;
		IPersistentMap[] tempMap = new PersistentHashMap[castedELabels.size()];
		for(int i = 0; i < castedELabels.size(); i++) {
		    nestedELabels = (List)castedELabels.get(i);
		    tempMap[i] = PersistentHashMap.EMPTY;
		    for (int j = 0; j < nestedELabels.size(); j++) {
			tempMap[i] = tempMap[i].assoc(nestedELabels.get(j), j);
		    }
		}
		newELabels = tempMap;
	    }
	    else if(ELabels.getClass().isArray()) {
		Object[][] castedELabels = (Object[][])ELabels;
		Object[] nestedELabels;
		IPersistentMap[] tempMap = new PersistentHashMap[castedELabels.length];
		for(int i = 0; i < castedELabels.length; i++) {
		    nestedELabels = (Object[])castedELabels[i];
		    tempMap[i] = PersistentHashMap.EMPTY;
		    for (int j = 0; j < nestedELabels.length; j++) {
			tempMap[i] = tempMap[i].assoc(nestedELabels[j], j);
		    }
		}
		newELabels = tempMap;
	    }
	    else{
		throw new UnsupportedOperationException();
	    }	
	}


	return new PersistentMatrix(newALabels, newELabels, this);
    }

    public PersistentMatrix reshape(int[] shape){
	return new PersistentMatrix(this.unitShape, shape, this);
    }

    public int getLastSDimInDeep(){
	return subspaceMap[subspace-1];
    }

    public int getSpace(){
	return space;
    }

    public int getSubspace() {
	return subspace;
    }
    
    public int[] getSubspaceShape() {
	return subspaceShape;
    }

    public int[] getSubspaceMap() {
	return subspaceMap;
    }

    public int[] getShape() {
	return shape;
    }

    public int[][] getIndexMap() {
	return indexMap;
    }
    

    public IPersistentMap getALabels() {
	return ALabels;
    }

    public IPersistentMap[] getELabels() {
	return ELabels;
    }

    public int[] getIntegerArray() {
	return integerArray;
    }

    public boolean[] getBooleanArray() {
	return booleanArray;
    }
    
    public double[] getDoubleArray() {
	return doubleArray;
    }

    public Object[] getObjectArray() {
	return objectArray;
    }

    public Class getBoxedType() {
	return boxedType;
    }

    public Object index(int[] dCoordinates) {
	int Offset = 0;
	for(int i=0;i<dCoordinates.length;i++){
	    Offset += indexMap[i][dCoordinates[i]];
	}

	if(unitSpace == 0){
	    Class elementClass = boxedType;
	    if(elementClass==Boolean.class)
		return booleanArray[Offset];
	    else if(elementClass==Double.class)
		return doubleArray[Offset];
	    else if(elementClass==Integer.class)
		return integerArray[Offset];
	    else
		return objectArray[Offset];
	}
	else{

	    int[][] tempIndexMap = new int[unitShape.length][];

	    for(int i=0;i<unitShape.length;i++){
		tempIndexMap[i] = new int[unitShape[i]];
	    }

	    int product = 1;
	    int tempCnt = 1;
	    
	    for(int i=0;i<tempIndexMap.length;i++){
		for(int j=0;j<tempIndexMap[i].length;j++){
		    tempIndexMap[i][j] = Offset + j*product;
		}
		product *= tempIndexMap[i].length;
		tempCnt *= unitShape[i];
	    }

	    return new PersistentMatrix(new int[]{1}, tempIndexMap, this);

	}
    }


    public PersistentMatrix slice(int dDimIndex, int startingElement, int endingElement){
	int[][] newIndexMap = new int[indexMap.length][];
	IPersistentMap[] newELabels = null;	
	if(ELabels != null){
	    newELabels = ELabels.clone();
	}
	ISeq tempELabels;
	int loopmax = space;
	for(int i=0;i<loopmax;i++){
	    if(i==dDimIndex){
		newIndexMap[i] = new int[endingElement-startingElement];
		if(newELabels != null){
		    newELabels[i] = PersistentHashMap.EMPTY;
		}
		for(int j=0;j<indexMap[i].length;j++){
		    if(startingElement <= j && j < endingElement){
			newIndexMap[i][j-startingElement] = indexMap[i][j];
			if(ELabels != null){
			    tempELabels = ELabels[i].seq();
			    for(int k = 0; k < ELabels[i].count(); tempELabels = tempELabels.next(), k++) {
				if(((IMapEntry)tempELabels.first()).val() == (Integer)j){
				    newELabels[i] = newELabels[i].assoc(((IMapEntry)tempELabels.first()).key(), (Integer)(j-startingElement));
				}
			    }
			}
		    }
		}
	    }
	    else{
		newIndexMap[i] = indexMap[i];
	    }
	}
	// validate submatrix
	return new PersistentMatrix(ALabels, newELabels, new PersistentMatrix(unitShape, newIndexMap, this));
    }

    public PersistentMatrix squeeze(){

	int[] coordinates = new int[subspace];
	int squeezeCnt = 1;
	for(int i=0; i<subspace; i++){
	    coordinates[i] = subspaceShape[i]-1;
	    squeezeCnt *= subspaceShape[i];
	}

	IPersistentMap[] tempELabels = null;

	if(ELabels != null){
	    tempELabels = new PersistentHashMap[subspace];

	    for(int i=0; i < subspace; i++){
		tempELabels[i] = ELabels[subspaceMap[i]];
	    }
	}

	IPersistentMap tempALabels = null;

	if(ALabels != null){
	    tempALabels = PersistentHashMap.EMPTY;
	    ISeq ALabelSeq = ALabels.seq();

	    for(int i=0; i < ALabels.count(); ALabelSeq = ALabelSeq.next(), i++) {
		for(int j=0; j < subspace; j++){
		    if(((IMapEntry)ALabelSeq.first()).val() == (Integer)subspaceMap[j]){
			tempALabels = tempALabels.assoc(((IMapEntry)ALabelSeq.first()).key(), (Integer)j);
		    }
		}
	    }
	}
	if(boxedType == Boolean.class){
	    boolean[] newBooleanArray = new boolean[squeezeCnt];
	    PersistentMatrix newMat = new PersistentMatrix(unitShape, subspaceShape, newBooleanArray);

	    copyRecurse(this, newMat, subspace-1, coordinates);
	    return new PersistentMatrix(tempALabels, tempELabels, newMat);
	}
	else if(boxedType == Double.class){
	    double[] newDoubleArray = new double[squeezeCnt];
	    PersistentMatrix newMat = new PersistentMatrix(unitShape, subspaceShape, newDoubleArray);
	    
	    copyRecurse(this, newMat, subspace-1, coordinates);
	    return new PersistentMatrix(tempALabels, tempELabels, newMat);
	}
	else if(boxedType == Integer.class){
	    int[] newIntegerArray = new int[squeezeCnt];
	    PersistentMatrix newMat = new PersistentMatrix(unitShape, subspaceShape, newIntegerArray);
	    
	    copyRecurse(this, newMat, subspace-1, coordinates);
	    return new PersistentMatrix(tempALabels, tempELabels, newMat);
	}
	else{
	    Object[] newObjectArray = new Object[squeezeCnt];
	    PersistentMatrix newMat = new PersistentMatrix(unitShape, subspaceShape, newObjectArray);
	    
	    copyRecurse(this, newMat, subspace-1, coordinates);
	    return new PersistentMatrix(tempALabels, tempELabels, newMat);
	}
    }


    public Object nth(int index){
	
	if(index>=shape[subspaceMap[subspace-1]])
	    throw new IndexOutOfBoundsException();
	else{
	    int dDim = subspaceMap[subspace-1];
	    int[] dCoordinates = new int[space];
	    PersistentMatrix nthMat = slice(dDim, index, index+1);
	    if(nthMat.subspace == 0){
		for(int i=0;i<space;i++){
		    dCoordinates[i] = 0;
		}
		return nthMat.index(dCoordinates);
	    }
	    else
		return nthMat.squeeze();
	}
	    
    }

    // this is where I stopped with the updates


    public IPersistentCollection empty() {
	return new PersistentMatrix(new int[]{1}, new int[] {}, new Object[]{null});
    }

    public PersistentMatrix withMeta(IPersistentMap meta){
	return new PersistentMatrix(meta, unitShape, indexMap, this);
    }

    public boolean consable(PersistentMatrix consMat){
	if(consMat.boxedType == this.boxedType){
	    if(consMat.unitSpace == this.unitSpace){
		if(subspace != 0 && consMat.subspace != 0){
		    if(Arrays.equals(consMat.subspaceShape,this.subspaceShape)){
			return true;
		    }
		    if(consMat.subspaceShape.length == this.subspaceShape.length-1){
			for(int i=0;i<consMat.subspaceShape.length;i++){
			    if(consMat.subspaceShape[i] != this.subspaceShape[i]){
				return false;
			    }
			}
			return true;
		    }
		}
	    }
	}
	return false;     
    }

    public IPersistentMap consALabels(PersistentMatrix firstMat, PersistentMatrix secondMat){
	IPersistentMap tempFirstALabels = null;
	IPersistentMap tempSubFirstALabels = null;

	if(firstMat.ALabels != null){
	    tempFirstALabels = PersistentHashMap.EMPTY;
	    tempSubFirstALabels = PersistentHashMap.EMPTY;
	    ISeq FirstALabelSeq = firstMat.ALabels.seq();

	    for(int i=0; i < firstMat.ALabels.count(); FirstALabelSeq = FirstALabelSeq.next(), i++) {
		for(int j=0; j < firstMat.subspace; j++){
		    if(((IMapEntry)FirstALabelSeq.first()).val() == (Integer)firstMat.subspaceMap[j]){
			tempFirstALabels = tempFirstALabels.assoc(((IMapEntry)FirstALabelSeq.first()).key(), (Integer)j);
			if(((IMapEntry)FirstALabelSeq.first()).val() != (Integer)firstMat.subspaceMap[firstMat.subspace-1]){
			    tempSubFirstALabels = tempSubFirstALabels.assoc(((IMapEntry)FirstALabelSeq.first()).key(), (Integer)j);
			}
		    }
		}
	    }
	}

	IPersistentMap tempSecondALabels = null;

	if(secondMat.ALabels != null){
	    tempSecondALabels = PersistentHashMap.EMPTY;
	    ISeq SecondALabelSeq = secondMat.ALabels.seq();

	    for(int i=0; i < secondMat.ALabels.count(); SecondALabelSeq = SecondALabelSeq.next(), i++) {
		for(int j=0; j < secondMat.subspace; j++){
		    if(((IMapEntry)SecondALabelSeq.first()).val() == (Integer)secondMat.subspaceMap[j]){
			tempSecondALabels = tempSecondALabels.assoc(((IMapEntry)SecondALabelSeq.first()).key(), (Integer)j);
		    }
		}
	    }
	}

	if(tempFirstALabels == null || tempSecondALabels == null){
	    return null;
	}
	else{
	    if(tempSecondALabels.equals(tempFirstALabels) || tempSecondALabels.equals(tempSubFirstALabels)){
		return tempFirstALabels;
	    } 
	    else{
		throw new UnsupportedOperationException();
	    }
	}
    }

    public IPersistentMap[] consELabels(PersistentMatrix firstMat, PersistentMatrix secondMat){


	IPersistentMap[] tempFirstELabels = null;
	IPersistentMap[] tempSubFirstELabels = null;

	if(firstMat.ELabels != null){
	    tempFirstELabels = new PersistentHashMap[firstMat.subspace];
	    tempSubFirstELabels = new PersistentHashMap[firstMat.subspace-1];

	    for(int i=0; i < firstMat.subspace; i++){
		tempFirstELabels[i] = firstMat.ELabels[firstMat.subspaceMap[i]];
		if(i<firstMat.subspace-1){
		    tempSubFirstELabels[i] = firstMat.ELabels[firstMat.subspaceMap[i]];
		}
	    }
	}


	IPersistentMap[] tempSecondELabels = null;

	if(secondMat.ELabels != null){
	    tempSecondELabels = new PersistentHashMap[secondMat.subspace];

	    for(int i=0; i < secondMat.subspace; i++){
		tempSecondELabels[i] = secondMat.ELabels[secondMat.subspaceMap[i]];
	    }
	}

	if(firstMat.ELabels == null || secondMat.ELabels == null){
	    return null;
	}
	else{

	    if(Arrays.equals(tempSecondELabels, tempFirstELabels)){
		return tempFirstELabels;
	    }
	    else if(Arrays.equals(tempSecondELabels, tempSubFirstELabels)){
		int fullLength = tempFirstELabels.length;
		int subLength = fullLength-1;
		IPersistentMap[] consedELabels = new PersistentHashMap[fullLength];
		for(int i=0;i<subLength;i++){
		    consedELabels[i] = tempSecondELabels[i];
		}
		if(secondMat.ELabels[secondMat.ELabels.length-1].count() == 1){
		    consedELabels[consedELabels.length-1] = tempFirstELabels[tempFirstELabels.length-1].assoc(((IMapEntry)secondMat.ELabels[secondMat.ELabels.length-1].seq().first()).key(), tempFirstELabels[tempFirstELabels.length-1].count());
		    return consedELabels;
		}
		else{
		    throw new UnsupportedOperationException();
		}
	    }
	    else{
		throw new UnsupportedOperationException();
	    }

	}
    }

	


    public PersistentMatrix cons(Object o){ 
	PersistentMatrix consMat = (PersistentMatrix) o;

	int[] tempShape;

	if(!consable(consMat)){
	    System.out.println("Not consable");
	    throw new UnsupportedOperationException(); // I should throw an exception here
	}
	else{

	    IPersistentMap consedALabels = consALabels(this, consMat);
	    IPersistentMap[] consedELabels = consELabels(this, consMat);

	    int newCount = totalCnt + consMat.totalCnt;


	    if(consMat.subspace == this.subspace){ 
		if(this.subspaceMap[subspace-1]==this.space-1){

		    tempShape = new int[shape.length+1];
		    
		    for(int i=0;i<tempShape.length;i++){
			if(i==tempShape.length-1){
			    tempShape[i] = 2;
			}
			else{
			    tempShape[i] = shape[i];
			}
		    }
		}
		else{

		    tempShape = new int[shape.length];
		    
		    for(int i=0;i<tempShape.length;i++){
			if(i==tempShape.length-1){
			    tempShape[i] = shape[i]+1; // should be two
			    if(shape[i]+1 != 2) // test: TODO remove
				System.out.println("There is an error in cons");
			
			}
			else{
			    tempShape[i] = shape[i];
			}
		    }

		}
	    }
	    else{


		tempShape = new int[shape.length];
		
		for(int i=0;i<tempShape.length;i++){
		    if(i==subspaceMap[subspace-1]){
			tempShape[i] = shape[i]+1; // is necessarily not two
		    }
		    else{
			tempShape[i] = shape[i];
		    }
		}
	    }

	    int[] head_coordinates = new int[this.subspace];
	    for(int i=0; i<this.subspace; i++){
      		head_coordinates[i] = this.subspaceShape[i]-1;
	    }
	    
	    int[] tail_coordinates = new int[consMat.subspace];
	    for(int i=0; i<consMat.subspace; i++){
      		tail_coordinates[i] = consMat.subspaceShape[i]-1;
	    }


	    if(boxedType == Boolean.class){
		boolean[] newBooleanArray = new boolean[newCount];
		PersistentMatrix consedMat = new PersistentMatrix(unitShape, tempShape, newBooleanArray);
		int sliceDim = consedMat.subspaceMap[consedMat.subspace-1];
		int dimLength = consedMat.shape[sliceDim];
		PersistentMatrix firstMat = consedMat.slice(sliceDim, 0, dimLength-1);
		PersistentMatrix secondMat = consedMat.slice(sliceDim, dimLength-1, dimLength);

		copyRecurse(this, firstMat, subspace-1, head_coordinates);
		copyRecurse(consMat, secondMat, consMat.subspace-1, tail_coordinates);
		return new PersistentMatrix(consedALabels, consedELabels, consedMat);
	    }
	    else if(boxedType == Double.class){
		double[] newDoubleArray = new double[newCount];
		PersistentMatrix consedMat = new PersistentMatrix(unitShape, tempShape, newDoubleArray);
		int sliceDim = consedMat.subspaceMap[consedMat.subspace-1];
		int dimLength = consedMat.shape[sliceDim];
		PersistentMatrix firstMat = consedMat.slice(sliceDim, 0, dimLength-1);
		PersistentMatrix secondMat = consedMat.slice(sliceDim, dimLength-1, dimLength);

		copyRecurse(this, firstMat, subspace-1, head_coordinates);
		copyRecurse(consMat, secondMat, consMat.subspace-1, tail_coordinates);
		return new PersistentMatrix(consedALabels, consedELabels, consedMat);
	    }
	    else if(boxedType == Integer.class){
		int[] newIntegerArray = new int[newCount];
		PersistentMatrix consedMat = new PersistentMatrix(unitShape, tempShape, newIntegerArray);
		int sliceDim = consedMat.subspaceMap[consedMat.subspace-1];
		int dimLength = consedMat.shape[sliceDim];
		PersistentMatrix firstMat = consedMat.slice(sliceDim, 0, dimLength-1);
		PersistentMatrix secondMat = consedMat.slice(sliceDim, dimLength-1, dimLength);
		if(!Arrays.equals(this.subspaceShape,firstMat.subspaceShape)){
		    System.out.println("array 1 not the right size");
		}
		if(!Arrays.equals(consMat.subspaceShape,secondMat.subspaceShape)){
		    System.out.println("array 1 not the right size");
		}

		copyRecurse(this, firstMat, subspace-1, head_coordinates);
		copyRecurse(consMat, secondMat, consMat.subspace-1, tail_coordinates);
		return new PersistentMatrix(consedALabels, consedELabels, consedMat);
	    }
	    else{
		Object[] newObjectArray = new Object[newCount];
		PersistentMatrix consedMat = new PersistentMatrix(unitShape, tempShape, newObjectArray);
		int sliceDim = consedMat.subspaceMap[consedMat.subspace-1];
		int dimLength = consedMat.shape[sliceDim];
		PersistentMatrix firstMat = consedMat.slice(sliceDim, 0, dimLength-1);
		PersistentMatrix secondMat = consedMat.slice(sliceDim, dimLength-1, dimLength);

		copyRecurse(this, firstMat, subspace-1, head_coordinates);
		copyRecurse(consMat, secondMat, consMat.subspace-1, tail_coordinates);
		return new PersistentMatrix(consedALabels, consedELabels, consedMat);
	    }
	    
	}
    }


    public void copyRecurse(PersistentMatrix copier, PersistentMatrix receiver, int depth, int[] coordinates){
	if (depth == 0){

	    int receiverOffset = 0;
	    int copierOffset = 0;

	    for(int i = 0;i<copier.space;i++){
		if(copier.shape[i] == 1){
		    copierOffset += copier.indexMap[i][0];
		}
	    }
	    for(int i = 0;i<receiver.space;i++){
		if(receiver.shape[i] == 1){
		    receiverOffset += receiver.indexMap[i][0];
		}
	    }
	    
	    for(int i = 1;i<coordinates.length;i++){// not >=0 so that you don't do the final dimension
		receiverOffset += receiver.indexMap[receiver.subspaceMap[i]][coordinates[i]];
		copierOffset += copier.indexMap[copier.subspaceMap[i]][coordinates[i]];
	    }

	    if(copier.boxedType==Boolean.class){
		for(int i =0;i<copier.shape[copier.subspaceMap[0]];i++){
		    for(int j=0;j<(copier.totalCnt/copier.unitCnt);j++){
			receiver.booleanArray[receiverOffset+receiver.indexMap[receiver.subspaceMap[0]][i]] = copier.booleanArray[copierOffset+copier.indexMap[copier.subspaceMap[0]][i]];
		    }
		}
	    }
	    else if(copier.boxedType==Double.class){
		for(int i=0;i<copier.shape[copier.subspaceMap[0]];i++){
		    for(int j=0;j<(copier.totalCnt/copier.unitCnt);j++){
			receiver.doubleArray[receiverOffset+receiver.indexMap[receiver.subspaceMap[0]][i]] = copier.doubleArray[copierOffset+copier.indexMap[copier.subspaceMap[0]][i]];
		    }
		}
	    }
	    else if(copier.boxedType==Integer.class){
		for(int i=0;i<copier.shape[copier.subspaceMap[0]];i++){
		    for(int j=0;j<(copier.totalCnt/copier.unitCnt);j++){
			receiver.integerArray[receiverOffset+j+receiver.indexMap[receiver.subspaceMap[0]][i]] = copier.integerArray[copierOffset+j+copier.indexMap[copier.subspaceMap[0]][i]];
		    }
		}
	    }
	    else{
		for(int i=0;i<copier.shape[copier.subspaceMap[0]];i++){
		    for(int j=0;j<(copier.totalCnt/copier.unitCnt);j++){
			receiver.objectArray[receiverOffset+receiver.indexMap[receiver.subspaceMap[0]][i]] = copier.objectArray[copierOffset+copier.indexMap[copier.subspaceMap[0]][i]];
		    }
		}
	    }
	}
	else{
	    for(int i=0;i<copier.subspaceShape[depth];i++){
		int[] newCoordinates = new int[coordinates.length];
		for(int j=0;j<newCoordinates.length; j++){
		    if(j==depth)
			newCoordinates[j] = i;
		    else
			newCoordinates[j] = coordinates[j];
		}
		copyRecurse(copier, receiver, depth-1, newCoordinates);
	    }
	}
    }


    public boolean concatCompat(Object dimension, PersistentMatrix mat1, PersistentMatrix mat2){
	Integer dim;
	if(Util.isInteger(dimension)){
	    dim = (Integer)dimension;
	}
	else{
	    dim = mat1.aLabelToIndex(dimension);
	}
	if(mat1.ALabels == null || mat2.ALabels == null){ // one or both doesn't have labels, do it by the numbers
	    for(int i=0; i<mat1.space; i++){
		if(i  != dim && mat1.shape[i] != mat2.shape[i]){ // something's wrong
		    return false;
		}
	    }
	    for(int i=0; i<mat1.ELabels[dim].count();i++){
		if(((PersistentHashMap)mat2.ELabels[dim]).containsKey(((PersistentHashMap)mat1.ELabels[dim]).get(i))){
		    return false;
		}
	    }// This looks incorrect ... looking at labels even though in here you know one or both don't have labels.  It shouldn't show false regardless though.
	}
	else{ // do it by the labels
	    Set keyset = ((PersistentHashMap)mat1.ALabels).keySet();
	    Iterator iterator = keyset.iterator();
	    Object curkey;
	    for(int i=0; iterator.hasNext(); i++){
		curkey = iterator.next();
		if(((PersistentHashMap)mat1.ALabels).get(curkey) != dim && (!((PersistentHashMap)mat2.ALabels).containsKey(curkey) || mat1.shape[(Integer)((PersistentHashMap)mat1.ALabels).get(curkey)] != mat2.shape[(Integer)((PersistentHashMap)mat2.ALabels).get(curkey)])){
		    return false;
		}
	    }
	    IPersistentMap mat2ELabels = mat2.ELabels[(Integer)((PersistentHashMap)mat2.ALabels).get(mat1.indexToALabel(dim))];
	    for(int i=0; i<mat1.ELabels[dim].count();i++){
		if(((PersistentHashMap)mat2.ELabels[dim]).containsKey(((PersistentHashMap)mat1.ELabels[dim]).get(i))){
		    return false;
		}
	    }
	}
	return true;
    }

    public boolean sameBacking(PersistentMatrix mat1, PersistentMatrix mat2){
	if(mat1.booleanArray == mat2.booleanArray &&
	   mat1.doubleArray == mat2.doubleArray &&
	   mat1.integerArray == mat2.integerArray &&
	   mat1.objectArray == mat2.objectArray){
	    return true;
	}
	else{
	    return false;
	}
    }

//     public PersistentMatrix concat(Object dimension, PersistentMatrix mat1, PersistentMatrix mat2){

// 	if(concatCompat(dimension, mat1, mat2)){
// 	    if(Util.isInteger(dimension)){
// 		dim = (Integer)dimension;
// 	    }
// 	    else{
// 		dim = mat1.aLabelToIndex(dimension);
// 	    }

// 	    if(sameBacking(mat1, mat2)){
// 		IPersistentMap[] newELabels = mat1.ELabels.clone();
// 		IPersistentMap singleELabel;
// 	    }
// 	    else{
// 	    }
// 	}

//     }


    public int count(){ // doesn't work on 1D matrices
	if(subspace == 0){
	    return 1;
	}
	else{
	    return subspaceShape[subspace-1];
	}
    }

}


