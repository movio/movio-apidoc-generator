package scala.models

import com.bryzek.apidoc.spec.v0.models._
import com.bryzek.apidoc.spec.v0.models.json._
import scala.generator.{ ScalaModel, ScalaService }
import scala.generator.ScalaUtil._
import play.api.libs.json.__
import play.api.libs.json.Reads
import play.api.libs.functional.syntax._
import movio.apidoc.generator.attributes.v0.models._
import movio.apidoc.generator.attributes.v0.models.json._

import HasAttributes.ops._
import HasAttributesI._

object KafkaUtil {
  import CaseClassUtil._

  val KafkaPropsKey = "kafka_props"

  def isKafkaClass(model: ScalaModel): Boolean = model.model.findAttribute(KafkaPropsKey).isDefined

  def getKafkaModels(ssd: ScalaService): Seq[ScalaModel] = ssd.models.filter(isKafkaClass(_))

  def getKafkaClass(payload: ScalaModel, ssd: ScalaService): Option[ScalaModel] = {
    getKafkaModels(ssd).filter(getKafkaProps(_) match {
      case Some(attr) ⇒ attr.dataType == payload.originalName
      case None       ⇒ false
    }).headOption
  }

  def getKafkaProps(model: ScalaModel): Option[KafkaProps] = {
    model.model.findAttribute(KafkaPropsKey).map(attr ⇒ attr.value.as[KafkaProps])
  }

  def getConsumerClassName(payload: ScalaModel): String = s"Kafka${payload.name}Consumer"

  def getPayloadFieldName(kafkaClass: ScalaModel): String = {
    val payload = getKafkaProps(kafkaClass).get.dataType
    kafkaClass.fields.filter(_.`type`.name == payload).head.name
  }

}
