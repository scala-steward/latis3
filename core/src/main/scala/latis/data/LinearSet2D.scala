package latis.data

import latis.metadata.Metadata
import latis.model._

/**
 * Define a two-dimensional Cartesian DomainSet with regularly spaced elements.
 * This is implemented as a product of two LinearSet1Ds.
 */
class LinearSet2D(set1: LinearSet1D, set2: LinearSet1D, val model: DataType = LinearSet2D.defaultModel)
  extends DomainSet
  with Serializable {
  //TODO: ProductSet, could be used for any set of 1D sets

  override def length: Int = set1.length * set2.length

  override def min: DomainData = set1.min ++ set2.min
  override def max: DomainData = set1.max ++ set2.max

  override def shape: Array[Int] = Array(set1.length, set2.length)

  def elements: IndexedSeq[DomainData] = for {
    dd1 <- set1.elements
    dd2 <- set2.elements
  } yield dd1 ++ dd2

  override def apply(index: Int): Option[DomainData] =
    if (isDefinedAt(index)) {
      val i1: Int = index / set2.length
      val i2: Int = index - (i1 * set2.length)
      for {
        dd1 <- set1(i1)
        dd2 <- set2(i2)
      } yield dd1 ++ dd2
    } else None

  override def indexOf(data: DomainData): Int = data match {
    case DomainData(d1, d2) =>
      val i1 = set1.indexOf(DomainData(d1))
      val i2 = set2.indexOf(DomainData(d2))
      if (set1.isDefinedAt(i1) && set2.isDefinedAt(i2))
        i1 * set2.length + i2
      else -1
    case _ => ??? //TODO: invalid arg, -1?
  }
}

object LinearSet2D {

  /**
   * Define the model of this DomainSet assuming double value types
   * and a 1-based "_#" naming scheme for variable identifiers.
   */
  def defaultModel: DataType = Tuple(
    Scalar(Metadata("id" -> "_1", "type" -> "double")),
    Scalar(Metadata("id" -> "_2", "type" -> "double"))
  )
}
