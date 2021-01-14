package latis.input

import java.net.URI

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalactic.Equality

import latis.data.Data.DoubleValue
import latis.data.Data.FloatValue
import latis.data.DomainData
import latis.data.RangeData
import latis.data.Sample
import latis.dataset.Dataset
import latis.input.fdml.FdmlReader

class DatabaseAdapterSuite extends BaseDatasetSuite {
  lazy val fdmlFile = "src/test/resources/DbAdapterTest.fdml"
  lazy val expectedFirstSample: Sample =
    Sample(
      DomainData(9999999L),
      RangeData("expected_message", "expected_filename")
    )
}

/**
 * Applies custom equality to the Sample type for testing
 * TODO: define sample equality in latis core
 */
trait SampleEquality extends Equality[Sample] {
  override def areEqual(actualSample: Sample, expected: Any): Boolean = {
    expected match {
      case expectedSample: Sample =>
        val samples = actualSample.domain.zip(expectedSample.domain) ++ actualSample.range.zip(expectedSample.range)
        samples.forall {
          // explicitly check for NaNs, returns true if both are NaNs
          case (a: DoubleValue, b: DoubleValue) => (a.value.isNaN && b.value.isNaN) || a == b
          case (a: FloatValue, b: FloatValue) => (a.value.isNaN && b.value.isNaN) || a == b
          case (a, b) => a == b
        }
      case _ => false
    }
  }
}

abstract class BaseDatasetSuite extends AnyFunSuite with Matchers {
  def fdmlFile: String
  def expectedFirstSample: Sample
  implicit def sampleEq: SampleEquality = new SampleEquality {}

  test(s"First sample from ${fdmlFile} should match the expected first sample") {
    val uri: URI = new URI(fdmlFile)
    val ds: Dataset = FdmlReader.read(uri, validate = true)
    val actualFirstSample = ds.samples.take(1).compile.last.unsafeRunSync().getOrElse {
      fail("Empty Dataset")
    }
    actualFirstSample should equal (expectedFirstSample)
  }
}
