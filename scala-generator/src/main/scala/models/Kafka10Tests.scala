package scala.models

import com.bryzek.apidoc.generator.v0.models.{ File, InvocationForm }
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ ScalaEnums, ScalaCaseClasses, ScalaService, ScalaModel, ScalaField }
import generator.ServiceFileNames
import lib.Datatype._
import play.api.libs.json.JsString

object Kafka10Tests extends CodeGenerator {
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

    val prefix = underscoreAndDashToInitCap(ssd.name)
    val enumJson: String = ssd.enums.map { ScalaEnums(ssd, _).buildJson() }.mkString("\n\n")
    val play2Json = Play2JsonExtended(ssd).generate()

    val header = addHeader match {
      case false ⇒ ""
      case true  ⇒ ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val kafkaModels = getKafkaModels(ssd)

    // Return list of files
    kafkaModels.map { model ⇒
      val className = model.name
      val configPath = ssd.namespaces.base.split("\\.").toSeq.dropRight(1).mkString(".")
      val source = s""" $header
package ${ssd.namespaces.base}.kafka

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.util.{ Try, Success, Failure }

import org.joda.time.LocalDateTime
import org.mockito.Matchers.any
import org.mockito.Matchers.{ eq ⇒ is }

import com.typesafe.config.ConfigFactory

import movio.testtools.MovioSpec
import movio.testtools.kafka.{ KafkaTestKit, KafkaTestKitUtils }


class ${className}Tests extends MovioSpec with KafkaTestKit {
  import ${ssd.namespaces.base}.models._

  val kafkaServer = createKafkaServer()
  kafkaServer.startup()

  override def afterAll() = {
    zkServer.stop()
  }

  describe("${className} Producer and Consumer") {
    it("should timeout with no messages") {
      new Fixture {
        awaitCondition("Message should get processed") {
          def processor(messages: Map[String, Seq[${className}]]): Try[Map[String, Seq[${className}]]] =  Success(messages)
          consumer.processBatchThenCommit(processor) shouldBe Success(Map.empty)
        }
        consumer.close()
      }
    }

    it("should send and receive a message") {
      new Fixture {
        // Produce test message
        producer.sendWrapped(entity1, tenant).get
        producer.close()

        // And consume it
        awaitCondition("Message should get processed") {
          def processor(messages: Map[String, Seq[${className}]]): Try[Map[String, Seq[${className}]]] = {
            println("do some side effecting stuff here")
            Success(messages)
          }
          consumer.processBatchThenCommit(processor).get(tenant) shouldBe Seq(entity1)
        }
        consumer.close()
      }
    }

    it("should send and receive a batch of messages") {
      new Fixture {
        val entities = Seq(entity1, entity2)

        // Produce test message
        producer.sendWrapped(entities, tenant).get
        producer.close()

        // And consume it
        awaitCondition("Message should get processed") {
          def processor(messages: Map[String, Seq[${className}]]): Try[Map[String, Seq[${className}]]] =  {
            println("do some side effecting stuff here")
            Success(messages)
          }
          // Use distinct because there are items in the queue from other tests
          consumer.processBatchThenCommit(processor, 100).get(tenant) shouldBe entities
        }
        consumer.close()
      }
    }

    it("messages keys should be available to the processor") {
      new Fixture {
        val entities = Seq(entity1, entity2)

        // Produce test message
        producer.sendWrapped(entities, tenant).get
        producer.close()

        // And consume it
        awaitCondition("Message should get processed") {
          def processor(messages: Map[String, Seq[(String, Option[${className}])]]) = {
            println("do some side effecting stuff here")
            Success(messages)
          }

          // Use distinct because there are items in the queue from other tests
          consumer.processBatchWithKeysThenCommit(processor, 100).get(tenant) shouldBe Seq(
            key1 → Some(entity1),
            key2 → Some(entity2)
          )
        }
        consumer.close()
      }
    }

    it("consumer ignores null payload messages, to support deletes on topics with compaction") {
      new Fixture {
        val topic = ${className}Topic.topic(topicInstance)(tenant)
        val rawProducer = createProducer(kafkaServer)

        producer.sendWrapped(entity1, tenant).get
        // Produce null payload message. Need to use the raw producer because the generated producer would
        // throw an exception when trying to convert a null entity to JSON.
        import org.apache.kafka.clients.producer.ProducerRecord
        rawProducer.send(new ProducerRecord[String, String](topic, "anId", null)).get

        producer.sendWrapped(entity2, tenant).get
        producer.close()

        // And consume them
        var consumedEntities = Seq.empty[${className}]
        awaitCondition("All messages should get processed") {
          def processor(messages: Map[String, Seq[${className}]]): Try[Map[String, Seq[${className}]]] =  {
            println("do some side effecting stuff here")
            Success(messages)
          }

          // Use distinct because there are items in the queue from other tests
          consumer.processBatchThenCommit(processor, 100).get.get(tenant).foreach { messages =>
            consumedEntities ++= messages
          }

          consumedEntities shouldBe Seq(entity1, entity2)
        }
        consumer.close()
      }
    }

    it("should not commit offset if it fails to process a message") {
      val pollTimeout = 200
      // Setup offset topic consumer
      val offsetConsumer = createConsumer(kafkaServer, Map("enable.auto.commit" → "false"))
      offsetConsumer.subscribe(Seq("__consumer_offsets"))

      @tailrec
      def countRemainingOffsets(initialCount: Int = 0): Int = {
        Try {
          offsetConsumer.poll(pollTimeout)
        } match {
          case Success(msgs) if (msgs.count > 0) ⇒ countRemainingOffsets(initialCount + msgs.count)
          case Success(_)                        ⇒ initialCount
          case Failure(e)                        ⇒ throw e
        }
      }

      new Fixture {
        // Init the topic/offsets
        producer.sendWrapped(entity1, tenant).get
        consumer.processBatchThenCommit(Success(_))
        // Wait for auto commit
        Thread.sleep(1000)

        // Consume all the offset records before testing
        countRemainingOffsets()

        // Produce test message
        producer.sendWrapped(entity2, tenant).get
        producer.close()

        def processor(messages: Map[String, Seq[${className}]]): Try[Map[String, Seq[${className}]]] = {
          println("failure on purpose")
          Failure(TestException)
        }

        consumer.processBatchThenCommit(processor) should be a 'failure

        // Wait for auto commit
        Thread.sleep(1000)

        // No offset should be committed
        countRemainingOffsets() shouldBe 0

        consumer.close()
        offsetConsumer.close()
      }
    }

  }

  trait Fixture {

    val bootstrapServers = kafkaServer.config.hostName + ":" + kafkaServer.config.port
    val topicInstance = "test"
    val tenant = KafkaTestKitUtils.tempTopic()

    val testConfig = ConfigFactory.parseString(s\"\"\"
      |configuration {
      |  log-on-startup = false
      |}
      |
      |${configPath}.kafka {
      |  producer {
      |    bootstrap.servers: "$$bootstrapServers"
      |    topic.instance = "$$topicInstance"
      |  }
      |}
      |
      |${configPath}.kafka {
      |  consumer {
      |    bootstrap.servers: "$$bootstrapServers"
      |    topic.instance = "$$topicInstance"
      |    tenants = ["ignore_me", "$$tenant"]
      |    poll.timeout = 100
      |
      |    properties {
      |      auto.commit.interval.ms = "500"
      |    }
      |  }
      |}
      |\"\"\".stripMargin)
      .withFallback(ConfigFactory.load())

    val producer = new ${className}Producer(testConfig)
    val consumer = new ${className}Consumer(testConfig, new java.util.Random().nextInt.toString)

    val entity1 = ${generateInstance(model, 1, ssd).indent(4)}
    val key1 = entity1.generateKey(tenant)

    val entity2 = ${generateInstance(model, 2, ssd).indent(4)}
    val key2 = entity2.generateKey(tenant)
  }

}
"""
      ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, s"${className}Test", source, Some("Scala"))
    }
  }

}
