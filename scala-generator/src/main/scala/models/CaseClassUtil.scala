package scala.models

import scala.generator.{ ScalaModel, ScalaService, ScalaField }
import scala.generator.ScalaUtil
import play.api.libs.json.__
import play.api.libs.json.Reads
import play.api.libs.functional.syntax._
import AdvancedCaseClasses._
import lib.Datatype._
import lib.Text._

object CaseClassUtil {

  def getInstanceName(model: ScalaModel, id: Int): String = {
    val name = model.originalName + "_entity"
    ScalaUtil.toVariable(name) + id
  }

  def getInstanceBatchName(model: ScalaModel): String =
    ScalaUtil.toVariable(model.originalName + "_entities")

  def getModelByName(specName: String, ssd: ScalaService): Option[ScalaModel] =
    ssd.models.filter(_.originalName == specName).headOption

  def getScalaProps(scalaModel: ScalaModel): Option[ScalaModelProps] =
    scalaModel.attribute(ScalaPropsKey).map(_.value.as[ScalaModelProps])

  def getScalaProps(scalaField: ScalaField): Option[ScalaFieldProps] =
    scalaField.attribute(ScalaPropsKey).map(_.value.as[ScalaFieldProps])

  def getModelFromOriginalName(name: String, ssd: ScalaService): Option[ScalaModel] =
    ssd.models.find(model => model.originalName == name)

  def generateInstance(model: ScalaModel, id: Int, ssd: ScalaService): String = {
  // def createEntity(model: ScalaModel, number: Int, models: Seq[ScalaModel]): String = {
    // def getModelForField(name: String): ScalaModel = {
    //   models.find(model ⇒ model.originalName == name).get
    // }

    val fields = model.fields.map { field ⇒
      val defaultValue =
        getScalaProps(field) match {
        // field.field.attributes.find(_.name == AdvancedCaseClasses.ScalaPropsKey) match {
          case Some(props) ⇒
            props.example match {
            // (attr.value \ AdvancedCaseClasses.ScalaExampleKey).toOption match {
              case Some(example) ⇒ example
              case None ⇒
                props.default match {
                // (attr.value \ AdvancedCaseClasses.ScalaDefaultKey).toOption match {
                  case Some(default) ⇒ default
                  case None ⇒
                    if (field.required)
                      throw new RuntimeException("manditory fields must provide examples")
                    else
                      "None"
                }
            }

          case None ⇒
            field.`type` match {
              case t: Primitive ⇒ t match {
                case Primitive.Boolean         ⇒ true
                case Primitive.Double          ⇒ 2.0 + id
                case Primitive.Integer         ⇒ 21 + id
                case Primitive.Long            ⇒ 101L + id
                case Primitive.DateIso8601     ⇒ "new org.joda.time.Date()"
                case Primitive.DateTimeIso8601 ⇒ "new org.joda.time.DateTime()"
                case Primitive.Decimal         ⇒ 1.31 + id
                case Primitive.Object          ⇒ ""
                case Primitive.String          ⇒ s""""${field.name}${id}""""
                case Primitive.Unit            ⇒ ""
                case Primitive.Uuid            ⇒ "new java.util.UUID()"
                case _                         ⇒ "???"
              }
              case t: Container ⇒ t match {
                case Container.List(name)   ⇒ "List.empty"
                case Container.Map(name)    ⇒ "Map.empty"
                case Container.Option(name) ⇒ "None"
              }
              case t: UserDefined ⇒ t match {
                case UserDefined.Model(name) ⇒ generateInstance(getModelFromOriginalName(name, ssd).get, id, ssd).indent(2)
                case UserDefined.Enum(name)  ⇒ "???" // TBC
                case UserDefined.Union(name) ⇒ "???" // TBC
              }
              case e ⇒ "???"
            }
        }

      s"""${field.name} = $defaultValue"""
    }.mkString("", ",\n", "").indent(2)
    s"""
${model.name} (
$fields
)
"""
  }
}

case class ScalaModelProps(
  `extends`: Option[Seq[String]],
  tbc: Option[String]
)
object ScalaModelProps {
  implicit def scalaModelPropsFmt: Reads[ScalaModelProps] = {
    (
      (__ \ ScalaExtendsKey).readNullable[Seq[String]] and
      (__ \ "tbc").readNullable[String]
    )(ScalaModelProps.apply _)
  }
}

case class ScalaFieldProps(
  `class`: Option[String],
  default: Option[String],
  example: Option[String]
)
object ScalaFieldProps {
  implicit def kafkaModelAttributeFmt: Reads[ScalaFieldProps] = {
    (
      (__ \ ScalaClassKey).readNullable[String] and
      (__ \ ScalaDefaultKey).readNullable[String] and
      (__ \ ScalaExampleKey).readNullable[String]
    )(ScalaFieldProps.apply _)
  }
}
