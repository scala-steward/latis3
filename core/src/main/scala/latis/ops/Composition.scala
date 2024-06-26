package latis.ops

import cats.syntax.all.*

import latis.data.Data
import latis.data.DomainData
import latis.dataset.ComputationalDataset
import latis.dataset.Dataset
import latis.dataset.MemoizedDataset
import latis.model.DataType
import latis.model.Function
import latis.util.LatisException

case class Composition(dataset: Dataset) extends MapRangeOperation {

  override def applyToModel(model: DataType): Either[LatisException, DataType] = {
    //TODO: make sure dataset range matches compFunction domain
    val domain = model match {
      case Function(d, _) => Right(d)
      case _ => Left(LatisException("Model must be a function"))
    }
    val range = dataset.model match {
      case Function(_, r) => Right(r)
      case _ => Left(LatisException("Composed dataset must be a function"))
      // TODO: check this in a smart constructor
    }
    (domain, range).mapN(Function.from(_, _).fold(throw _, identity))
  }

  override def mapFunction(model: DataType): Data => Data = {
    val f: Data => Either[LatisException, Data] = (data: Data) =>
      dataset match {
        case ComputationalDataset(_, _, f) => f(data)
        case mds: MemoizedDataset => DomainData.fromData(data).flatMap { dd =>
          mds.data.eval(dd).map(Data.fromSeq(_))
        }
        case _ => throw LatisException("Invalid dataset for composition")
    }
    (input: Data) =>
      f(input) match {
        case Right(x) => x
        case Left(le) => throw le
      }
  }
}
