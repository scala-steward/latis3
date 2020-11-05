package latis.ops

import cats.syntax.all._

import latis.data._
import latis.model._
import latis.util.Identifier
import latis.util.LatisException

/**
 * Operation to project only a given set of variables in a Dataset.
 * Domain variables will be included, for now.
 */
case class Projection(ids: Identifier*) extends MapOperation {
  //TODO: support nested Functions
  //TODO: support aliases, hasName
  //TODO: support dot notation for nested tuples
  //TODO: Index place holders for non-projected domain variables

  override def applyToModel(model: DataType): Either[LatisException, DataType] =
    applyToVariable(model).toRight(LatisException("Nothing projected"))

  /** Recursive method to apply the projection. */
  private def applyToVariable(v: DataType): Option[DataType] = v match {
    case s: Scalar =>
      if (s.id.exists(id => ids.contains(id))) Some(s) else None
    case Tuple(vars @ _*) =>
      val vs = vars.flatMap(applyToVariable)
      vs.length match {
        case 0 => None // drop empty Tuple
        case 1 => Some(vs.head) // reduce Tuple of one
        case _ => Some(Tuple(vs))
      }
    case Function(d, r) =>
      (d, applyToVariable(r)) match {
        case (d, Some(r)) => Some(Function(d, r))
        case _ => None
      }
  }

  override def mapFunction(model: DataType): Sample => Sample = {
    // Get the indices of the projected variables in the Sample.
    // Sort since the FDM requires original order of variables.
    // TODO: should we allow range to be reordered?
    val rangeIndices: Seq[Int]  = ids.map(model.getPath).flatMap {
      case Some(RangePosition(i) :: Nil) => Some(i)
      case Some(_) => None
      case None => ??? //error, invalid vname, catch earlier
    }.sorted

    (sample: Sample) => sample match {
      case Sample(ds, rs) =>
        val range = rangeIndices.map(rs(_))
        Sample(ds, range)
    }
  }

}

object Projection {

  def apply(exp: String): Projection = {
    val ids = exp.split(",").map { id =>
      Identifier.fromString(id).getOrElse {
        throw LatisException(s"'$id' is not a valid identifier")
      }
    }
    Projection(ids.toIndexedSeq: _*)
  }

  def fromArgs(args: List[String]): Either[LatisException, Projection] = args match {
    case Nil => Left(LatisException("Projection requires at least one argument"))
    case _ => args.traverse { id =>
      Identifier.fromString(id).toRight(LatisException(s"'$id' is not a valid identifier"))
    }.map(Projection(_: _*))
  }

}
