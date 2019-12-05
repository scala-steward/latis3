package latis.input

import java.net.URI

import scala.collection.immutable.Map
import scala.xml._

import cats.implicits._

import latis.dataset.Dataset
import latis.metadata.Metadata
import latis.model._
import latis.util.FdmlUtils
import latis.util.NetUtils
import latis.util.ReflectionUtils

/**
 * From an FDML file an FdmlReader reader creates a dataset, configures its adapter, and builds the dataset's model.
 */
class FdmlReader(xml: Elem) extends AdaptedDatasetReader {

  // Defines global metadata
  val datasetName: String = (xml \ "@id").text
  override def metadata: Metadata =
    Metadata("id" -> datasetName)

  // Defines the location of the dataset as a URI
  val locationNode: NodeSeq = xml \ "source"
  val locationUri: String = (locationNode \ "@uri").text
  def uri: URI = new URI(locationUri)

  // Defines the Adapter for reading the data
  val adapterNode: NodeSeq = xml \ "adapter"
  def adapter: Adapter = createAdapter(adapterNode, model)

  // Defines the model of the dataset
  val functionNode: NodeSeq = xml \ "function"
  def model: DataType = createModel(functionNode).get

  /**
   * This method is used by the DatasetReader ServiceLoader to determine
   * if this can read a dataset with the given URI. This is based on the
   * dataset type, not IO errors.
   */
  override def read(uri: URI): Option[Dataset] =
    // If the extension is "fdml" then try to load it
    if (uri.getPath.endsWith(".fdml")) Some(FdmlReader(uri).getDataset)
    else None

  /**
   * Recursively parse the function XML element into a domain and range.
   * The createDataType function introduces recursion because it calls
   * functions that can also call createDataType.
   */
  def createModel(
    functionNode: NodeSeq
  ): Option[DataType] =
    if (functionNode.length > 0) {
      createFunctionDataType(functionNode)
    } else {
      None
    }

  /**
   * The concept of the datatype exists in the fdml.xsd schema.
   */
  def isDatatype(node: Node): Boolean =
    node.label == "function" ||
      node.label == "scalar" ||
      node.label == "tuple"

  /**
   * Create a DataType object from XML.
   * Create domain and range objects with the same code.
   */
  def createDataType(datatype: Node): DataType =
    if (datatype.label == "function") createFunctionDataType(datatype).get
    else if (datatype.label == "scalar") createScalarDataType(datatype).get
    else createTupleDataType(datatype).get

  /**
   * Create an Adapter object from XML and the supplied model.
   */
  def createAdapter(adapterNode: NodeSeq, model: DataType): Adapter = {
    val properties: Seq[(String, String)] = getAttributes(adapterNode).toSeq
    val config = AdapterConfig(properties: _*)
    ReflectionUtils
      .callMethodOnCompanionObject(
        "latis.input.AdapterFactory",
        "makeAdapter",
        model,
        config
      )
      .asInstanceOf[Adapter]
  }

  ///**
  // * Create a sequence of operations from XML that are to be applied to the dataset.
  // */
  //def createOperations(operationNodes: NodeSeq): Seq[UnaryOperation] = {
  //  val operations: Seq[UnaryOperation] = for {
  //    node <- operationNodes \ "_" // wildcard, get all top nodes
  //    operation <- createOperation(node)
  //  } yield operation
  //  operations
  //}

  ///**
  // * For a specified XML operation node, return a unary operation.
  // * ToDo: reflection might be more elegant, but for now pattern matching will suffice
  // */
  //def createOperation(node: Node): Option[UnaryOperation] = {
  //  node.label match {
  //    case "contains" => contains(node)
  //    case "groupby"  => groupBy(node)
  //    case "head"     => head(node)
  //    //case "pivot"     => pivot(node)
  //    case "project" => project(node)
  //    case "rename"  => rename(node)
  //    case "select"  => select(node)
  //    case "take"    => take(node)
  //    case "uncurry" => uncurry(node)
  //    case _         => None
  //  }
  //}

  /**
   * Function datatypes must contain a domain and a range.
   */
  def createFunctionDataType(functionNodes: NodeSeq): Option[Function] =
    if (functionNodes.length > 0) {
      val functionNode: Node = functionNodes.head
      val attributes: Map[String, String] = getAttributes(functionNodes)
      val datatypes: Seq[Node] = functionNode.child.filter(isDatatype)
      val domain: Node = datatypes.head
      val range: Node = datatypes.tail.head
      if (attributes.isEmpty) {
        Some(Function(createDataType(domain), createDataType(range)))
      } else {
        Some(
          Function(
            Metadata(attributes),
            createDataType(domain),
            createDataType(range)
          )
        )
      }
    } else {
      None
    }

  /**
   * Scalar datatypes only contain metadata.
   */
  def createScalarDataType(scalarNode: Node): Option[Scalar] =
    if (scalarNode.nonEmpty) {
      val (classMap, mdMap) = getAttributes(scalarNode).partition(_._1 == "class")
      val md = Metadata(mdMap)
      if (classMap.nonEmpty) {
        val s = ReflectionUtils.callMethodOnCompanionObject(
          classMap("class"),
          "apply",
          md
        )
        Option(s.asInstanceOf[Scalar])
      } else Option(Scalar(md))
    } else {
      None
    }

  /**
   * Tuple datatypes can contain an arbitrary number of elements.
   */
  def createTupleDataType(tupleNode: Node): Option[Tuple] = {
    val attributes: Map[String, String] = getAttributes(tupleNode)
    val tupleElements: Seq[Node] = tupleNode.child.filter(isDatatype)
    val tuple: List[DataType] = tupleElements.map(createDataType).toList
    if (tuple.nonEmpty) {
      if (attributes.isEmpty) {
        Some(Tuple(tuple: _*))
      } else {
        Some(Tuple(Metadata(attributes), tuple: _*))
      }
    } else {
      None
    }
  }

  /**
   * Get the value of this element's attribute with the given name.
   */
  def getAttribute(xml: NodeSeq, name: String): Option[String] =
    (xml \ ("@" + name)).text match {
      case s: String if s.length > 0 => Some(s)
      case _                         => None
    }

  /**
   * Get all of the attributes for the specified XML Node.
   */
  def getAttributes(xml: NodeSeq): Map[String, String] = {
    val node: Node = xml.head
    val seq: Iterable[(String, String)] = for {
      att <- node.attributes
    } yield (att.key, att.value.text)
    seq.toMap
  }

//  /*
//   * BEGIN parsing of specific operations
//   */
//
//  def contains(node: Node): Option[UnaryOperation] = {
//    for {
//      vname <- Option(node \ "vname").filter(_.nonEmpty)
//      values <- Option(node \ "value").filter(_.nonEmpty)
//    } yield Contains(vname.text, values.map(_.text))
//  }
//
//  def groupBy(node: Node): Option[UnaryOperation] = {
//    for {
//      vnames <- Option(node \ "vname").filter(_.nonEmpty)
//    } yield GroupBy(vnames.map(_.text): _*)
//  }
//
//  /**
//   * Head operation not yet implemented.
//   */
//  def head(node: Node): Option[UnaryOperation] = {
//    None
//  }
//
////  def pivot(node: Node): Option[UnaryOperation] = {
////    for {
////      values  <- Option(node \ "value").filter(_.nonEmpty)
////      vids <- Option(node \ "vid").filter(_.nonEmpty)
////    } yield Pivot(values.map(_.text), vids.map(_.text))
////  }
//
//  def project(node: Node): Option[UnaryOperation] = {
//    for {
//      vids <- Option(node \ "vid").filter(_.nonEmpty)
//    } yield Projection(vids.map(_.text): _*)
//  }
//
//  /**
//   * Rename operation not yet implemented.
//   */
//  def rename(node: Node): Option[UnaryOperation] = {
//    val vName: String = (node \ "vname").text
//    val newName = (node \ "newName").text
//    //todo: implement Rename operation
//    //Some(Rename(vName, newName))
//    None
//  }
//
//  def select(node: Node): Option[UnaryOperation] = {
//    for {
//      vname <- Option(node \ "vname").filter(_.nonEmpty)
//      operator <- Option(node \ "operator").filter(_.nonEmpty)
//      value <- Option(node \ "value").filter(_.nonEmpty)
//    } yield Selection(vname.text, operator.text, value.text)
//  }
//
//  /**
//   * Take operation not yet implemented.
//   */
//  def take(node: Node): Option[UnaryOperation] = {
//    None
//  }
//
//  def uncurry(node: Node): Option[UnaryOperation] = {
//    Some(Uncurry())
//  }

}

object FdmlReader {

  def apply(xmlText: String): FdmlReader =
    new FdmlReader(XML.loadString(xmlText))

  def apply(uri: URI, validate: Boolean = false): FdmlReader = {
    if (validate) FdmlUtils.validateFdml(uri).leftMap(throw _)
    val xml: String = NetUtils.readUriIntoString(uri) match {
      case Right(x) => x
      case Left(e)  => throw e
    }
    FdmlReader(xml)
  }

}
