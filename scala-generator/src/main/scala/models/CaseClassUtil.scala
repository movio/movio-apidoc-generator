package scala.models

import scala.generator.{ ScalaModel, ScalaService, ScalaField }
import scala.generator.ScalaUtil
import play.api.libs.json.__
import play.api.libs.json.Reads
import play.api.libs.functional.syntax._
import AdvancedCaseClasses._

object CaseClassUtil {

  def getInstanceName(model: ScalaModel, id: Int): String = {
    val name = model.originalName + "_entity"
    ScalaUtil.toVariable(name) + id
  }

  def getInstanceBatchName(model: ScalaModel): String =
    ScalaUtil.toVariable(model.originalName + "_entities")

  def generateInstance(model: ScalaModel, id: Int): String = ???

  def getModelByName(specName: String, ssd: ScalaService): Option[ScalaModel] =
    ssd.models.filter(_.originalName == specName).headOption

  def getScalaProps(scalaModel: ScalaModel): Option[ScalaModelProps] =
    scalaModel.attribute(ScalaPropsKey).map(_.value.as[ScalaModelProps])

  def getScalaProps(scalaField: ScalaField): Option[ScalaFieldProps] =
    scalaField.attribute(ScalaPropsKey).map(_.value.as[ScalaFieldProps])

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
