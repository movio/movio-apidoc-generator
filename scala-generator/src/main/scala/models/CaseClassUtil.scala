package scala.models

import scala.generator.{ ScalaModel, ScalaService, ScalaField, ScalaEnum }
import scala.generator.ScalaUtil
import play.api.libs.json.__
import play.api.libs.json.Reads
import play.api.libs.functional.syntax._
import AdvancedCaseClasses._
import lib.Datatype._
import lib.Text._
import movio.apidoc.generator.attributes.v0.models._
import movio.apidoc.generator.attributes.v0.models.json._

import HasAttributes.ops._
import HasAttributesI._
import com.bryzek.apidoc.spec.v0.models._

object CaseClassUtil {
  val ScalaModelPropsKey = "scala_model_props"
  val ScalaFieldPropsKey = "scala_field_props"
  val FieldValidationKey = "field_validation"

  def getInstanceName(model: ScalaModel, id: Int): String = {
    val name = model.originalName + "_entity"
    ScalaUtil.toVariable(name) + id
  }

  def getInstanceBatchName(model: ScalaModel): String =
    ScalaUtil.toVariable(model.originalName + "_entities")

  def getModelByName(specName: String, ssd: ScalaService): Option[ScalaModel] =
    ssd.models.filter(_.originalName == specName).headOption


  def getScalaProps(model: Model): Option[ScalaModelProps] =
    model.findAttribute(ScalaModelPropsKey).map(_.value.as[ScalaModelProps])

  def getScalaProps(field: Field): Option[ScalaFieldProps] =
    field.findAttribute(ScalaFieldPropsKey).map(_.value.as[ScalaFieldProps])

  def getFieldValidation(field: Field): Option[FieldValidation] =
    field.findAttribute(FieldValidationKey).map(_.value.as[FieldValidation])

  def getModelFromOriginalName(name: String, ssd: ScalaService): Option[ScalaModel] =
    ssd.models.find(model => model.originalName == name)

  def generateInstance(model: ScalaModel, id: Int, ssd: ScalaService): String = {
    val fields = model.fields.map { field ⇒
      val defaultValue =
        getScalaProps(field.field) match {
          case Some(props) ⇒
            props.example match {
              case Some(example) ⇒ example
              case None ⇒
                props.default match {
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
                case Primitive.Double          ⇒ 0.1 + id
                case Primitive.Integer         ⇒ 1 + id
                case Primitive.Long            ⇒ 2L + id
                case Primitive.DateIso8601     ⇒ "new org.joda.time.Date()"
                case Primitive.DateTimeIso8601 ⇒ "new org.joda.time.DateTime()"
                case Primitive.Decimal         ⇒ 0.31 + id
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
                case UserDefined.Enum(name)  ⇒ generateEnum(name, ssd)
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

  def generateEnum(name: String, ssd: ScalaService): String = {
    val result = ssd.enums.find(_.enum.name == name) map (enum => {
      val enumValue = enum.values(0).name
      s"${enum.name}.${enumValue}"
      }
    )
    result.getOrElse("")
  }
}
