package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService}
import generator.ServiceFileNames
import scala.generator.ScalaUtil
import play.api.libs.json.JsString

object Kafka10Producer extends CodeGenerator {
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
import scala.util.{ Try, Failure }
import scala.collection.JavaConversions._

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

import play.api.libs.json.Json
import play.api.libs.json.Writes

import java.util.Properties
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueType

import movio.api.kafka_0_10.Producer
import movio.api.kafka_0_10.KafkaProducerException
import movio.core.utils.TryHelpers.TryOps

package ${ssd.namespaces.base}.kafka {
  import ${ssd.namespaces.base}.models._
  import ${ssd.namespaces.base}.models.json._

  object ${kafkaClassName}Producer {
    val base = "${configPath}.kafka.producer"
    val BootstrapServers = s"$$base.bootstrap.servers"
    val TopicInstanceKey = s"$$base.topic.instance"
    val PropertiesKey = s"$$base.properties"
  }

  class ${kafkaClassName}Producer(
    config: Config
  ) extends Producer[${kafkaClassName}, ${className}] {
    import ${kafkaClassName}Producer._

    lazy val topicResolver = ${kafkaClassName}Topic.topic(config.getString(TopicInstanceKey))(_)

    lazy val kafkaProducer = new KafkaProducer[String, String](readProducerPropertiesFromConfig(config))

    def readProducerPropertiesFromConfig(config: Config) = {
      val properties = new Properties
      properties.put("acks", "all")
      properties.put("linger.ms", "500")

      if (config.hasPath(PropertiesKey)) {
        config.getConfig(PropertiesKey)
          .entrySet
          .filter { _.getValue.valueType == ConfigValueType.STRING }
          .foreach { e ⇒ properties.put(e.getKey, e.getValue.unwrapped) }
      }

      properties.put("bootstrap.servers", config.getString(BootstrapServers))
      properties.put("key.serializer", classOf[StringSerializer].getName)
      properties.put("value.serializer", classOf[StringSerializer].getName)
      properties
    }

    def send(single: ${className}, tenant: String): Try[${className}] = {
      send(Seq(single), tenant).map(_.head)
    }

    def sendWrapped(single: ${kafkaClassName}, tenant: String): Try[${kafkaClassName}] = {
      sendWrapped(Seq(single), tenant).map(_.head)
    }

    def send(batch: Seq[${className}], tenant: String): Try[Seq[${className}]] = {
      val topic = topicResolver(tenant)
      val messages = batch.map(${kafkaClassName}(_))
      Try {
        messages foreach { record ⇒
          kafkaProducer.send(
            new ProducerRecord[String, String](topic, record.generateKey(tenant), Json.stringify(Json.toJson(record)))
          )
        }
        kafkaProducer.flush()
        batch
      } recoverWith {
        case ex ⇒ Failure(new KafkaProducerException(s"Failed to publish $$topic message, to kafka queue.", ex))
      }
    }

    def sendWrapped(batch: Seq[${kafkaClassName}], tenant: String): Try[Seq[${kafkaClassName}]] = {
      val topic = topicResolver(tenant)
      Try {
        batch foreach { record ⇒
          kafkaProducer.send(
            new ProducerRecord[String, String](topic, record.generateKey(tenant), Json.stringify(Json.toJson(record)))
          )
        }
        kafkaProducer.flush()
        batch
      } recoverWith {
        case ex ⇒ Failure(new KafkaProducerException(s"Failed to publish $$topic message, to kafka queue.", ex))
      }
    }
  }
}
"""

      ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, s"${kafkaClassName}Producer", source, Some("Scala"))
    }
  }
}
