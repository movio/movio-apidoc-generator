package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService}
import generator.ServiceFileNames

object KafkaConsumer extends CodeGenerator {

  val KafkaClassAttribute = "kafka_class"

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

    val classes = ssd.models.filter(model =>
      model.model.attributes.exists(attr =>
        attr.name == KafkaClassAttribute
      )
    )

    // Return list of files
    classes.map{ clazz =>
      val className = clazz.name
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
  import ${ssd.namespaces.base}.kafka.models._
  import ${ssd.namespaces.base}.kafka.models.json._

  object ${className}Consumer {
    val base = s"$${${className}Topic.base}.consumer"
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

    val topicFilter = new Whitelist(${className}Topic.regex)

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
      processor: Seq[${className}] ⇒ Try[Seq[${className}]],
      batchSize: Int = 1
    ): Try[Seq[${className}]] = {
      @tailrec
      def fetchBatch(remainingInBatch: Int, messages: Seq[${className}]): Try[Seq[${className}]] ={
        if (remainingInBatch == 0) {
          messages
        } else {
          // FIXME test
          Try {
            Json.parse(iterator.next().message).as[${className}]
          } match {
            case Success(message) =>
              fetchBatch(remainingInBatch - 1, messages :+ message)
            case Failure(ex) => ex match {
              case ex: ConsumerTimeoutException ⇒
                // Consumer timed out waiting for a message. Ending batch.
                messages
              case ex =>
                Failure(ex)
            }
          }
        }
      }

      fetchBatch(batchSize, Seq.empty) match {
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
