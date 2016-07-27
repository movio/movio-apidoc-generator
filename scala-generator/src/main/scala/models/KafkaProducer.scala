package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService}
import generator.ServiceFileNames
import scala.generator.ScalaUtil
import play.api.libs.json.JsString

object KafkaProducer extends CodeGenerator {
  import CaseClassUtil._
  import KafkaUtil._

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

    val header = addHeader match {
      case false => ""
      case true => ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val kafkaModels = getKafkaModels(ssd)

    // Return list of files
    kafkaModels.map{ model =>
      val kafkaClassName = model.name
      val kafkaProps = getKafkaProps(model.model).get
      val className = getModelByName(kafkaProps.dataType, ssd).get.name
      val configPath = ssd.namespaces.base.split("\\.").toSeq.dropRight(1).mkString(".")
      val source = s""" $header
import kafka.producer._
import kafka.serializer.StringEncoder

import play.api.libs.json.Json
import play.api.libs.json.Writes

import java.util.Properties
import com.typesafe.config.Config

import movio.api.kafka_0_8.KafkaProducer
import movio.api.kafka_0_8.KafkaProducerException
import movio.core.utils.TryHelpers.TryOps

package ${ssd.namespaces.base}.kafka {
  import ${ssd.namespaces.base}.models._
  import ${ssd.namespaces.base}.models.json._

  object ${kafkaClassName}Producer {
    val base = "${configPath}.kafka.producer"
    val BrokerListKey = s"$$base.broker-connection-string"
    val TopicInstanceKey = s"$$base.topic-instance"
  }

  class ${kafkaClassName}Producer(
    config: Config
  ) extends KafkaProducer[${kafkaClassName}, ${className}] {
    import ${kafkaClassName}Producer._

    lazy val topicResolver = ${kafkaClassName}Topic.topic(config.getString(TopicInstanceKey))(_)

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

    def send(single: ${className}, tenant: String): scala.util.Try[${className}] = {
      send(Seq(single), tenant).map(_.head)
    }

    def sendWrapped(single: ${kafkaClassName}, tenant: String): scala.util.Try[${kafkaClassName}] = {
      sendWrapped(Seq(single), tenant).map(_.head)
    }

    def send(batch: Seq[${className}], tenant: String): scala.util.Try[Seq[${className}]] = {
      val topic = topicResolver(tenant)
      val messages = batch.map(${kafkaClassName}(_))
      scala.util.Try {
        producer.send(messages map { message =>
                        new KeyedMessage[String, String](topic, message.generateKey(tenant), Json.stringify(Json.toJson(message)))
                      }: _*)
        batch
      } recoverWith {
        case ex => scala.util.Failure(new KafkaProducerException(s"Failed to publish $$topic message, to kafka queue.", ex))
      }
    }

    def sendWrapped(batch: Seq[${kafkaClassName}], tenant: String): scala.util.Try[Seq[${kafkaClassName}]] = {
      val topic = topicResolver(tenant)
      scala.util.Try {
        producer.send(batch map { message =>
                        new KeyedMessage[String, String](topic, message.generateKey(tenant), Json.stringify(Json.toJson(message)))
                      }: _*)
        batch
      } recoverWith {
        case ex => scala.util.Failure(new KafkaProducerException(s"Failed to publish $$topic message, to kafka queue.", ex))
      }
    }

    def shutdown() = producer.close()
  }

}
"""

      ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, s"${kafkaClassName}Producer", source, Some("Scala"))
    }
  }
}
