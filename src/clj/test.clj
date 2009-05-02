(ns matrix
  (:import
   (java.util Arrays)
   (persistentmatrix.core PersistentMatrix)))


(defn test-conj []
  (let
      [m (.reshape (PersistentMatrix/create (int-array [1]) (range 1000) true) (int-array [100 10]))
       single-lastd (PersistentMatrix/create (int-array [1]) (range 100) true)
       single-firstd (PersistentMatrix/create (int-array [1]) (range 10) true)]
    (if
	(do
	  (conj m m)
	  (conj m single-lastd)
	  (conj (conj (.slice m 0 1 2) (.slice m 0 5 6)) (.slice m 0 2 3)))
      (prn "conjing ok")
      (prn "conjing failed"))))
(defn test-ndim-units []
  (let
      [m (PersistentMatrix/create (int-array [2]) (repeat 1000 '(1 2)) true)]
    (if
	(and
	 (= (count m) 1000)
	 (= (nth (nth m 99) 1) 2))
      (prn "ndim-units ok")
      (prn "ndim-units failed"))))

(defn test-constructors [] 
  (let 
      [m-from-list (PersistentMatrix/create (int-array [1]) (vector
							     (vector (range 1000))
							     (vector (range 1000))
							     (vector (range 1000))) true)
       m-from-seq (PersistentMatrix/create (int-array [1]) (list
							    (range 1000)
							    (range 1000)
							    (range 1000)) true)
       nested-array (let
		      [array (make-array Integer/TYPE 3 1)]
		    (aset array 0 (into-array Integer/TYPE (range 1000)))
		    (aset array 1 (into-array Integer/TYPE (range 1000)))
		    (aset array 2 (into-array Integer/TYPE (range 1000)))
		    array)
       m-from-array (PersistentMatrix/create (int-array [1]) nested-array true)]
    (if
	(and 
	 (= (nth (nth m-from-list 0) 9) 9)
	 (= (nth (nth m-from-list 1) 99) 99)
	 (= (nth (nth m-from-list 2) 999) 999))
      (prn "m-from-list ok")
      (prn "m-from-list failed"))
    (if
	(and 
	 (= (nth (nth m-from-seq 0) 9) 9)
	 (= (nth (nth m-from-seq 1) 99) 99)
	 (= (nth (nth m-from-seq 2) 999) 999))
      (prn "m-from-seq ok")
      (prn "m-from-seq failed"))
    (if
	(and 
	 (= (nth (nth m-from-array 0) 9) 9)
	 (= (nth (nth m-from-array 1) 99) 99)
	 (= (nth (nth m-from-array 2) 999) 999))
      (prn "m-from-array ok")
      (prn "m-from-array failed"))))

(defn test-reshaping []
  (let
      [m (PersistentMatrix/create (int-array [1]) (range 1000) true)
       m-reshaped (.reshape m (int-array [100 10]))]
    (if
	(= (nth (nth m-reshaped 9) 9) 909)
      (prn "reshaping ok")
      (prn "reshaping failed"))))
(defn test-labels []
  (let 
      [m (.reshape (PersistentMatrix/create (int-array [1]) (range 27) true) (int-array [3 3 3]))
       m-labels (.addLabels m [:person :language :years] [[:adler :greg :per] [:python :lisp :c] [:1 :2 :3]])
       m-labels2 (.addLabels m [:person :language :years] [[:adler :greg :per] [:python :lisp :c] [:1 :2 :3]])
       m-labels3 (.addLabels (.reshape (PersistentMatrix/create (int-array [1]) (range 9) true) (int-array [3 3]))
			     [:person :language :years] [[:adler :greg :per] [:python :lisp :c] [:4]])]
    (if (= {:1 0} (aget (.getELabels (.slice m-labels 2 0 1)) 2))
      (prn "ELabels ok")
      (prn "ELabels failed"))
    (if (Arrays/equals (.getELabels (conj m-labels m-labels2)) (.getELabels m-labels))
      (prn "consed e-labels ok")
      (prn "consed e-labels failed"))
    (if (= (.getALabels (conj m-labels m-labels2)) (.getALabels m-labels))
      (prn "consed a-labels ok")
      (prn "consed a-labels failed"))
    (if (= (.getALabels (conj m-labels m-labels3)) (.getALabels m-labels))
      (prn "consed with singleton alabels ok")
      (prn "consed with singleton alabels failed"))
    (if (= {:4 3 :1 0 :3 2 :2 1} (aget (.getELabels (conj m-labels m-labels3)) 2))
      (prn "consed with singleton elabels ok")
      (prn "consed with singleton elabels failes"))))


(do  
  (test-reshaping)
  (test-constructors)
  (test-ndim-units)
  (test-conj)
  (test-labels))
