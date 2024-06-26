package latis.ops

import cats.syntax.all.*

import latis.data.DomainData
import latis.data.Sample
import latis.model.DataType
import latis.model.Function
import latis.model.Tuple
import latis.util.LatisException

/**
 * Curry a Dataset by taking the first variable of a multi-dimensional domain
 * and making it the new domain with the rest represented as a nested Function.
 *
 * e.g. curry(1): (a, b) -> c  =>  a -> b -> c
 *
 * The effect is the restructuring of Samples such that the primary (outer)
 * Function has one Sample per curried variable value.
 *
 * Note that this will be a no-op for Datasets that already have arity one.
 * Assume no named Tuples or nested Tuples in domain, for now.
 */
case class Curry(arity: Int = 1) extends GroupOperation {
  //TODO: arity vs dimension/rank for nested tuples in the domain, ignoring nesting for now
  //TODO: Provide description for prov
  //TODO: avoid adding prov if this is a no-op

  def groupByFunction(model: DataType): Sample => Option[DomainData] = {
    if (model.arity < arity) {
      val msg = "Curry can only reduce arity. Use Uncurry to increase arity."
      throw LatisException(msg)
    }
    (sample: Sample) => Some(sample.domain.take(arity)) //TODO: only Cartesian dataset can guarantee unique, order
  }

  // takes the model for the dataset and returns the domain of the curried dataset
  def domainType(model: DataType): DataType = model match {
    // Ignore nested tuples
    case Function(d, _) =>
      d.getScalars.take(arity) match { //TODO: fails for nested Tuples in domain
        case s1 :: Nil => s1
        case ss => Tuple.fromSeq(ss).fold(throw _, identity)
      }
    //case Function(Tuple(es @ _*), _) => Tuple(es.take(arity))
    case _ => throw LatisException("Invalid DataType for Curry")
  }

  def aggregation: Aggregation = {
    val mapOp = new MapOperation {
      override def mapFunction(model: DataType): Sample => Sample =
        (sample: Sample) => Sample(sample.domain.drop(arity), sample.range)

      // takes the model for the dataset and returns the range of the curried dataset
      override def applyToModel(model: DataType): Either[LatisException, DataType] = model match {
        // Ignore nested tuples
        case Function(d, range) =>
          (d.getScalars.drop(arity) match { //TODO: fails for nested Tuples in domain
            case Nil => range  // this happens when the arity is not changed
            case s1 :: Nil => Function.from(s1, range).fold(throw _, identity)
            case ss => Function.from(Tuple.fromSeq(ss).fold(throw _, identity), range).fold(throw _, identity)
          }).asRight
        case _ => Left(LatisException("Model must be a function"))
        //case Function(Tuple(es @ _*), range) => Function(Tuple(es.drop(arity)), range)
          //TODO: beef up edge cases
      }
    }

    DefaultAggregation().compose(mapOp)
  }

}

object Curry {

  def builder: OperationBuilder = (args: List[String]) => fromArgs(args)

  def fromArgs(args: List[String]): Either[LatisException, Curry] = args match {
    case arity :: Nil => Either.catchOnly[NumberFormatException](Curry(arity.toInt))
      .leftMap(LatisException(_))
    case Nil => Right(Curry())
    case _ => Left(LatisException("Too many arguments to Curry"))
  }
}
