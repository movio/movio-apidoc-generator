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

  val KafkaPropsKey = "kafka_props"
  val KafkaMessageKey = "message_key"
  val KafkaTopicKey = "topic"
  val KafkaTypeKey = "data_type"

  def isKafkaClass(model: ScalaModel): Boolean = model.attribute(KafkaPropsKey).isDefined

  def getKafkaModels(ssd: ScalaService): Seq[ScalaModel] = ssd.models.filter(isKafkaClass(_))

  def getKafkaClass(payload: ScalaModel, ssd: ScalaService): Option[ScalaModel] = {
    getKafkaModels(ssd).filter(getKafkaProps(_) match {
      case Some(attr) ⇒ attr.dataType == payload.originalName
      case None       ⇒ false
    }).headOption
  }

  def getKafkaProps(model: ScalaModel): Option[KafkaProps] = {
    model.attribute(KafkaPropsKey).map(attr ⇒ attr.value.as[KafkaProps])
  }

  def getConsumerClassName(payload: ScalaModel): String = s"Kafka${payload.name}Consumer"

  def getPayloadFieldName(kafkaClass: ScalaModel): String = {
    val payload = getKafkaProps(kafkaClass).get.dataType
    kafkaClass.fields.filter(_.`type`.name == payload).head.name
  }

}

case class KafkaProps(
  dataType: String,
  topic: String,
  messageKey: String = "java.util.UUID.randomUUID().toString"
)
object KafkaProps {
  implicit def kafkaModelAttributeFmt: Reads[KafkaProps] = {
    (
      (__ \ "data_type").read[String] and
      (__ \ "topic").read[String] and
      (__ \ "message_key").read[String]
    )(KafkaProps.apply _)
  }
}
