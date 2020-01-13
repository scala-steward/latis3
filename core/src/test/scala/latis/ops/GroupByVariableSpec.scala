package latis.ops

import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import latis.data._
import latis.dataset._
import latis.metadata.Metadata
import latis.model._
import latis.output.TextWriter

class GroupByVariableSpec extends FlatSpec {

  // (x, y) -> a
  val model = Function(
    Tuple(
      Scalar(Metadata("id" -> "x", "type" -> "int")),
      Scalar(Metadata("id" -> "y", "type" -> "int"))
    ),
    Scalar(Metadata("id" -> "a", "type" -> "int"))
  )

  val data = SeqFunction(Seq(
    Sample(DomainData(0, 10), RangeData(1)),
    Sample(DomainData(0, 11), RangeData(2)),
    Sample(DomainData(1, 10), RangeData(3)),
    Sample(DomainData(1, 11), RangeData(4)),
  ))

  val ds = new MemoizedDataset(Metadata("test"), model, data)
      .withOperation(GroupByVariable("y"))
      .unsafeForce()

  //TextWriter().write(ds)

  "GroupByVariable" should "unProject the grouped variables" in {
    ds.model.toString should be ("y -> x -> a")
  }
}
