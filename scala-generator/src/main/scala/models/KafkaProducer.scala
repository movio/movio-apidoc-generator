package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService}
import generator.ServiceFileNames
import scala.generator.MovioCaseClasses
import scala.generator.ScalaUtil
import play.api.libs.json.JsString

object KafkaProducer extends CodeGenerator {

  override def invoke(
    form: InvocationForm
  ): Either[Seq[String], Seq[File]] = {
    Right(generateCode(form))
  }

  def generateCode(
    form: InvocationForm,
    addHeader: Boolean = true
  ): Seq[File] = {
    val ssd = ScalaService(form.service)

    val prefix = underscoreAndDashToInitCap(ssd.name)
    val enumJson: String = ssd.enums.map { ScalaEnums(ssd, _).buildJson() }.mkString("\n\n")
    val play2Json = Play2Json(ssd).generate()

    val header = addHeader match {
      case false => ""
      case true => ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val models = ssd.models.filter(model =>
      model.model.attributes.filter(attr =>
        attr.name == MovioCaseClasses.KafkaClassKey
      ).size > 0
    )

    // Return list of files
    models.map{ model =>
      val kafkaClassName = model.name
      val className = ScalaUtil.toClassName((
        model.model.attributes.find(attr => attr.name == MovioCaseClasses.KafkaClassKey) map {attr: Attribute =>
          (attr.value \ MovioCaseClasses.KafkaTypeKey).as[JsString].value
        }).get)
      val configPath = ssd.namespaces.base.split("\\.").toSeq.dropRight(1).mkString(".")
      val source = s""" $header
import kafka.producer._
import kafka.serializer.StringEncoder

import play.api.libs.json.Json
import play.api.libs.json.Writes

import movio.core.utils.TryHelpers.TryOps

import java.util.Properties
import com.typesafe.config.Config

import scala.util.{ Failure, Try }

package ${ssd.namespaces.base}.kafka {
  import ${ssd.namespaces.base}.models._
  import ${ssd.namespaces.base}.models.json._

  class ${kafkaClassName}Producer(config: Config) {

    val BrokerListKey = s"${configPath}.kafka.producer.broker-connection-string"

    lazy val producerConfig = new ProducerConfig(readProducerPropertiesFromConfig(config))
    lazy val producer = new Producer[String, String](producerConfig)


    def readProducerPropertiesFromConfig(config: Config) = {
      val properties = new Properties
      properties.put("producer.type", "sync")
      properties.put("metadata.broker.list", config.getString(BrokerListKey))
      properties.put("request.required.acks", "-1")
      properties.put("serializer.class", classOf[StringEncoder].getName)
      properties
    }

    def send(single: ${className}, tenant: String): Try[${className}] = {
      send(Seq(single), tenant).map(_.head)
    }

    def sendWrapped(single: ${kafkaClassName}, tenant: String): Try[${kafkaClassName}] = {
      sendWrapped(Seq(single), tenant).map(_.head)
    }

    def send(batch: Seq[${className}], tenant: String): Try[Seq[${className}]] = {
      val topic = ${kafkaClassName}Topic.topic(tenant)
      val messages = batch.map(${kafkaClassName}(_))
      Try {
        producer.send(messages map { message =>
                        new KeyedMessage[String, String](topic, message.key, Json.stringify(Json.toJson(message)))
                      }: _*)
        batch
      } andThen {
        case Failure(ex) ⇒
          throw new KafkaProducerException(s"Failed to publish $$topic message, to kafka queue.", ex)
      }
    }

    def sendWrapped(batch: Seq[${kafkaClassName}], tenant: String): Try[Seq[${kafkaClassName}]] = {
      val topic = ${kafkaClassName}Topic.topic(tenant)
      Try {
        producer.send(batch map { message =>
                        new KeyedMessage[String, String](topic, message.key, Json.stringify(Json.toJson(message)))
                      }: _*)
        batch
      } andThen {
        case Failure(ex) ⇒
          throw new KafkaProducerException(s"Failed to publish $$topic message, to kafka queue.", ex)
      }
    }
  }

}
"""

      ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, s"${kafkaClassName}Producer", source, Some("Scala"))
    }
  }
}
