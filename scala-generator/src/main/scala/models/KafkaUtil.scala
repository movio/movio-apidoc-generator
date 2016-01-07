package scala.models

import com.bryzek.apidoc.spec.v0.models._
import com.bryzek.apidoc.spec.v0.models.json._
import scala.generator.{ ScalaModel, ScalaService }
import scala.generator.ScalaUtil._
import play.api.libs.json.__
import play.api.libs.json.Reads
import play.api.libs.functional.syntax._

object KafkaUtil {
  import CaseClassUtil._

  val KafkaClassKey = "kafka_class"
  val KafkaMessageKey = "message_key"
  val KafkaTopicKey = "topic"
  val KafkaTypeKey = "data_type"

  def isKafkaClass(model: ScalaModel): Boolean = model.attribute(KafkaClassKey).isDefined

  def getKafkaModels(ssd: ScalaService): Seq[ScalaModel] = ssd.models.filter(isKafkaClass(_))

  def getKafkaClass(payload: ScalaModel, ssd: ScalaService): Option[ScalaModel] = {
    getKafkaModels(ssd).filter(getKafkaAttribute(_) match {
      case Some(attr) ⇒ attr.dataType == payload.originalName
      case None       ⇒ false
    }).headOption
  }

  def getKafkaAttribute(model: ScalaModel): Option[KafkaModelAttribute] = {
    model.attribute(KafkaClassKey).map(attr ⇒ attr.value.as[KafkaModelAttribute])
  }

  def getConsumerClassName(payload: ScalaModel): String = s"Kafka${payload.name}Consumer"

  def getPayloadFieldName(kafkaClass: ScalaModel): String = {
    val payload = getKafkaAttribute(kafkaClass).get.dataType
    kafkaClass.fields.filter(_.`type`.name == payload).head.name
  }

}

case class KafkaModelAttribute(
  dataType: String,
  messageKey: String,
  topic: String
)
object KafkaModelAttribute {
  implicit def kafkaModelAttributeFmt: Reads[KafkaModelAttribute] = {
    (
      (__ \ "data_type").read[String] and
      (__ \ "message_key").read[String] and
      (__ \ "topic").read[String]
    )(KafkaModelAttribute.apply _)
  }
}
