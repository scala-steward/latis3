package latis.dataset

import latis.data._
import latis.metadata._
import latis.model._
import latis.util.LatisException

/**
 * Defines a Dataset as a computational function.
 * The function type is described as a DataType
 * to facilitate composition with Datasets.
 * The function could be enabled by the evaluation
 * of a SampledFunction or it could be a pure function.
 */
case class DatasetFunction(
  metadata: Metadata,
  model: DataType,
  function: TupleData => Either[LatisException, TupleData]
) extends MetadataLike {

  def apply(data: TupleData): Either[LatisException, TupleData] =
    function(data)

  /**
   * Uses double arrow (=>) to represent computational function.
   */
  override def toString: String = model match {
    case Function(domain, range) => s"$id: $domain => $range"
  }
}
