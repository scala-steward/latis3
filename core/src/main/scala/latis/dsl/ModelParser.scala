package latis.dsl

import atto.*
import atto.Atto.*
import cats.syntax.all.*

import latis.model.*
import latis.util.LatisException
import latis.util.dap2.parser.parsers.identifier

/**
 * Defines a parser that turns a functional data model expression
 * into a DataType.
 */
object ModelParser {
  private val defaultScalarType: ValueType = IntValueType

  def parse(exp: String): Either[LatisException, DataType] =
    phrase(dataType).parse(exp).done.either.leftMap { s =>
      val msg = s"Failed to parse model expression: $exp. $s"
      LatisException(msg)
    }

  def unsafeParse(exp: String): DataType =
    ModelParser.parse(exp).fold(throw _, identity)

  def apply(exp: String): Either[LatisException, DataType] =
    ModelParser.parse(exp)

  //private def variable: Parser[String] =
  //  sepBy1(identifier, char('.')).map(_.toList.mkString("."))

  private def dataType: Parser[DataType] =
    function | tuple | scalar

  /** Doesn't support nested functions in the domain. */
  def function: Parser[DataType] =
    parens(functionWithoutParens) | functionWithoutParens

  /** Doesn't support nested functions in the domain. */
  private def functionWithoutParens: Parser[DataType] = for {
    d <- (tuple | scalar).token
    _ <- string("->").token
    r <- dataType.token
  } yield Function.from(d, r).fold(throw _, identity)

  //TODO: support functions in tuples
  def tuple: Parser[DataType] = for {
    //l <- parens(sepBy(dataType.token, char(',').token)) //stack overflow
    l <- parens(sepBy((scalar | tuple).token, char(',').token))
  } yield Tuple.fromSeq(l).fold(throw _, identity)

  def scalar: Parser[DataType] =
    scalarWithType | scalarWithoutType

  private def scalarWithType: Parser[DataType] = for {
    id <- identifier.token
    _ <- string(":").token
    t <- valueType.token
  } yield Scalar(id, ValueType.fromName(t).fold(throw _, identity))

  private def scalarWithoutType: Parser[DataType] = for {
    id <- identifier.token
  } yield Scalar(id, defaultScalarType)

  private def valueType: Parser[String] =
    stringCI("boolean") |
      stringCI("byte") |
      stringCI("char") |
      stringCI("short") |
      stringCI("int") |
      stringCI("long") |
      stringCI("float") |
      stringCI("double") |
      stringCI("binary") |
      stringCI("string") |
      stringCI("bigint") |
      stringCI("bigdecimal")
}
