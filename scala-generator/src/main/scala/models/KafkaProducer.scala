package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService}
import generator.ServiceFileNames
import scala.generator.MovioCaseClasses

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
        attr.name == MovioCaseClasses.KafkaKey
      ).size > 0
    )

    // Return list of files
    models.map{ model =>
      val className = model.name
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

  class ${className}Producer(config: Config) {

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

    def send(single: ${className}, tenant: String): Try[Unit] = {
      send(Seq(single), tenant)
    }

    def send(batch: Seq[${className}], tenant: String): Try[Unit] = {
      val topic = ${className}Topic.topic(tenant)
      Try {
        producer.send(batch map { message =>
                        new KeyedMessage[String, String](topic, message.key, Json.stringify(Json.toJson(message)))
                      }: _*)
      } andThen {
        case Failure(ex) ⇒
          throw new KafkaProducerException(s"Failed to publish $$topic message to kafka queue.", ex)
      }
    }
  }

}
"""

      ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, s"${className}Producer", source, Some("Scala"))
    }
  }
}
