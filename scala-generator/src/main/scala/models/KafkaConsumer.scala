package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService}
import scala.util.matching.Regex
import generator.ServiceFileNames
import play.api.libs.json.JsString

object KafkaConsumer extends CodeGenerator {
  import CaseClassUtil._
  import KafkaUtil._

  override def invoke(
    form: InvocationForm
  ): Either[Seq[String], Seq[File]] = {
    Right(generateCode(form))
  }

  def generateTopicRegex(topicFn: String, apiVersion: String) = {
    val tenantVariable = Seq("${tenant}", "$tenant").map(Regex.quote(_)).mkString("|")
    //`tenantsPattern` is a val defined in the `topicRegex` function, see `source` below.
    topicFn.replaceAll(tenantVariable, Regex.quoteReplacement("($tenantsPattern)"))
  }

  def generateCode(
    form: InvocationForm,
    addHeader: Boolean = true
  ): Seq[File] = {
    val ssd = ScalaService(form.service)

    val prefix = underscoreAndDashToInitCap(ssd.name)
    val enumJson: String = ssd.enums.map { ScalaEnums(ssd, _).buildJson() }.mkString("\n\n")
    val play2Json = Play2JsonExtended(ssd).generate()

    val header = addHeader match {
      case false ⇒ ""
      case true  ⇒ ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val kafkaModels = getKafkaModels(ssd)

    // Return list of files
    kafkaModels.map{ model ⇒
      val className = model.name
      val configPath = ssd.namespaces.base.split("\\.").toSeq.dropRight(1).mkString(".")
      val kafkaProps = getKafkaProps(model.model).get
      val apiVersion = ssd.namespaces.last
      val topicFn = kafkaProps.topic
      val topicRegex = generateTopicRegex(topicFn, apiVersion)

      val source = s"""$header
import java.util.Properties

import scala.language.postfixOps
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.util.matching.Regex

import com.typesafe.config.Config

import kafka.consumer._
import kafka.message.MessageAndMetadata
import kafka.serializer.StringDecoder

import play.api.libs.json.Json

import movio.api.kafka_0_8.KafkaConsumer

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
      Example: `s"mc-servicename-$${apiVersion}-$${instance}-$${tenant}"`

      @param instance an instance of the topic, eg uat, prod. It's read from the config.
      @param tenant is the customer id, eg vc_regalus
      */
    def topic(instance: String)(tenant: String) = ${topicFn}

    /**
      The regex for the kafka consumer to match topics.

      @param instance an instance of the topic, eg uat, prod. It's read from the config.
      @param tenants the tenants of the topics from which the consumer consumes. If it's empty,
             all tenants are matched.
      */
    def topicRegex(inst: String, tenants: Seq[String]) = {
      val instance = Regex.quote(inst)
      val tenantsPattern = if (tenants.isEmpty) ".*"
                           else tenants.map(Regex.quote(_)).mkString("|")

      ${topicRegex}
    }
  }

  object ${className}Consumer {
    val base = "${configPath}.kafka.consumer"
    val KafkaOffsetStorageType = s"$$base.offset-storage-type"
    val KafkaOffsetStorageDualCommit = s"$$base.offset-storage-dual-commit"
    val ConsumerTimeoutKey = s"$$base.timeout.ms"
    val ConsumerZookeeperConnectionKey = s"$$base.zookeeper.connection"
    val TopicInstanceKey = s"$$base.topic-instance"
    val TenantsKey = s"$$base.tenants"
  }

  /**
    If you choose to override `topicRegex`, make sure the first group captures
    the tenant names.
   */
  class ${className}Consumer (
    config: Config,
    consumerGroupId: String,
    tenants: Option[Seq[String]] = None
  ) extends KafkaConsumer[${className}] {
    import ${className}Consumer._

    lazy val topicRegex: Regex =
      ${className}Topic.topicRegex(
        config.getString(TopicInstanceKey),
        tenants.getOrElse(config.getStringList(TenantsKey))
      ).r

    lazy val topicFilter = new Whitelist(topicRegex.toString)

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
      processor: Map[String, Seq[${className}]] ⇒ scala.util.Try[Map[String, Seq[${className}]]],
      batchSize: Int = 1
    ): scala.util.Try[Map[String, Seq[${className}]]] =
      doProcess[${className}] { message ⇒
        Option(message.message).map(Json.parse(_).as[${className}])
      }(processor, batchSize)

    def processBatchWithKeysThenCommit(
      processor: Map[String, Seq[(String, Option[${className}])]] ⇒ scala.util.Try[Map[String, Seq[(String, Option[${className}])]]],
      batchSize: Int = 1
    ): scala.util.Try[Map[String, Seq[(String, Option[${className}])]]] =
      doProcess[(String,  Option[${className}])] { message ⇒
        Some(
          message.key → Option(message.message).map(Json.parse(_).as[${className}])
        )
      }(processor, batchSize)

    def doProcess[T](
      converter: MessageAndMetadata[String, String] ⇒ Option[T]
    )(
      processor: Map[String, Seq[T]] ⇒ scala.util.Try[Map[String, Seq[T]]],
      batchSize: Int = 1
    ): scala.util.Try[Map[String, Seq[T]]] = {
      @tailrec
      def fetchBatch(remainingInBatch: Int, messages: Map[String, Seq[T]]): scala.util.Try[Map[String, Seq[T]]] ={
        if (remainingInBatch == 0) {
          scala.util.Success(messages)
        } else {
          // FIXME test
          scala.util.Try {
            iterator.next()
          } match {
            case scala.util.Success(message) ⇒
              val newMessages = converter(message) map { entity ⇒
                val topicRegex(tenant) = message.topic
                val newSeq = messages.get(tenant).getOrElse(Seq.empty) :+ entity

                messages + (tenant → newSeq)
              } getOrElse messages

              fetchBatch(remainingInBatch - 1, newMessages)
            case scala.util.Failure(ex) ⇒ ex match {
              case ex: ConsumerTimeoutException ⇒
                // Consumer timed out waiting for a message. Ending batch.
                scala.util.Success(messages)
              case ex ⇒
                scala.util.Failure(ex)
            }
          }
        }
      }

      fetchBatch(batchSize, Map.empty) match {
        case scala.util.Success(messages) ⇒
          processor(messages) map { allMessages ⇒
            consumer.commitOffsets(true)
            allMessages
          }
        case scala.util.Failure(ex) ⇒ scala.util.Failure(ex)
      }
    }

    def shutdown() = consumer.shutdown()
  }
}

"""
      ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, s"${className}Consumer", source, Some("Scala"))
    }
  }
}
