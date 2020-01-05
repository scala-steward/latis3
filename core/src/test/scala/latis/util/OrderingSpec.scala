package latis.util

import scala.collection.mutable

import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import latis.data._
import latis.metadata.Metadata
import latis.model.Function
import latis.model.Scalar
import latis.model.Tuple
import latis.time.Time

class OrderingSpec extends FlatSpec {

  val model = Function(
    Tuple(
      Scalar(Metadata("id" -> "x", "type" -> "int")),
      Time(Metadata("id" -> "time", "type" -> "string", "units" -> "MM/dd/yyyy"))
    ),
    Scalar(Metadata("id" -> "x", "type" -> "int"))
  )

  val samples = List(
    Sample(DomainData(0, "01/01/2001"), RangeData(2)),
    Sample(DomainData(1, "01/01/2001"), RangeData(4)),
    Sample(DomainData(1, "02/01/2000"), RangeData(3)),
    Sample(DomainData(0, "02/01/2000"), RangeData(1)),
  )

  "2D samples with time" should "be sortable" in {

    val totalOrdering = LatisOrdering.partialToTotal(LatisOrdering.sampleOrdering(model))

    samples.sorted(totalOrdering).map {
      case Sample(_, RangeData(Integer(x))) => x
    } should be (List(1,2,3,4))
  }

  it should "go into a SortedMap ordered by keys" in {
    val ordering = LatisOrdering.partialToTotal(LatisOrdering.domainOrdering(model.domain.getScalars))

    val smap = mutable.SortedMap[DomainData, RangeData]()(ordering)
    samples.foreach {
      case Sample(dd, rd) => smap += (dd -> rd)
    }
    smap.map {
      case Sample(_, RangeData(Integer(x))) => x
    } should be (List(1,2,3,4))
  }
}