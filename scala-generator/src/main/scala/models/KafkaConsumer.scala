package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService}
import generator.ServiceFileNames
import scala.generator.MovioCaseClasses
import play.api.libs.json.JsString

object KafkaConsumer extends CodeGenerator {

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
      model.model.attributes.exists(attr =>
        attr.name == MovioCaseClasses.KafkaClassKey
      )
    )

    // Return list of files
    models.map{ model =>
      val className = model.name
      val configPath = ssd.namespaces.base.split("\\.").toSeq.dropRight(1).mkString(".")
      val topicFn = (model.model.attributes.find(attr => attr.name == MovioCaseClasses.KafkaClassKey) map {attr: Attribute =>
             (attr.value \ MovioCaseClasses.KafkaTopicKey).as[JsString].value
         }).get
      val apiVersion = ssd.namespaces.last
      val topicRegex = topicFn.
        replace("${apiVersion}", apiVersion).
        replace("$apiVersion", apiVersion).
        replace("${tenant}",""). 
        replace("$tenant","") +
        """ + "(.*)""""
      val source = s"""$header
import java.util.Properties

import scala.language.postfixOps
import scala.util.{ Try, Success, Failure }
import scala.annotation.tailrec

import com.typesafe.config.Config

import kafka.consumer._
import kafka.message.MessageAndMetadata
import kafka.serializer.StringDecoder

import play.api.libs.json.Json

package ${ssd.namespaces.base}.kafka {
  import ${ssd.namespaces.base}.models._
  import ${ssd.namespaces.base}.models.json._

  object ${className}Topic {
    /**
      The version of the api - apidoc generator enforces this value.
      For use when creating a topic name.
      Example: "v2"
      */
    val apiVersion = "${apiVersion}"

    /**
      The name of the kafka topic to publish and consume messages from.
      This is a scala statedment/code that that gets executed
      Example: `s"mc-servicename-$${apiVersion}-$${tenant}"` 

      @param tenant is the customer id, eg vc_regalus
      */
    def topic(tenant: String) = ${topicFn}

    val topicRegex = ${topicRegex}
  }

  case class KafkaProducerException(message: String, ex: Throwable)
      extends RuntimeException(message, ex)

  object ${className}Consumer {
    val base = "${configPath}.kafka.consumer"
    val KafkaOffsetStorageType = s"$$base.offset-storage-type"
    val KafkaOffsetStorageDualCommit = s"$$base.offset-storage-dual-commit"
    val ConsumerTimeoutKey = s"$$base.timeout.ms"
    val ConsumerZookeeperConnectionKey = s"$$base.zookeeper.connection"
  }

  class ${className}Consumer (
    config: Config,
    consumerGroupId: String
  ) extends {
    import ${className}Consumer._

    val topicFilter = new Whitelist(${className}Topic.topicRegex)

    lazy val consumerConfig = new ConsumerConfig(readConsumerPropertiesFromConfig)
    lazy val consumer = Consumer.create(consumerConfig)

    lazy val stream: KafkaStream[String, String] =
      consumer.createMessageStreamsByFilter(topicFilter, 1, new StringDecoder, new StringDecoder).head

    lazy val iterator = stream.iterator()

    def readConsumerPropertiesFromConfig = {
      val properties = new Properties

      properties.put("group.id", consumerGroupId)
      properties.put("zookeeper.connect", config.getString(ConsumerZookeeperConnectionKey))
      properties.put("auto.offset.reset", "smallest")
      properties.put("consumer.timeout.ms", config.getString(ConsumerTimeoutKey))
      properties.put("consumer.timeout", config.getString(ConsumerTimeoutKey))
      properties.put("auto.commit.enable", "false")

      properties.put("offsets.storage", config.getString(KafkaOffsetStorageType))
      properties.put("dual.commit.enabled", config.getString(KafkaOffsetStorageDualCommit))

      properties
    }

    def processBatchThenCommit(
      processor: Map[String, Seq[${className}]] ⇒ Try[Map[String, Seq[${className}]]],
      batchSize: Int = 1
    ): Try[Map[String, Seq[${className}]]] = {
      @tailrec
      def fetchBatch(remainingInBatch: Int, messages: Map[String, Seq[${className}]]): Try[Map[String, Seq[${className}]]] ={
        if (remainingInBatch == 0) {
          Success(messages)
        } else {
          // FIXME test
          Try {
            iterator.next()
          } match {
            case Success(message) =>
              val entity = Json.parse(message.message).as[${className}]
              val ${className}Topic.topicRegex.r(tenant) = message.topic

              val newSeq = messages.get(tenant).getOrElse(Seq.empty) :+ entity
              val newMessages = messages + (tenant -> newSeq)

              fetchBatch(remainingInBatch - 1, newMessages)
            case Failure(ex) => ex match {
              case ex: ConsumerTimeoutException ⇒
                // Consumer timed out waiting for a message. Ending batch.
                Success(messages)
              case ex =>
                Failure(ex)
            }
          }
        }
      }

      fetchBatch(batchSize, Map.empty) match {
        case Success(messages) =>
          processor(messages) map { allMessages =>
            consumer.commitOffsets(true)
            allMessages
          }
        case Failure(ex) => Failure(ex)
      }
    }

    def shutdown = { consumer.shutdown }
  }
}

"""
      ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, s"${className}Consumer", source, Some("Scala"))
    }
  }
}
