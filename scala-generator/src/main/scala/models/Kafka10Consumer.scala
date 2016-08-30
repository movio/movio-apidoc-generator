package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService}
import scala.util.matching.Regex
import generator.ServiceFileNames
import play.api.libs.json.JsString

object Kafka10Consumer extends CodeGenerator {
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

    val header = if (addHeader) ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
                 else ""

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
import scala.util.{ Try, Success }

import com.typesafe.config.Config

import play.api.libs.json.Json

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.TopicPartition

import movio.api.kafka_0_10.Consumer

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
    val base = "test.apidoc.apidoctest.kafka.consumer"
    val BootstrapServers = s"$$base.bootstrap.servers"
    val TopicInstanceKey = s"$$base.topic.instance"
    val TenantsKey = s"$$base.tenants"
    val PollTimeoutKey = s"$$base.poll.timeout" // ms
  }

  class ${className}Consumer (
    config: Config,
    consumerGroupId: String,
    tenants: Option[Seq[String]] = None
  ) extends Consumer[${className}] {
    import ${className}Consumer._

    val pollMillis = config.getLong(PollTimeoutKey)

    lazy val topicRegex: Regex =
      ${className}Topic.topicRegex(
        config.getString(TopicInstanceKey),
        tenants.getOrElse(config.getStringList(TenantsKey))
      ).r

    lazy val kafkaConsumer = new KafkaConsumer[String, String](readConsumerPropertiesFromConfig)
    kafkaConsumer.subscribe(topicRegex.pattern, new ConsumerRebalanceListener {
      def onPartitionsRevoked(partitions: java.util.Collection[TopicPartition]) = {}
      def onPartitionsAssigned(partitions: java.util.Collection[TopicPartition]) = {}
    })

    def readConsumerPropertiesFromConfig = {
      val properties = new Properties
      properties.put("bootstrap.servers", config.getString(BootstrapServers))
      properties.put("group.id", consumerGroupId)
      properties.put("auto.offset.reset", "earliest")
      properties.put("auto.commit.enable", "false")
      properties.put("key.deserializer", classOf[StringDeserializer].getName)
      properties.put("value.deserializer", classOf[StringDeserializer].getName)

      properties
    }

    /**
      * Process a batch of messages with given processor function and commit
      * offsets if it succeeds. Messages with null payloads are ignored.
      *
      * @param processor processor function that takes a map of messages for different tenants
      * @param batchSize the maximum number of messages to process
      */
    def processBatchThenCommit(
      processor: Map[String, Seq[${className}]] ⇒ Try[Map[String, Seq[${className}]]],
      batchSize: Int = 1
    ): Try[Map[String, Seq[${className}]]] =
      doProcess[${className}] { record ⇒
        Option(record.value).map(Json.parse(_).as[KafkaMember])
      }(processor, batchSize)

    /**
      * Process a batch of messages with given processor function and commit
      * offsets if it succeeds.
      *
      * Each message is a tuple of the key and the payload deserialised to
      * `Option[T]` which is `None` when the message has a null payload.
      *
      * @param processor processor function that takes a map of messages for different tenants
      * @param batchSize the maximum number of messages to process
      */
    def processBatchWithKeysThenCommit(
      processor: Map[String, Seq[(String, Option[${className}])]] ⇒ Try[Map[String, Seq[(String, Option[${className}])]]],
      batchSize: Int = 1
    ): Try[Map[String, Seq[(String, Option[${className}])]]] =
      doProcess[(String,  Option[${className}])] { record ⇒
        Some(
          record.key → Option(record.value).map(Json.parse(_).as[KafkaMember])
        )
      }(processor, batchSize)

    def doProcess[T](
      converter: ConsumerRecord[String, String] ⇒ Option[T]
    )(
      processor: Map[String, Seq[T]] ⇒ Try[Map[String, Seq[T]]],
      batchSize: Int = 1
    ): Try[Map[String, Seq[T]]] = {
      val batch = Try {
        import scala.collection.JavaConverters._

        kafkaConsumer.poll(pollMillis).toSeq.flatMap { r ⇒
          val topic = r.topic
          val value = r.value
          val topicRegex(tenant) = r.topic
          converter(r).map(t ⇒ (tenant, t))
        }.groupBy(_._1).mapValues(_.map(_._2))
      }

      for {
        records          ← batch
        processedRecords ← processor(records)
      } yield {
        kafkaConsumer.commitSync()
        processedRecords
      }
    }
  }
}

"""
      ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, s"${className}Consumer", source, Some("Scala"))
    }
  }
}
