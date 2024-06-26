package latis.data

import latis.model.*
import latis.util.DefaultDomainOrdering

/**
 * Represents the data values that make up the domain of a SampledFunction.
 * This is used explicitly for a SetFunction.
 */
trait DomainSet {

  /**
   * Provide the data type for the variables represented by this DomainSet.
   */
  def model: DataType

  //TODO: implement ordering for DomainSets
  //Note, can't simply use model unless we know if set is cartesian
  def ordering: Option[PartialOrdering[DomainData]] = None

  /**
   * Return the number of dimensions covered by this DomainSet.
   */
  def rank: Int = shape.length //assumes Cartesian
  //TODO: arity?

  /**
   * Return an Array with the length of each dimension of this DomainSet.
   */
  def shape: Array[Int] = Array(length) //1D, non-Cartesian
  /*
   * TODO: consider Cartesian vs other topologies:
   *   e.g. 2D manifold in 3D space: rank 3, shape size 2
   */

  /**
   * Returns the DomainData elements of this DomainSet
   * based on its ordering. Multi-dimensional DomainSets
   * will vary slowest in its first dimension.
   * This returns an IndexedSeq to ensure that this
   * collection of elements can be rapidly accessed by Index
   * and is presumably strict (not lazy or tied to side effects).
   */
  def elements: IndexedSeq[DomainData]

  /**
   * Return the number of elements in this DomainSet.
   */
  def length: Int = elements.length

  /**
   * Return the minimum extent of the coverage of this DomainSet.
   */
  def min: DomainData = elements(0)

  /**
   * Return the maximum extent of the coverage of this DomainSet.
   */
  def max: DomainData = elements(length - 1)

  //TODO: first, last, take,...?

  /**
   * Optionally return the DomainData element at the given index.
   * If the index is out of bounds, None will be returned.
   */
  def apply(index: Int): Option[DomainData] =
    if (isDefinedAt(index)) Option(elements(index))
    else None

  /**
   * Is this DomainSet defined at the given index.
   */
  def isDefinedAt(index: Int): Boolean =
    (index >= 0) && (index < length)

  /**
   * Return the index of the given DomainData element.
   * If it does not exist, return -1.
   */
  def indexOf(data: DomainData): Int = elements.indexOf(data)
  //TODO: "search" to return SearchResult, InsertionPoint
  //TODO: "contains" for exact match?

  /**
   * Does the coverage of this DomainSet include the given element.
   * Note that this does not guarantee a *matching* element.
   */
  def covers(data: DomainData): Boolean = {
    // Note that PartialOrdering is sufficient for gteq and lt
    val ord: PartialOrdering[DomainData] =
      ordering.getOrElse(DefaultDomainOrdering)
    ord.gteq(data, min) && ord.lt(data, max)
  }

  //TODO: min/max may rule out many sets, e.g. polygon

  /**
   * Defines the string representation of a DomainSet as the
   * model it represents.
   */
  override def toString: String = model.toString
}
